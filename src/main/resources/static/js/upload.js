/**
 * 直传：先向服务端要凭证，再让浏览器把文件直接送到对象存储，
 * 文件字节不经过应用服务器。
 *
 * 服务端可能给两种凭证：
 *  - method=PUT：直接把文件体 PUT 到 uploadUrl，headers 必须原样带上（Content-Type 签在里面）
 *  - method=POST：把 formFields 拼成表单 POST，文件字段名固定为 file
 */
function xhrSend(options) {
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.open(options.method, options.url, true);
        Object.entries(options.headers || {}).forEach(([key, value]) => {
            xhr.setRequestHeader(key, value);
        });
        if (options.onProgress) {
            xhr.upload.onprogress = (event) => {
                if (event.lengthComputable) {
                    options.onProgress(Math.round((event.loaded / event.total) * 100));
                }
            };
        }
        xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                resolve();
            } else {
                reject(new Error('上传失败（' + xhr.status + '），可能是文件超限或凭证已过期'));
            }
        };
        xhr.onerror = () => reject(new Error('网络中断，上传失败'));
        xhr.send(options.body);
    });
}

async function sendToStorage(presign, file, onProgress) {
    if (presign.method === 'PUT') {
        await xhrSend({
            method: 'PUT',
            url: presign.uploadUrl,
            headers: presign.headers || {},
            body: file,
            onProgress
        });
        return;
    }
    const form = new FormData();
    Object.entries(presign.formFields || {}).forEach(([key, value]) => form.append(key, value));
    form.append('file', file);
    await xhrSend({method: 'POST', url: presign.uploadUrl, body: form, onProgress});
}

async function uploadFile(file, onProgress) {
    if (file.size > 50 * 1024 * 1024) {
        throw new Error('单文件不能超过 50MB');
    }
    const presign = await api('/api/files/presign', {
        method: 'POST',
        body: JSON.stringify({name: file.name, sizeBytes: file.size})
    });
    await sendToStorage(presign, file, onProgress);
    return api('/api/files/' + presign.fileId + '/commit', {method: 'POST'});
}

async function uploadAvatar(file, onProgress) {
    if (!file.type || !file.type.startsWith('image/')) {
        throw new Error('头像只能是图片');
    }
    if (file.size > 5 * 1024 * 1024) {
        throw new Error('头像不能超过 5MB');
    }
    const presign = await api('/api/me/avatar/presign', {
        method: 'POST',
        body: JSON.stringify({sizeBytes: file.size, contentType: file.type})
    });
    await sendToStorage(presign, file, onProgress);
    return api('/api/me/avatar/commit', {
        method: 'POST',
        body: JSON.stringify({objectKey: presign.objectKey})
    });
}

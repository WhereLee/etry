/**
 * 通用请求封装与页面小工具。
 * 所有接口都返回 {success, code, message, data}，这里统一拆封装。
 */
async function api(path, options = {}) {
    const response = await fetch(path, {
        credentials: 'same-origin',
        headers: {'Content-Type': 'application/json', ...(options.headers || {})},
        ...options
    });

    if (response.status === 401 && !path.startsWith('/api/auth/login')) {
        window.location.href = '/login';
        throw new Error('未登录');
    }

    let payload = null;
    try {
        payload = await response.json();
    } catch (e) {
        throw new Error('服务异常（' + response.status + '）');
    }

    if (!payload.success) {
        throw new Error(payload.message || '请求失败');
    }
    return payload.data;
}

function toast(message) {
    let element = document.querySelector('.toast');
    if (!element) {
        element = document.createElement('div');
        element.className = 'toast';
        document.body.appendChild(element);
    }
    element.textContent = message;
    element.classList.add('show');
    clearTimeout(window.__toastTimer);
    window.__toastTimer = setTimeout(() => element.classList.remove('show'), 2200);
}

function showMessage(element, message, isError = true) {
    if (!element) {
        return;
    }
    element.textContent = message;
    element.className = 'msg ' + (isError ? 'error' : 'ok');
}

function formatSize(bytes) {
    if (bytes === 0 || bytes === null || bytes === undefined) {
        return '0 B';
    }
    const units = ['B', 'KB', 'MB', 'GB'];
    let index = 0;
    let value = bytes;
    while (value >= 1024 && index < units.length - 1) {
        value /= 1024;
        index++;
    }
    return (index === 0 ? value : value.toFixed(1)) + ' ' + units[index];
}

function formatTime(value) {
    if (!value) {
        return '-';
    }
    return String(value).replace('T', ' ').substring(0, 16);
}

async function logout() {
    try {
        await api('/api/auth/logout', {method: 'POST'});
    } finally {
        window.location.href = '/login';
    }
}

function bindLogout() {
    const button = document.querySelector('[data-action="logout"]');
    if (button) {
        button.addEventListener('click', logout);
    }
}

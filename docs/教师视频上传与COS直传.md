# 教师视频上传与 COS 直传

更新日期：2026-11-02

状态：**批次 C 已实现**

## 1. 业务目标

让教师独立完成视频交付，不再依赖管理员手工上传文件或填写 COS 对象键。

流程：

1. 教师创建视频元数据；
2. 浏览器申请上传地址；
3. 浏览器直传 COS；
4. 服务端 HEAD 校验对象；
5. 标记视频为 `READY`；
6. 教师发布系列；
7. 管理员分配，学员观看。

## 2. 媒体状态

| 状态 | 含义 |
|---|---|
| `PENDING` | 已创建条目，尚未上传 |
| `UPLOADING` | 已申请上传地址，等待上传或确认 |
| `READY` | 对象已确认，可发布、可播放 |

发布要求所有视频均为 `READY`。

## 3. 安全约束

- 对象键由服务端生成，教师不能任意填写。
- 对象路径绑定教师、系列、视频和随机 UUID。
- 上传地址只允许写指定对象，默认 60 分钟有效。
- 申请上传和完成确认都校验教师资源归属。
- 文件大小和 MIME 类型受限。
- 完成确认通过 COS HEAD 校验实际对象大小和类型。
- 删除教师上传的视频时，尝试删除对应 COS 对象。
- 替换视频成功后，旧对象在事务提交后清理。

## 4. 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/teacher/series/{seriesId}/chapters/{chapterId}/videos/{videoId}/upload-url` | 申请预签名 PUT |
| `POST` | `/api/teacher/series/{seriesId}/chapters/{chapterId}/videos/{videoId}/complete` | 确认上传并标记就绪 |

上传地址请求包含：

- 文件名；
- MIME 类型；
- 文件大小。

完成请求包含：

- 视频时长秒数。

## 5. COS CORS

浏览器直传要求 COS 桶配置 CORS。至少允许：

- `AllowedOrigins`：实际前端域名，本地为 `http://127.0.0.1:5175`；
- `AllowedMethods`：`PUT`、`GET`、`HEAD`；
- `AllowedHeaders`：`Content-Type`，开发阶段可先使用 `*`；
- `ExposeHeaders`：`ETag`、`x-cos-request-id`；
- `MaxAgeSeconds`：建议 3600。

CORS 未配置时，浏览器会出现预检失败或“COS 上传失败，请检查存储桶 CORS 配置”。服务端的预签名 URL 生成和 PUT 上传不受此影响。

## 6. 失败与重试

- 上传失败后视频进入/保持 `UPLOADING`，不能发布。
- 教师可以重新申请上传地址并重试。
- 对象不存在或大小/类型不正确时，完成接口拒绝标记就绪。
- 发布系列时再次检查所有视频是否 `READY`。

## 7. 配置

| 变量 | 默认值 | 说明 |
|---|---|---|
| `UPLOAD_MAX_SIZE_BYTES` | 4GB | 单视频大小上限 |
| `UPLOAD_URL_TTL_MINUTES` | 60 | 上传地址有效期 |
| `UPLOAD_ALLOWED_CONTENT_TYPES` | `video/mp4,video/webm` | 允许的类型 |

## 8. 当前限制

- 不做服务端转码。
- 不做 faststart 自动重封装。
- 上传失败产生的未引用对象需要后续清理任务。
- 本地 `127.0.0.1:5175` / `localhost:5175` 已加入 COS CORS；生产前端域名上线时需要追加。

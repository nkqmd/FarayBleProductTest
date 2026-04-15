# Test Server 接口文档

## 环境准备

1. 激活虚拟环境

```bat
venv\Scripts\activate
```

2. 安装依赖

```bat
pip install flask flask-cors flask-jwt-extended werkzeug
```

3. 启动服务

```bat
python app.py
```

## 服务说明

- 默认地址：`http://localhost:5000`
- 已启用 CORS
- `access_token` 有效期：24 小时
- `refresh_token` 有效期：7 天
- 最大上传大小：16 MB
- 上传目录：`uploads/`
- 批次目录根路径：`batch/`

### 默认测试账号

- `username`: `test`
- `password`: `123456`

## 认证规则

- 除 `/api/login` 外，所有业务接口都需要 JWT
- `/api/refresh` 必须使用 `Authorization: Bearer <refresh_token>`
- 其余受保护接口必须使用 `Authorization: Bearer <access_token>`
- 同一用户再次登录时，会吊销该用户上一次登录产生的 `access_token` 和 `refresh_token`
- refresh 成功后，会返回一组新的 `access_token` 和 `refresh_token`
- refresh 成功后，旧 `access_token` 和旧 `refresh_token` 会立刻失效
- `/api/logout` 会吊销当前 `access_token`；如果它是当前活跃 `access_token`，也会一并吊销对应 `refresh_token`
- token 黑名单和登录状态保存在内存中，服务重启后会清空

### 常见认证失败返回

Token 过期：

```json
{
  "msg": "Token has expired"
}
```

Token 已吊销：

```json
{
  "msg": "Token has been revoked"
}
```

缺少 Token：

```json
{
  "msg": "Missing token: ..."
}
```

Token 非法：

```json
{
  "msg": "Invalid token: ..."
}
```

## 批次目录约定

按 `batch_id` 读取批次数据，目录结构必须如下：

```text
batch/
  BATCH20260318001/
    BATCH20260318001_mac_list.txt
    BATCH20260318001_config.json
    result/
```

其中：

- `batch_id` 会经过 `secure_filename` 校验，不能包含非法字符或路径穿越内容
- `BATCH20260318001_mac_list.txt` 为 MAC 列表文件，每行一个 MAC，空行会被忽略
- `BATCH20260318001_config.json` 为批次配置文件
- `result/` 用于保存该批次上传的测试结果和按批次上传的附件

批次配置文件必须包含以下字段：

- `expected_count`
- `expire_time`
- `expected_firmware`
- `device_type`
- `ble_name_prefix`
- `ble_config`

## 接口文档

### 1. 登录

**请求**

```http
POST http://localhost:5000/api/login
Content-Type: application/json
```

请求示例：

```json
{
  "username": "test",
  "password": "123456"
}
```

成功返回：

```json
{
  "access_token": "<access_token>",
  "access_expires": 86400,
  "refresh_token": "<refresh_token>",
  "refresh_expires": 604800
}
```

失败返回：

```json
{
  "msg": "Invalid username or password"
}
```

### 2. 刷新 Token

**请求**

```http
POST http://localhost:5000/api/refresh
Authorization: Bearer <refresh_token>
```

成功返回：

```json
{
  "access_token": "<new_access_token>",
  "access_expires": 86400,
  "refresh_token": "<new_refresh_token>",
  "refresh_expires": 604800
}
```

说明：

- refresh 后旧 `refresh_token` 立即失效
- refresh 前签发的旧 `access_token` 也立即失效
- 客户端必须用新返回的 token 覆盖本地旧 token

### 3. 退出登录

**请求**

```http
POST http://localhost:5000/api/logout
Authorization: Bearer <access_token>
```

成功返回：

```json
{
  "msg": "Logout successful"
}
```

### 4. 上传文件

**请求**

```http
POST http://localhost:5000/api/upload
Authorization: Bearer <access_token>
Content-Type: multipart/form-data
```

表单字段：

- `file`：必填，上传文件
- `batch_id`：可选，可放在 form-data 中，也可通过 query 参数传入

行为说明：

- 未传 `batch_id` 时，文件保存到 `uploads/`
- 传入 `batch_id` 时，会先校验该批次目录和配置是否存在
- 批次上传文件会保存到 `batch/<batch_id>/result/`

无批次上传成功返回：

```json
{
  "filename": "example.png",
  "url": "/api/download/example.png"
}
```

按批次上传成功返回：

```json
{
  "filename": "report.json",
  "url": "/api/download/batch/BATCH20260318001/report.json"
}
```

常见失败：

未上传文件：

```json
{
  "msg": "No file uploaded"
}
```

文件名为空：

```json
{
  "msg": "Empty filename"
}
```

批次不存在时：

```json
{
  "msg": "Batch directory not found: batch/BATCH20260318001"
}
```

### 5. 下载普通上传文件

**请求**

```http
GET http://localhost:5000/api/download/<filename>
Authorization: Bearer <access_token>
```

说明：

- 从 `uploads/` 目录下载文件
- 以附件形式返回

### 6. 下载批次文件

**请求**

```http
GET http://localhost:5000/api/download/batch/<batch_id>/<filename>
Authorization: Bearer <access_token>
```

说明：

- 从 `batch/<batch_id>/result/` 目录下载文件
- 请求前会校验批次目录、MAC 文件和配置文件是否存在

### 7. 查询文件列表

**请求**

```http
GET http://localhost:5000/api/storage/files
Authorization: Bearer <access_token>
```

可选参数：

- `batch_id`

说明：

- 未传 `batch_id` 时，返回 `uploads/` 目录下的文件名数组
- 传 `batch_id` 时，返回 `batch/<batch_id>/` 目录下的相对路径数组，包含子目录文件

不带 `batch_id` 返回示例：

```json
[
  "example.png",
  "log.txt"
]
```

带 `batch_id` 返回示例：

```json
[
  "BATCH20260318001_config.json",
  "BATCH20260318001_mac_list.txt",
  "result/report.json",
  "result/screenshot.png"
]
```

常见失败：

非法 `batch_id`：

```json
{
  "msg": "Invalid batch_id"
}
```

批次目录不存在：

```json
{
  "msg": "Batch directory not found: batch/BATCH20260318001"
}
```

### 8. 查询生产批次摘要

**请求**

```http
GET http://localhost:5000/api/production/summary?batch_id=BATCH20260318001
Authorization: Bearer <access_token>
```

成功返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "batch_id": "BATCH20260318001",
    "expected_count": 100,
    "expire_time": "2026-03-18T18:00:00+08:00",
    "expected_firmware": "2.1.0",
    "device_type": "ST02US-WH-H",
    "ble_name_prefix": "CL-ST02US-",
    "ble_config": {
      "rssi_min": -70,
      "rssi_max": -30,
      "max_concurrent": 5
    },
    "mac_list_count": 100,
    "mac_list_hash": "sha256:xxxxxxxx",
    "mac_list_version": "2026-03-18T02:00:00+00:00",
    "mac_list_format": "txt",
    "mac_list_url": "/api/production/mac-list/download?batch_id=BATCH20260318001"
  }
}
```

说明：

- 该接口不返回完整 MAC 列表，只返回摘要信息
- `mac_list_hash` 为 MAC 列表按换行拼接后的 SHA-256
- `mac_list_version` 为 MAC 文件最后修改时间，UTC ISO 8601 格式

缺少 `batch_id`：

```json
{
  "code": 400,
  "message": "batch_id is required",
  "data": null
}
```

### 9. 下载生产批次 MAC 列表文件

**请求**

```http
GET http://localhost:5000/api/production/mac-list/download?batch_id=BATCH20260318001
Authorization: Bearer <access_token>
```

返回说明：

- 响应体为纯文本，每行一个 MAC
- `Content-Disposition` 会返回下载文件名 `<batch_id>_mac_list.txt`
- 响应头 `ETag` 为 MAC 列表哈希
- 响应头 `X-Mac-List-Version` 为 MAC 文件最后修改时间
- 响应头 `X-Mac-List-Count` 为 MAC 数量

### 10. 查询生产批次完整 MAC 列表

**请求**

```http
GET http://localhost:5000/api/production/mac-list?batch_id=BATCH20260318001
Authorization: Bearer <access_token>
```

成功返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "batch_id": "BATCH20260318001",
    "expected_count": 100,
    "expire_time": "2026-03-18T18:00:00+08:00",
    "expected_firmware": "2.1.0",
    "device_type": "ST02US-WH-H",
    "ble_name_prefix": "CL-ST02US-",
    "ble_config": {
      "rssi_min": -70,
      "rssi_max": -30,
      "max_concurrent": 5
    },
    "mac_list_count": 2,
    "mac_list_hash": "sha256:xxxxxxxx",
    "mac_list_version": "2026-03-18T02:00:00+00:00",
    "mac_list_format": "txt",
    "mac_list_url": "/api/production/mac-list/download?batch_id=BATCH20260318001",
    "mac_list": [
      "AA:BB:CC:DD:EE:FF",
      "11:22:33:44:55:66"
    ]
  }
}
```

### 11. 上传产测结果

**请求**

```http
POST http://localhost:5000/api/production/result/upload
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求示例：

```json
{
  "batch_id": "BATCH20260318001",
  "factory_id": "FACTORY001",
  "app_version": "1.0.0",
  "test_start_time": "2026-03-18T10:00:00+08:00",
  "test_end_time": "2026-03-18T10:45:00+08:00",
  "statistics": {
    "expected_count": 100,
    "actual_count": 98,
    "success_count": 95,
    "fail_count": 3,
    "success_rate": 96.94
  },
  "success_records": [
    {
      "mac": "AA:BB:CC:DD:EE:FF",
      "time": "2026-03-18T10:05:01+08:00"
    }
  ],
  "fail_records": [
    {
      "mac": "11:22:33:44:55:66",
      "result": "FAIL",
      "reason": "Connect Timeout",
      "time": "2026-03-18T10:06:20+08:00"
    }
  ],
  "invalid": [
    {
      "mac": "22:33:44:55:66:77",
      "time": "2026-03-18T10:08:00+08:00"
    }
  ]
}
```

字段校验规则：

- 必填字段：`batch_id`、`factory_id`、`app_version`、`test_start_time`、`test_end_time`、`statistics`、`success_records`、`fail_records`、`invalid`
- `test_start_time` 和 `test_end_time` 必须是合法 ISO 8601 时间
- `test_end_time` 不能早于 `test_start_time`
- `statistics` 必须包含 `expected_count`、`actual_count`、`success_count`、`fail_count`、`success_rate`
- `success_records` 必须是对象数组，每项至少包含 `mac`、`time`
- `fail_records` 必须是对象数组，每项必须包含 `mac`、`result`、`reason`、`time`
- `invalid` 必须是对象数组，每项必须包含 `mac`、`time`

服务端附加字段：

- 保存文件前，服务端会自动补充 `upload_id`、`uploaded_by`、`uploaded_at`、`batch_mac_count`
- 结果文件保存到 `batch/<batch_id>/result/<batch_id>_<factory_id>_<UTC时间戳>.json`

成功返回：

```json
{
  "code": 200,
  "message": "upload success",
  "data": {
    "upload_id": "UPLOAD20260318104530",
    "upload_time": "2026-03-18T10:45:30+08:00"
  }
}
```

校验失败示例：

```json
{
  "code": 400,
  "message": "test_end_time must be greater than or equal to test_start_time",
  "data": null
}
```

### 12. 查询最新产测统计结果

**请求**

```http
GET http://localhost:5000/api/production/statistics?batch_id=BATCH20260318001
Authorization: Bearer <access_token>
```

说明：

- 读取该批次 `result/` 目录下最新修改的结果文件
- 如果该批次还没有上传过结果，会返回 `404`

成功返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "batch_id": "BATCH20260318001",
    "expected_count": 100,
    "actual_count": 98,
    "success_count": 95,
    "fail_count": 3,
    "success_rate": 96.94,
    "test_start_time": "2026-03-18T10:00:00+08:00",
    "test_end_time": "2026-03-18T10:45:00+08:00",
    "status": "completed"
  }
}
```

结果不存在时：

```json
{
  "code": 404,
  "message": "Production result not found for batch_id: BATCH20260318001",
  "data": null
}
```

## 联调建议

1. 调用 `/api/login` 获取 `access_token` 和 `refresh_token`
2. 使用 `access_token` 调用 `/api/storage/files`
3. 调用 `/api/production/summary?batch_id=...` 确认批次配置和 MAC 摘要
4. 如需校验完整 MAC 列表，可调用 `/api/production/mac-list` 或 `/api/production/mac-list/download`
5. 使用 `refresh_token` 调用 `/api/refresh`，确认旧 token 失效
6. 上传产测结果到 `/api/production/result/upload`
7. 再调用 `/api/production/statistics?batch_id=...` 校验统计结果
8. 调用 `/api/logout` 后，再次使用旧 token，预期返回 `Token has been revoked`

## 注意事项

- `SECRET_KEY` 和 `JWT_SECRET_KEY` 当前仍是示例值，只适合本地测试
- 正式环境应改为安全随机密钥，并通过环境变量或配置文件注入
- 黑名单和活跃 token 状态当前仅保存在内存中，不适合多实例生产环境
- 如果客户端依赖接口文档生成代码，请以本文中的实际路径为准，旧文档中的 `/api/files` 已变更为 `/api/storage/files`

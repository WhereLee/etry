# P3 前端 RBAC、Playwright E2E 与 CI 安全门禁（2026-11-24）

## 1. 后端契约

`GET /api/auth/me` 与登录响应中的 `user` 新增：

```json
{
  "role": "ADMIN",
  "roles": ["SUPER_ADMIN"],
  "permissions": ["audit:read", "material:read", "order:operate", "user:manage", "..."]
}
```

- `role` 保留 legacy 角色，兼容旧前端/旧数据。
- `roles` 来自 `t_user_role`；无 RBAC 记录时回退 legacy 角色。
- `permissions` 来自 `t_role_permission`，与 `JwtAuthFilter` 的 authority 同源。
- 集成测试 `StorefrontApiIntegrationTest` 断言 BUYER 的 `roles=[BUYER]`、`permissions=[]`。

## 2. 前端权限控制

- `session.hasRole` 同时识别 legacy `role` 与 RBAC `roles`。
- 新增 `hasPermission/hasAnyPermission`。
- 路由守卫支持 `meta.permission`：
  - 用户管理 `user:read`，教师邀请 `user:manage`；
  - 资料管理 `material:read`，订单 `order:read`，对账 `recon:read`，审计 `audit:read`；
  - 内容管理/系统配置仅 `SUPER_ADMIN`。
- 运营端菜单按当前权限过滤；财务/客服只看到自己可访问的入口。
- 关键操作按钮按权限隐藏：
  - 用户：新建/分配系列/角色/重置密码/停用需 `user:manage`；
  - 订单：查单/补履约/关闭需 `order:operate`，退款需 `refund:operate`；
  - 对账：导入/执行/认领/解决/忽略需 `recon:operate`。
- 登录后跳转统一走 `homePath()`，按权限选第一个可访问页面，避免跳到无权限路由。

## 3. Playwright E2E（生产环境）

3 个用例全部通过（Chromium headless，baseURL=https://whereri.site）：

```
✓ 游客可浏览资料商城 (852ms)
✓ 管理员登录后按权限看到运营菜单 (2.3s)
✓ 学员无法进入运营端路由 (1.2s)
3 passed (5.2s)
```

- 管理员用例校验菜单可见性，并调用 `/api/auth/me` 断言 `SUPER_ADMIN`、`user:manage`、`order:operate`。
- 学员用例直接访问 `/learn/admin/users`，被守卫重定向回资料商城。
- 测试账号为临时账号，演练后已删除（leftover=0），凭据文件已删除。
- 截图证据：
  - `docs/evidence/2026-11-24/e2e/guest-materials.png`
  - `docs/evidence/2026-11-24/e2e/admin-console.png`
  - `docs/evidence/2026-11-24/e2e/student-redirect.png`

运行方式（凭据只走环境变量，不入库）：

```bash
E2E_BASE_URL=https://whereri.site \
E2E_ADMIN_USERNAME=... E2E_ADMIN_PASSWORD=... \
E2E_STUDENT_USERNAME=... E2E_STUDENT_PASSWORD=... \
npm run test:e2e
```

## 4. CI

`.github/workflows/ci.yml` 新增 `sca` job：

```yaml
- uses: google/osv-scanner-action@v2
  with:
    scan-args: |-
      --recursive
      --min-severity HIGH
      .
```

- 对 Maven/npm 锁文件做 OSV 扫描，HIGH/CRITICAL 阻断合并。
- 已知 MODERATE 项仍记录在安全整改清单，后续单独清理。
- 原有 backend 单测/集成/打包、frontend lint/build、compose 校验与镜像构建保留。

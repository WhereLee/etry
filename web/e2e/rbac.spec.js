import fs from 'node:fs'
import { test, expect } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL || 'https://whereri.site'
const adminUser = process.env.E2E_ADMIN_USERNAME || 'admin'
const adminPassword = process.env.E2E_ADMIN_PASSWORD || ''
const studentUser = process.env.E2E_STUDENT_USERNAME || 'student'
const studentPassword = process.env.E2E_STUDENT_PASSWORD || ''
const artifactDir = process.env.E2E_ARTIFACT_DIR || ''

async function shot(page, name) {
  if (!artifactDir) return
  fs.mkdirSync(artifactDir, { recursive: true })
  await page.screenshot({ path: `${artifactDir}/${name}.png`, fullPage: true })
}

async function login(page, username, password) {
  await page.goto(`${baseURL}/learn/login`)
  await page.getByPlaceholder('请输入账号').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '进入学习空间' }).click()
  await page.waitForURL((url) => !url.pathname.endsWith('/login'), { timeout: 15_000 })
}

test('游客可浏览资料商城', async ({ page }) => {
  await page.goto(`${baseURL}/learn/materials`)
  await expect(page.getByRole('heading', { name: '学习资料商城' })).toBeVisible()
  await shot(page, 'guest-materials')
})

test('管理员登录后按权限看到运营菜单', async ({ page }) => {
  test.skip(!adminPassword, 'E2E_ADMIN_PASSWORD 未配置')
  await login(page, adminUser, adminPassword)
  await expect(page.getByRole('link', { name: '用户管理' }).first()).toBeVisible()
  await expect(page.getByRole('link', { name: '订单管理' }).first()).toBeVisible()
  await expect(page.getByRole('link', { name: '资金对账' }).first()).toBeVisible()

  const me = await page.evaluate(async () => {
    const raw = localStorage.getItem('etry.btry.session')
    const token = raw ? JSON.parse(raw).token : ''
    const response = await fetch('/api/auth/me', { headers: { Authorization: `Bearer ${token}` } })
    return response.json()
  })
  expect(me.roles).toContain('SUPER_ADMIN')
  expect(me.permissions).toContain('user:manage')
  expect(me.permissions).toContain('order:operate')
  await shot(page, 'admin-console')
})

test('学员无法进入运营端路由', async ({ page }) => {
  test.skip(!studentPassword, 'E2E_STUDENT_PASSWORD 未配置')
  await login(page, studentUser, studentPassword)
  await page.goto(`${baseURL}/learn/admin/users`)
  await page.waitForURL((url) => url.pathname.startsWith('/learn/materials'), { timeout: 10_000 })
  await expect(page.getByRole('heading', { name: '学习资料商城' })).toBeVisible()
  await shot(page, 'student-redirect')
})

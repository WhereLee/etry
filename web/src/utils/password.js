const WEAK_PASSWORDS = new Set([
  '12345678',
  '123456789',
  'password',
  'password1',
  'admin123',
  'qwerty123',
  'abc12345',
  '11111111',
  'student123',
  'teacher123'
])

export function passwordError(value) {
  if (!value || value.length < 8 || value.length > 32) {
    return '密码长度需在 8-32 位之间'
  }
  if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    return '密码需同时包含字母和数字'
  }
  if (WEAK_PASSWORDS.has(value.toLowerCase())) {
    return '不能使用常见弱密码'
  }
  return ''
}

export function generateStrongPassword() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
  const bytes = new Uint8Array(10)
  crypto.getRandomValues(bytes)
  const body = Array.from(bytes, (value) => chars[value % chars.length]).join('')
  return `Edu-${body}7a`
}

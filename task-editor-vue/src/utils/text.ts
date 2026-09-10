/**
 * 展示用的纯文本工具。
 *
 * <p>任务名/描述里带的是 MiniMessage 风格的颜色标签（例如 {@code <yellow>}），
 * 浏览器不认识它们，直接显示会很难看。这里统一剥离标签，只用于「显示」，
 * 提交给后端的始终是原始文本。
 */

/** 去掉颜色/样式标签，得到可读的纯文本。 */
export function stripTags(value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  return value
    // MiniMessage 风格：<yellow>、</yellow>、<gradient:#fff:#000>
    .replace(/<[^<>]{1,64}>/g, '')
    // 传统颜色代码：§a、§l
    .replace(/\u00a7./g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

/** 纯文本与原串不同才需要额外显示一行；相同则返回空串。 */
export function plainIfDifferent(value: string | null | undefined): string {
  const raw = (value ?? '').trim()
  const plain = stripTags(raw)
  return plain && plain !== raw ? plain : ''
}

/**
 * 把后端返回的时间戳格式化成可读文本。
 *
 * <p>后端可能给秒或毫秒，这里按量级自动判断；null/0/异常一律显示占位符。
 */
export function formatTime(value: number | null | undefined, placeholder = '—'): string {
  if (value === null || value === undefined) {
    return placeholder
  }
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return placeholder
  }
  // 10 位及以下按秒处理（1e11 秒 ≈ 公元 5138 年，不会误判正常数据）
  const millis = numeric < 1e11 ? numeric * 1000 : numeric
  const date = new Date(millis)
  if (Number.isNaN(date.getTime())) {
    return placeholder
  }
  const pad = (input: number) => String(input).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 金额/余额展示：整数不带小数点，小数最多保留两位。 */
export function formatAmount(value: number | null | undefined): string {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) {
    return '0'
  }
  return Number.isInteger(numeric) ? String(numeric) : numeric.toFixed(2)
}

/** 是否已过期；expiresAt 为 null/0 表示不限期。 */
export function isExpired(value: number | null | undefined): boolean {
  if (value === null || value === undefined) {
    return false
  }
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return false
  }
  const millis = numeric < 1e11 ? numeric * 1000 : numeric
  return millis < Date.now()
}

/** 把进度百分比夹到 0-100。 */
export function clampPercent(value: number | null | undefined): number {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) {
    return 0
  }
  return Math.max(0, Math.min(100, Math.round(numeric)))
}

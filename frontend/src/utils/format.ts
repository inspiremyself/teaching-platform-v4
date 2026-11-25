/** 日期时间展示选项 */
export interface DateFormatOptions {
  /** 空值或非法时间串的兜底文案，缺省 '-' */
  fallback?: string
}

/** 分数展示选项 */
export interface ScoreFormatOptions {
  /** 小数位数，缺省 1 */
  digits?: number
  /** 为 true 时去掉末尾的 '.0'，缺省 false */
  stripTrailingZero?: boolean
  /** 非有限数字的兜底文案，缺省 '-' */
  fallback?: string
}

const DEFAULT_FALLBACK = '-'

/** 两位补零 */
function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

/** 解析为合法日期，空值与非法值返回 null */
function toValidDate(value: string | null | undefined): Date | null {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return null
  }
  return date
}

/**
 * 长格式日期时间：YYYY-MM-DD HH:mm，按浏览器本地时区渲染。
 * 空值或非法时间串返回 fallback（缺省 '-'）。
 */
export function formatDateTime(value: string | null | undefined, options?: DateFormatOptions): string {
  const date = toValidDate(value)
  if (!date) {
    return options?.fallback ?? DEFAULT_FALLBACK
  }
  const year = date.getFullYear()
  const month = date.getMonth() + 1
  const day = date.getDate()
  return `${year}-${pad2(month)}-${pad2(day)} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

/**
 * 短格式日期时间：M/D HH:mm，月与日不补零、时与分补零，按浏览器本地时区渲染。
 * 空值或非法时间串返回 fallback（缺省 '-'）。
 */
export function formatShortDateTime(value: string | null | undefined, options?: DateFormatOptions): string {
  const date = toValidDate(value)
  if (!date) {
    return options?.fallback ?? DEFAULT_FALLBACK
  }
  const month = date.getMonth() + 1
  const day = date.getDate()
  return `${month}/${day} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

/**
 * 分数格式化：默认保留 1 位小数，可选去掉末尾 '.0'。
 * 非有限数字（含 null / undefined）返回 fallback（缺省 '-'）。
 */
export function formatScore(value: number | null | undefined, options?: ScoreFormatOptions): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return options?.fallback ?? DEFAULT_FALLBACK
  }
  let text = value.toFixed(options?.digits ?? 1)
  if (options?.stripTrailingZero === true) {
    text = text.replace(/\.0$/, '')
  }
  return text
}

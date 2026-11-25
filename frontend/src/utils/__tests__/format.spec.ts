import { describe, expect, it } from 'vitest'
import { formatDateTime, formatScore, formatShortDateTime } from '@/utils/format'

describe('formatDateTime', () => {
  it('按 YYYY-MM-DD HH:mm 渲染', () => {
    expect(formatDateTime('2026-11-25T10:00:00+08:00')).toBe('2026-11-25 10:00')
  })

  it('月/日/时/分补零', () => {
    expect(formatDateTime('2026-01-02T03:04:00+08:00')).toBe('2026-01-02 03:04')
  })

  it('跨日不偏移', () => {
    expect(formatDateTime('2026-11-25T23:59:00+08:00')).toBe('2026-11-25 23:59')
  })

  it('空值与非法时间串返回缺省兜底', () => {
    expect(formatDateTime(null)).toBe('-')
    expect(formatDateTime('')).toBe('-')
    expect(formatDateTime('not-a-date')).toBe('-')
  })

  it('显式 fallback 覆盖缺省值', () => {
    expect(formatDateTime('', { fallback: '未设置' })).toBe('未设置')
  })
})

describe('formatShortDateTime', () => {
  it('按 M/D HH:mm 渲染，月日不补零', () => {
    expect(formatShortDateTime('2026-11-05T08:07:00+08:00')).toBe('11/5 08:07')
  })

  it('空值返回显式 fallback', () => {
    expect(formatShortDateTime(null, { fallback: '未设置' })).toBe('未设置')
  })

  it('空串与非法串返回显式 fallback', () => {
    expect(formatShortDateTime('', { fallback: '时间待定' })).toBe('时间待定')
    expect(formatShortDateTime('x', { fallback: '时间未记录' })).toBe('时间未记录')
  })
})

describe('formatScore', () => {
  it('缺省保留 1 位小数', () => {
    expect(formatScore(8)).toBe('8.0')
    expect(formatScore(8.25)).toBe('8.3')
  })

  it('stripTrailingZero 去掉末尾 .0', () => {
    expect(formatScore(8, { stripTrailingZero: true })).toBe('8')
  })

  it('digits 可覆盖小数位数', () => {
    expect(formatScore(8.25, { digits: 2 })).toBe('8.25')
  })

  it('非有限数字返回缺省兜底', () => {
    expect(formatScore(NaN)).toBe('-')
    expect(formatScore(Infinity)).toBe('-')
    expect(formatScore(null)).toBe('-')
  })
})

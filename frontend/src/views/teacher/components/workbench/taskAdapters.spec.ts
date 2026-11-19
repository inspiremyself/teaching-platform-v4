import { describe, expect, it } from 'vitest'
import { bucketOf, taskAdapterOf } from './taskAdapters'

describe('taskAdapters', () => {
  it('maps exam endAt and homework/lab dueAt to endAt', () => {
    expect(taskAdapterOf('EXAM').adapt({ id: 1, title: 'A', status: 'DRAFT', endAt: '2026-11-20T10:00:00' }).endAt).toBe('2026-11-20T10:00:00')
    expect(taskAdapterOf('HOMEWORK').adapt({ id: 2, title: 'B', status: 'PUBLISHED', dueAt: '2026-11-21T10:00:00' }).endAt).toBe('2026-11-21T10:00:00')
    expect(taskAdapterOf('LAB').adapt({ id: 3, title: 'C', status: 'CLOSED', dueAt: '2026-11-22T10:00:00' }).endAt).toBe('2026-11-22T10:00:00')
  })

  it('keeps null for missing submission counts and item counts', () => {
    const lab = taskAdapterOf('LAB').adapt({ id: 3, title: 'C', status: 'DRAFT', stepCount: 4 })
    expect(lab.submissionCount).toBeNull()
    expect(lab.itemCount).toBe(4)
    expect(taskAdapterOf('HOMEWORK').adapt({ id: 2, title: 'B', status: 'DRAFT' }).itemCount).toBeNull()
  })

  it('maps activity statuses to Chinese labels with unknown fallback', () => {
    const adapter = taskAdapterOf('EXAM')
    expect(adapter.adapt({ id: 1, status: 'DRAFT' }).statusLabel).toBe('草稿')
    expect(adapter.adapt({ id: 1, status: 'PUBLISHED' }).statusLabel).toBe('已发布')
    expect(adapter.adapt({ id: 1, status: 'CLOSED' }).statusLabel).toBe('已关闭')
    expect(adapter.adapt({ id: 1, status: 'OTHER' }).statusLabel).toBe('未知')
  })

  it('does not throw on null and computes deadline buckets', () => {
    expect(taskAdapterOf('EXAM').adapt(null).statusLabel).toBe('未知')
    expect(bucketOf(null)).toBe('UNDATED')
    expect(bucketOf('invalid')).toBe('UNDATED')
  })
})

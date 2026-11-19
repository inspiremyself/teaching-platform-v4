import { describe, expect, it } from 'vitest'
import { backfillQueueScores, submissionAdapterOf } from './submissionAdapters'
import type { QueueItemVM } from './types'

describe('submissionAdapters', () => {
  it('reads exam objective, manual and total scores directly', () => {
    const item = submissionAdapterOf('EXAM').adapt(
      { submissionId: 1, studentName: '学生A', status: 'GRADED', autoScore: 20, manualScore: 30, totalScore: 50 },
      {},
    )
    expect([item.objectiveScore, item.manualScore, item.totalScore]).toEqual([20, 30, 50])
    expect(item.statusLabel).toBe('已评分')
  })

  it('aggregates homework scores by scoring strategy kind and ignores references', () => {
    const item = submissionAdapterOf('HOMEWORK').adapt({
      id: 2,
      studentName: '学生B',
      submitStatus: 'SUBMITTED',
      totalScore: 0,
      questions: [
        { answer: { id: 11, scoreSource: 'AUTO', score: 5 }, question: { score: 10 } },
        { answer: { id: 12, scoreSource: 'TEACHER', score: 8 }, question: { score: 10 } },
        { answer: { id: 13, scoreSource: 'SUGGESTED', suggestedScore: 6 }, question: { score: 10 } },
      ],
    }, {})
    expect(item.objectiveScore).toBe(5)
    expect(item.manualScore).toBe(8)
  })

  it('keeps lab breakdown null until backfilled and injects draftFlag', () => {
    const adapter = submissionAdapterOf('LAB')
    const item = adapter.adapt({ id: 3, studentName: '学生C', submitStatus: 'GRADED', totalScore: 9 }, { draftFlag: true })
    expect(item.objectiveScore).toBeNull()
    expect(item.manualScore).toBeNull()
    expect(item.draftFlag).toBe(true)
    const summary = {
      autoScore: 4,
      manualScore: 5,
      totalScore: 9,
      completed: 1,
      total: 1,
      percent: 100,
      hasManual: true,
      noManualText: '',
    }
    const next = backfillQueueScores([item], 3, summary)
    expect(next[0].objectiveScore).toBe(4)
    expect(next[0].manualScore).toBe(5)
    expect((next[0] as QueueItemVM).totalScore).toBe(9)
  })

  it('does not throw on null raw data', () => {
    expect(submissionAdapterOf('EXAM').adapt(null, {}).statusLabel).toBe('未知')
  })
})

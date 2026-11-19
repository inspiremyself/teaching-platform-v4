import { describe, expect, it } from 'vitest'
import { taskActionStrategyOf, type TaskActionHandlers } from './taskActionStrategies'
import type { TaskRowVM } from './types'

const handlers: TaskActionHandlers = {
  steps: () => {},
  viewResult: () => {},
  edit: () => {},
  publish: () => {},
  close: () => {},
}

const row = (status: string): TaskRowVM => ({
  id: 1,
  title: 'task',
  status,
  statusLabel: status,
  className: '班级',
  startAt: null,
  endAt: null,
  submissionCount: null,
  itemCount: null,
  description: '',
  raw: null,
})

describe('taskActionStrategies', () => {
  const cases: Array<['EXAM' | 'HOMEWORK' | 'LAB', string, string[]]> = [
    ['EXAM', 'DRAFT', ['viewResult', 'edit', 'publish']],
    ['EXAM', 'PUBLISHED', ['viewResult', 'close']],
    ['EXAM', 'CLOSED', ['viewResult']],
    ['HOMEWORK', 'DRAFT', ['viewResult', 'edit', 'publish']],
    ['HOMEWORK', 'PUBLISHED', ['viewResult', 'edit', 'close']],
    ['HOMEWORK', 'CLOSED', ['viewResult', 'edit']],
    ['LAB', 'DRAFT', ['steps', 'viewResult', 'edit', 'publish']],
    ['LAB', 'PUBLISHED', ['steps', 'viewResult', 'edit', 'close']],
    ['LAB', 'CLOSED', ['steps', 'viewResult', 'edit', 'publish']],
  ]

  it.each(cases)('%s / %s returns the fixed action order', (domain, status, expected) => {
    const strategy = taskActionStrategyOf(domain, status, handlers)
    expect(strategy.actions.map(action => action.key)).toEqual(expected)
    expect(strategy.primaryKey).toBe('viewResult')
    expect(strategy.actions.length).toBeLessThanOrEqual(4)
  })

  it('falls back to viewResult for unknown statuses', () => {
    expect(taskActionStrategyOf('EXAM', 'UNKNOWN', handlers).actions.map(item => item.key)).toEqual(['viewResult'])
  })
})

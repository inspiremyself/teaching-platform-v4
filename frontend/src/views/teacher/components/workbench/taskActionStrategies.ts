import { ACTIVITY_STATUS } from '@/types/enums'
import type { ActionTone, TaskActionKey, TaskActionVM, TaskRowVM, WorkbenchDomain } from './types'

export interface TaskActionHandlers {
  steps: (row: TaskRowVM) => void
  viewResult: (row: TaskRowVM) => void
  edit: (row: TaskRowVM) => void
  publish: (row: TaskRowVM) => void
  close: (row: TaskRowVM) => void
}

export interface TaskActionStrategy {
  actions: TaskActionVM[]
  primaryKey: 'viewResult'
}

const LABELS: Record<TaskActionKey, string> = {
  steps: '步骤配置',
  viewResult: '查看结果',
  edit: '编辑',
  publish: '发布',
  close: '关闭',
}

const TONES: Record<TaskActionKey, ActionTone> = {
  steps: 'ghost',
  viewResult: 'primary',
  edit: 'ghost',
  publish: 'success',
  close: 'warning',
}

function action(key: TaskActionKey, handlers: TaskActionHandlers): TaskActionVM {
  return { key, label: LABELS[key], tone: TONES[key], handler: handlers[key] }
}

export function taskActionStrategyOf(
  domain: WorkbenchDomain,
  status: string,
  handlers: TaskActionHandlers,
): TaskActionStrategy {
  const viewResult = action('viewResult', handlers)
  const edit = action('edit', handlers)
  const publish = action('publish', handlers)
  const close = action('close', handlers)
  const steps = action('steps', handlers)

  let keys: TaskActionKey[]
  if (domain === 'EXAM') {
    if (status === ACTIVITY_STATUS.DRAFT) keys = ['viewResult', 'edit', 'publish']
    else if (status === ACTIVITY_STATUS.PUBLISHED) keys = ['viewResult', 'close']
    else keys = ['viewResult']
  } else if (domain === 'HOMEWORK') {
    if (status === ACTIVITY_STATUS.DRAFT) keys = ['viewResult', 'edit', 'publish']
    else if (status === ACTIVITY_STATUS.PUBLISHED) keys = ['viewResult', 'edit', 'close']
    else keys = ['viewResult', 'edit']
  } else {
    if (status === ACTIVITY_STATUS.PUBLISHED) keys = ['steps', 'viewResult', 'edit', 'close']
    else keys = ['steps', 'viewResult', 'edit', 'publish']
  }

  const pool: Record<TaskActionKey, TaskActionVM> = { steps, viewResult, edit, publish, close }
  return { actions: keys.map(key => pool[key]), primaryKey: 'viewResult' }
}

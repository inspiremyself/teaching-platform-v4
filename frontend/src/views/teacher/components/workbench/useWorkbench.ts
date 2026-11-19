import { computed, ref, triggerRef, type ComputedRef, type Ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  buildScoringSummary,
  type ScoreLineItem,
  type ScoringSummary,
} from '../scoreDisplay'
import {
  commitDraft as commitSharedDraft,
  createGradingDraft,
  hasDraftChanges as hasSharedDraftChanges,
  setDraftComment as setSharedDraftComment,
  setDraftScore as setSharedDraftScore,
  type DraftItem,
  type GradingDraftState,
} from '../gradingDraft'
import { submissionAdapterOf } from './submissionAdapters'
import { taskAdapterOf } from './taskAdapters'
import { taskActionStrategyOf, type TaskActionHandlers } from './taskActionStrategies'
import type {
  QueueItemVM,
  TaskActionVM,
  TaskRowVM,
  WorkbenchDomain,
  WorkbenchMeta,
} from './types'

interface WorkbenchListHandlers extends TaskActionHandlers {}

export interface DraftPort<TDetail, TDraft> {
  create(detail: TDetail): TDraft
  setScore(draft: TDraft, key: string, score: number | null): void
  setComment(draft: TDraft, key: string, comment: string): void
  hasChanges(draft: TDraft): boolean
  commit(draft: TDraft): void
  forSummary(draft: TDraft): Pick<GradingDraftState, 'scores' | 'marked'>
}

export function createSharedDraftPort<TDetail>(
  itemsOf: (detail: TDetail) => DraftItem[],
): DraftPort<TDetail, GradingDraftState> {
  return {
    create: detail => createGradingDraft(itemsOf(detail)),
    setScore: setSharedDraftScore,
    setComment: setSharedDraftComment,
    hasChanges: hasSharedDraftChanges,
    commit: commitSharedDraft,
    forSummary: draft => ({ scores: draft.scores, marked: draft.marked }),
  }
}

const ACTIVITY_STATUS = { DRAFT: 'DRAFT', PUBLISHED: 'PUBLISHED', CLOSED: 'CLOSED' } as const

function isActive(row: TaskRowVM, now: number): boolean {
  if (row.status !== ACTIVITY_STATUS.PUBLISHED) return false
  const start = row.startAt ? Date.parse(row.startAt) : NaN
  const end = row.endAt ? Date.parse(row.endAt) : NaN
  if (Number.isFinite(start) && start > now) return false
  if (Number.isFinite(end) && end < now) return false
  return true
}

const idOf = (raw: any): number | string =>
  (raw?.submissionId ?? raw?.id ?? '') as number | string

export function useWorkbenchList<T>(options: {
  domain: WorkbenchDomain
  fetchRows: () => Promise<T[]>
  handlers: WorkbenchListHandlers
}): {
  rows: Ref<TaskRowVM[]>
  filteredRows: ComputedRef<TaskRowVM[]>
  filters: {
    keyword: Ref<string>
    status: Ref<string>
    classId: Ref<number | null>
    deadlineBucket: Ref<string>
  }
  classOptions: ComputedRef<{ id: number; label: string }[]>
  meta: ComputedRef<WorkbenchMeta>
  actionsOf: (row: TaskRowVM) => TaskActionVM[]
  refresh: () => Promise<void>
} {
  const adapter = taskAdapterOf(options.domain)
  const rawRows = ref<T[]>([]) as Ref<T[]>
  const rows = ref<TaskRowVM[]>([]) as Ref<TaskRowVM[]>
  const keyword = ref('')
  const status = ref('')
  const classId = ref<number | null>(null)
  const deadlineBucket = ref('ALL')

  const filteredRows = computed(() => {
    const key = keyword.value.trim().toLowerCase()
    return rows.value.filter(row => {
      const snapshot = adapter.filtersOf(row.raw)
      const matchesKeyword =
        !key ||
        [row.title, row.description, row.className].some(item =>
          item.toLowerCase().includes(key),
        )
      const matchesStatus = !status.value || snapshot.status === status.value
      const matchesClass = classId.value === null || snapshot.classId === classId.value
      const matchesBucket = deadlineBucket.value === 'ALL' || snapshot.deadlineBucket === deadlineBucket.value
      return matchesKeyword && matchesStatus && matchesClass && matchesBucket
    })
  })

  const classOptions = computed(() => {
    const map = new Map<number, string>()
    for (const row of rawRows.value as any[]) {
      const id = row?.classId
      if (typeof id !== 'number') continue
      if (!map.has(id)) map.set(id, row.className ?? `班级 ${id}`)
    }
    return [...map.entries()].map(([id, label]) => ({ id, label }))
  })

  const meta = computed<WorkbenchMeta>(() => {
    const now = Date.now()
    return {
      total: rows.value.length,
      filtered: filteredRows.value.length,
      draft: rows.value.filter(row => row.status === ACTIVITY_STATUS.DRAFT).length,
      inProgress: rows.value.filter(row => isActive(row, now)).length,
      submissions: rows.value.reduce((sum, row) => sum + (row.submissionCount ?? 0), 0),
      hint: `当前筛选 ${filteredRows.value.length} / ${rows.value.length}`,
    }
  })

  const actionsOf = (row: TaskRowVM): TaskActionVM[] =>
    taskActionStrategyOf(options.domain, row.status, options.handlers).actions

  const refresh = async (): Promise<void> => {
    rawRows.value = await options.fetchRows()
    rows.value = (rawRows.value as any[]).map(raw => adapter.adapt(raw))
  }

  return {
    rows,
    filteredRows,
    filters: { keyword, status, classId, deadlineBucket },
    classOptions,
    meta,
    actionsOf,
    refresh,
  }
}

export function useGradingWorkspace<TDetail, TDraft>(options: {
  domain: WorkbenchDomain
  loadQueue: () => Promise<unknown[]>
  loadDetail: (id: number | string) => Promise<TDetail>
  scoreLinesOf: (detail: TDetail) => ScoreLineItem[]
  draftPort: DraftPort<TDetail, TDraft>
  statusOf: (detail: TDetail) => string | null | undefined
  isReadOnly: (status: string | null | undefined) => boolean
  handlers: {
    saveGrade(detail: TDetail, draft: TDraft): Promise<void>
    saveAnswerGrade?(detail: TDetail, key: string): Promise<void>
    returnSubmission?(detail: TDetail): Promise<void>
    releaseScores?(): Promise<void>
  }
}): {
  queue: Ref<QueueItemVM[]>
  filteredQueue: ComputedRef<QueueItemVM[]>
  keyword: Ref<string>
  statusFilter: Ref<string>
  selectedId: Ref<number | string | null>
  detail: Ref<TDetail | null>
  draft: Ref<TDraft | null>
  summary: ComputedRef<ScoringSummary>
  readOnly: ComputedRef<boolean>
  setScore: (key: string, score: number | null) => void
  setComment: (key: string, comment: string) => void
  selectSubmission: (id: number | string) => Promise<boolean>
  guardBeforeSwitch: (action: () => void | Promise<void>) => Promise<boolean>
  save: () => Promise<void>
  saveAnswer: (key: string) => Promise<void>
  returnSubmission: () => Promise<void>
  releaseScores: () => Promise<void>
  refresh: () => Promise<void>
} {
  const adapter = submissionAdapterOf(options.domain)
  const rawQueue = ref<unknown[]>([]) as Ref<unknown[]>
  const dirtyIds = ref(new Set<string>())
  const queue = ref<QueueItemVM[]>([]) as Ref<QueueItemVM[]>
  const keyword = ref('')
  const statusFilter = ref('')
  const selectedId = ref<number | string | null>(null) as Ref<number | string | null>
  const detail = ref<TDetail | null>(null) as Ref<TDetail | null>
  const draft = ref<TDraft | null>(null) as Ref<TDraft | null>
  const scoreLines = ref<ScoreLineItem[]>([]) as Ref<ScoreLineItem[]>
  let selectionToken = 0

  const rebuildQueue = (): void => {
    queue.value = (rawQueue.value as any[]).map(raw =>
      adapter.adapt(raw, { draftFlag: dirtyIds.value.has(String(idOf(raw))) }),
    )
  }

  const filteredQueue = computed(() => {
    const key = keyword.value.trim().toLowerCase()
    return queue.value.filter(item => {
      const matchesKeyword = !key || item.studentName.toLowerCase().includes(key)
      const matchesStatus = !statusFilter.value || item.status === statusFilter.value
      return matchesKeyword && matchesStatus
    })
  })

  const summary = computed<ScoringSummary>(() =>
    buildScoringSummary(
      options.domain,
      scoreLines.value,
      draft.value ? options.draftPort.forSummary(draft.value) : { scores: {}, marked: [] },
    ),
  )

  const readOnly = computed(() =>
    options.isReadOnly(detail.value ? options.statusOf(detail.value) : null),
  )

  const markDirty = (dirty: boolean): void => {
    if (selectedId.value === null) return
    const next = new Set(dirtyIds.value)
    if (dirty) next.add(String(selectedId.value))
    else next.delete(String(selectedId.value))
    dirtyIds.value = next
    rebuildQueue()
  }

  const setScore = (key: string, score: number | null): void => {
    if (!draft.value || readOnly.value) return
    options.draftPort.setScore(draft.value, key, score)
    triggerRef(draft)
    markDirty(options.draftPort.hasChanges(draft.value))
  }

  const setComment = (key: string, comment: string): void => {
    if (!draft.value || readOnly.value) return
    options.draftPort.setComment(draft.value, key, comment)
    triggerRef(draft)
    markDirty(options.draftPort.hasChanges(draft.value))
  }

  const guardBeforeSwitch = async (action: () => void | Promise<void>): Promise<boolean> => {
    if (draft.value && options.draftPort.hasChanges(draft.value) && !readOnly.value) {
      try {
        await ElMessageBox.confirm('当前评分尚未保存，确定切换吗？', '提示', {
          type: 'warning',
          confirmButtonText: '确定',
          cancelButtonText: '取消',
        })
      } catch {
        return false
      }
    }
    await action()
    return true
  }

  const loadDetail = async (id: number | string, token: number): Promise<void> => {
    const nextDetail = await options.loadDetail(id)
    if (token !== selectionToken) return
    detail.value = nextDetail
    scoreLines.value = options.scoreLinesOf(nextDetail)
    draft.value = options.draftPort.create(nextDetail)
    rebuildQueue()
  }

  const selectSubmission = (id: number | string): Promise<boolean> =>
    guardBeforeSwitch(async () => {
      const token = ++selectionToken
      selectedId.value = id
      await loadDetail(id, token)
    })

  const refresh = async (): Promise<void> => {
    const token = ++selectionToken
    rawQueue.value = await options.loadQueue()
    if (selectedId.value === null && queue.value.length === 0) rebuildQueue()
    if (selectedId.value === null && queue.value.length > 0) {
      selectedId.value = queue.value[0].id
    }
    if (selectedId.value !== null) await loadDetail(selectedId.value, token)
    else rebuildQueue()
  }

  const save = async (): Promise<void> => {
    if (!detail.value || !draft.value || readOnly.value) return
    await options.handlers.saveGrade(detail.value, draft.value)
    options.draftPort.commit(draft.value)
    markDirty(false)
    await refresh()
  }

  const saveAnswer = async (key: string): Promise<void> => {
    if (!detail.value || !draft.value || !options.handlers.saveAnswerGrade || readOnly.value) return
    await options.handlers.saveAnswerGrade(detail.value, key)
    markDirty(options.draftPort.hasChanges(draft.value))
  }

  const returnSubmission = async (): Promise<void> => {
    if (!detail.value || !options.handlers.returnSubmission) return
    await options.handlers.returnSubmission(detail.value)
    draft.value = null
    dirtyIds.value = new Set()
    rebuildQueue()
    await refresh()
  }

  const releaseScores = async (): Promise<void> => {
    if (!options.handlers.releaseScores) return
    await options.handlers.releaseScores()
    await refresh()
  }

  return {
    queue,
    filteredQueue,
    keyword,
    statusFilter,
    selectedId,
    detail,
    draft,
    summary,
    readOnly,
    setScore,
    setComment,
    selectSubmission,
    guardBeforeSwitch,
    save,
    saveAnswer,
    returnSubmission,
    releaseScores,
    refresh,
  }
}

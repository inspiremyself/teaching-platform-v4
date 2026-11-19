/**
 * 三域批改页共享的草稿状态（Factory + State 快照）。
 * 显式 0 才算已评分；默认占位 null 不算。只读态由调用方通过 markAll 写入基线标记。
 */

export interface GradeItem {
  score: number | null
  teacherComment: string
}

export interface DraftItem extends GradeItem {
  key: string
}

export interface GradingDraftState {
  scores: Record<string, number | null>
  comments: Record<string, string>
  marked: string[]
  baselineMarked: string[]
  initialComments: Record<string, string>
}

export function createGradingDraft(
  items: readonly DraftItem[],
  options?: { markAll?: boolean },
): GradingDraftState {
  const state: GradingDraftState = {
    scores: {},
    comments: {},
    marked: [],
    baselineMarked: [],
    initialComments: {},
  }
  for (const item of items) {
    state.scores[item.key] = item.score ?? null
    state.comments[item.key] = item.teacherComment ?? ''
    state.initialComments[item.key] = item.teacherComment ?? ''
    if (options?.markAll && item.score !== null && item.score !== undefined) {
      state.marked.push(item.key)
      state.baselineMarked.push(item.key)
    }
  }
  return state
}

export function setDraftScore(
  state: GradingDraftState,
  key: string,
  score: number | null,
): void {
  state.scores[key] = score
  const index = state.marked.indexOf(key)
  if (score === null || score === undefined) {
    if (index >= 0) state.marked.splice(index, 1)
  } else if (index < 0) {
    state.marked.push(key)
  }
}

export function setDraftComment(
  state: GradingDraftState,
  key: string,
  comment: string,
): void {
  state.comments[key] = comment
}

export function hasDraftChanges(state: GradingDraftState): boolean {
  const marked = [...state.marked].sort()
  const baseline = [...state.baselineMarked].sort()
  if (marked.length !== baseline.length) return true
  if (marked.some((key, index) => key !== baseline[index])) return true
  return Object.keys(state.comments).some(
    key => (state.comments[key] ?? '') !== (state.initialComments[key] ?? ''),
  )
}

export function commitDraft(state: GradingDraftState): void {
  state.initialComments = { ...state.comments }
  state.baselineMarked = [...state.marked]
}

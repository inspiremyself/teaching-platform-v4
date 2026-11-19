import { EXAM_SUBMISSION_STATUS, SUBMISSION_STATUS } from '@/types/enums'
import type { ExamAnswerDetail, ExamSubmissionDetail, GradeExamPayload } from '@/types/exam'
import {
  createGradingDraft,
  hasDraftChanges as hasSharedDraftChanges,
  setDraftScore as setSharedDraftScore,
  type GradeItem,
  type GradingDraftState,
} from '../components/gradingDraft'
import { scoringStrategyOf } from '../components/scoringStrategies'

export type { GradeItem } from '../components/gradingDraft'

export interface GradingSnapshot {
  status: string
  answers: ExamAnswerDetail[]
  gradeMap: Record<number, GradeItem>
  markedQuestionIds: number[]
  baselineMarkedQuestionIds: number[]
  initialComments: Record<number, string>
}

export interface GradingProgress {
  total: number
  completed: number
  percent: number
}

export function isManualQuestion(answer: ExamAnswerDetail): boolean {
  return answer.isCorrect === null
}

export function manualAnswersOf(answers: ExamAnswerDetail[]): ExamAnswerDetail[] {
  return answers.filter(isManualQuestion)
}

export function hasManualGrading(snapshot: GradingSnapshot): boolean {
  return manualAnswersOf(snapshot.answers).length > 0
}

export function isReadOnlySubmission(status: string): boolean {
  return scoringStrategyOf('EXAM').isReadOnly(status)
}

function toSharedDraft(snapshot: GradingSnapshot): GradingDraftState {
  const scores: Record<string, number | null> = {}
  const comments: Record<string, string> = {}
  for (const [key, item] of Object.entries(snapshot.gradeMap)) {
    scores[key] = item.score
    comments[key] = item.teacherComment
  }
  return {
    scores,
    comments,
    initialComments: { ...snapshot.initialComments },
    marked: snapshot.markedQuestionIds.map(String),
    baselineMarked: snapshot.baselineMarkedQuestionIds.map(String),
  }
}

export function createGradingState(detail: ExamSubmissionDetail | null): GradingSnapshot {
  const snapshot: GradingSnapshot = {
    status: detail?.status ?? '',
    answers: detail?.answers ?? [],
    gradeMap: {},
    markedQuestionIds: [],
    baselineMarkedQuestionIds: [],
    initialComments: {},
  }
  if (!detail) return snapshot

  const shared = createGradingDraft(
    detail.answers.filter(isManualQuestion).map(answer => ({
      key: String(answer.questionId),
      score: answer.score ?? null,
      teacherComment: answer.teacherComment ?? '',
    })),
    { markAll: detail.status === SUBMISSION_STATUS.GRADED },
  )
  for (const answer of detail.answers) {
    if (!isManualQuestion(answer)) continue
    const key = String(answer.questionId)
    snapshot.gradeMap[answer.questionId] = {
      score: shared.scores[key] ?? null,
      teacherComment: shared.comments[key] ?? '',
    }
    snapshot.initialComments[answer.questionId] = shared.initialComments[key] ?? ''
  }
  snapshot.markedQuestionIds = shared.marked.map(Number)
  snapshot.baselineMarkedQuestionIds = shared.baselineMarked.map(Number)
  return snapshot
}

export function setQuestionScore(
  snapshot: GradingSnapshot,
  questionId: number,
  score: number | null,
): void {
  const item = snapshot.gradeMap[questionId]
  if (!item) return
  const shared = toSharedDraft(snapshot)
  setSharedDraftScore(shared, String(questionId), score)
  item.score = shared.scores[String(questionId)] ?? null
  snapshot.markedQuestionIds = shared.marked.map(Number)
}

export function isMarked(snapshot: GradingSnapshot, questionId: number): boolean {
  return snapshot.markedQuestionIds.includes(questionId)
}

export function gradingProgress(snapshot: GradingSnapshot): GradingProgress {
  const manualAnswers = manualAnswersOf(snapshot.answers)
  const total = manualAnswers.length
  const completed = manualAnswers.filter(answer => isMarked(snapshot, answer.questionId)).length
  const percent = total === 0 ? 0 : Math.round((completed / total) * 100)
  return { total, completed, percent }
}

export function manualScoreOf(snapshot: GradingSnapshot): number {
  return manualAnswersOf(snapshot.answers).reduce(
    (sum, answer) => sum + (snapshot.gradeMap[answer.questionId]?.score ?? 0),
    0,
  )
}

export function totalScoreOf(snapshot: GradingSnapshot, autoScore: number | null): number {
  return (autoScore ?? 0) + manualScoreOf(snapshot)
}

export function hasUnsavedChanges(snapshot: GradingSnapshot): boolean {
  if (isReadOnlySubmission(snapshot.status)) return false
  return hasSharedDraftChanges(toSharedDraft(snapshot))
}

export function canSubmitGrade(snapshot: GradingSnapshot): boolean {
  if (isReadOnlySubmission(snapshot.status)) return false
  if (
    snapshot.status !== SUBMISSION_STATUS.SUBMITTED &&
    snapshot.status !== EXAM_SUBMISSION_STATUS.AUTO_SUBMITTED
  ) {
    return false
  }
  const manualAnswers = manualAnswersOf(snapshot.answers)
  if (manualAnswers.length === 0) return true
  return manualAnswers.every(answer => isMarked(snapshot, answer.questionId))
}

export function buildGradePayload(snapshot: GradingSnapshot): GradeExamPayload {
  return {
    answers: manualAnswersOf(snapshot.answers).map(answer => {
      const grade = snapshot.gradeMap[answer.questionId]
      return {
        questionId: answer.questionId,
        score: grade?.score ?? 0,
        teacherComment: grade?.teacherComment || undefined,
      }
    }),
  }
}

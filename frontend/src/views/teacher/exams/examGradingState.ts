import { EXAM_SUBMISSION_STATUS, SUBMISSION_STATUS } from '@/types/enums'
import type { ExamAnswerDetail, ExamSubmissionDetail, GradeExamPayload } from '@/types/exam'

export interface GradeItem {
  score: number | null
  teacherComment: string
}

export interface GradingSnapshot {
  status: string
  answers: ExamAnswerDetail[]
  gradeMap: Record<number, GradeItem>
  markedQuestionIds: number[]
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
  return status === SUBMISSION_STATUS.GRADED || status === EXAM_SUBMISSION_STATUS.IN_PROGRESS
}

export function createGradingState(detail: ExamSubmissionDetail | null): GradingSnapshot {
  const snapshot: GradingSnapshot = {
    status: detail?.status ?? '',
    answers: detail?.answers ?? [],
    gradeMap: {},
    markedQuestionIds: [],
    initialComments: {},
  }
  if (!detail) return snapshot

  for (const answer of detail.answers) {
    if (!isManualQuestion(answer)) continue
    const comment = answer.teacherComment ?? ''
    snapshot.gradeMap[answer.questionId] = { score: answer.score ?? null, teacherComment: comment }
    snapshot.initialComments[answer.questionId] = comment
    if (detail.status === SUBMISSION_STATUS.GRADED && answer.score !== null) {
      snapshot.markedQuestionIds.push(answer.questionId)
    }
  }
  return snapshot
}

export function setQuestionScore(
  snapshot: GradingSnapshot,
  questionId: number,
  score: number | null,
): void {
  const item = snapshot.gradeMap[questionId]
  if (!item) return
  item.score = score
  const index = snapshot.markedQuestionIds.indexOf(questionId)
  if (score === null) {
    if (index >= 0) snapshot.markedQuestionIds.splice(index, 1)
  } else if (index < 0) {
    snapshot.markedQuestionIds.push(questionId)
  }
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
  if (snapshot.markedQuestionIds.length > 0) return true
  return manualAnswersOf(snapshot.answers).some(answer => {
    const current = snapshot.gradeMap[answer.questionId]?.teacherComment ?? ''
    const initial = snapshot.initialComments[answer.questionId] ?? ''
    return current !== initial
  })
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

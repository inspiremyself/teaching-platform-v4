import type { UnifiedQuestionAnswerPayload } from './question-answer'
import type { ActivityStatus, ExamSubmissionStatus, ScoreVisibilityMode, SubmissionStatus } from './enums'

export type { ScoreVisibilityMode } from './enums'

export interface ExamListItem {
  id: number
  title: string
  description?: string
  classId: number
  className?: string
  status: ActivityStatus
  startAt: string | null
  endAt: string | null
  durationMinutes: number
  submissionCount?: number
  questionCount?: number
  submissionStatus?: ExamSubmissionStatus | null
  totalScore?: number | null
  canStart?: boolean
  canResume?: boolean
  canViewResult?: boolean
  scoreVisibilityMode?: ScoreVisibilityMode | null
}

export interface ExamQuestionView {
  questionId: number
  questionType?: string
  type?: string
  stem: string
  options?: Array<{ key: string; label: string }>
  optionsJson?: string | null
  score?: number
  questionScore?: number
  answer?: string | null
  studentAnswer?: string | null
  standardAnswer?: string | null
  answerJson?: string | null
  isCorrect?: boolean | null
  earnedScore?: number | null
  suggestedScore?: number | null
  autoScore?: number | null
  scoreSource?: string | null
  judgeDetail?: string | null
  acceptedAutoScore?: boolean | null
  teacherComment?: string | null
  sortOrder?: number
  analysisText?: string | null
}

interface ExamDetailBase {
  id: number
  title: string
  description: string
  classId: number
  className?: string
  status: ActivityStatus
  startAt: string | null
  endAt: string | null
  durationMinutes: number
  scoreVisibilityMode?: ScoreVisibilityMode | null
  shuffleQuestions?: boolean
  questions: ExamQuestionView[]
}

/** 学生端考试详情：作答状态、时间窗与成绩可见性 */
export interface ExamDetailForStudent extends ExamDetailBase {
  notStarted?: boolean
  submissionId?: number
  submissionStatus?: ExamSubmissionStatus
  startedAt?: string
  deadlineAt?: string
  remainingSeconds?: number
  autoScore?: number | null
  manualScore?: number | null
  totalScore?: number | null
  resultAvailable?: boolean
  gradingInProgress?: boolean
}

/** 教师端考试详情：成绩发布状态与总评 */
export interface ExamDetailForTeacher extends ExamDetailBase {
  scoreReleased?: boolean
  teacherComment?: string | null
}

/** 同一详情端点按角色返回不同字段，页面按可选字段读取 */
export type ExamDetail = ExamDetailForStudent & ExamDetailForTeacher

export interface CreateExamPayload {
  title: string
  description?: string
  classId?: number
  startAt?: string
  endAt?: string
  durationMinutes?: number
  status?: ActivityStatus
  scoreVisibilityMode?: ScoreVisibilityMode
  shuffleQuestions?: boolean
  questions?: Array<{
    questionId: number
    sourceType?: 'BANK'
    sortOrder: number
    questionScore: number
  }>
}

export interface UpdateExamPayload {
  title: string
  description: string
  classId: number
  startAt?: string
  endAt?: string
  durationMinutes: number
  scoreVisibilityMode?: ScoreVisibilityMode
  shuffleQuestions?: boolean
  questions: Array<{
    questionId: number
    sourceType?: 'BANK'
    sortOrder: number
    questionScore: number
  }>
}

export interface SubmitExamPayload {
  answers: Array<Omit<UnifiedQuestionAnswerPayload, 'questionRefId'> & { questionId: number; answer?: any }>
}

export interface ExamSubmissionListItem {
  submissionId: number
  examId?: number
  examTitle?: string
  classId?: number
  className?: string
  studentId: number
  studentName: string
  studentUsername: string
  studentNo?: string
  status: SubmissionStatus
  autoScore: number | null
  manualScore: number | null
  totalScore: number | null
  startedAt: string | null
  submittedAt: string | null
}

export interface ExamAnswerDetail {
  questionId: number
  questionType: string
  stem: string
  optionsJson: string | null
  standardAnswer: string | null
  studentAnswer: string | null
  isCorrect: boolean | null
  score: number | null
  autoScore?: number | null
  suggestedScore?: number | null
  scoreSource?: string | null
  judgeDetail?: string | null
  acceptedAutoScore?: boolean | null
  teacherComment: string | null
  questionScore: number
  analysisText?: string | null
}

export interface ExamSubmissionDetail {
  submissionId: number
  examId?: number
  examTitle?: string
  classId?: number
  className?: string
  studentId: number
  studentName: string
  studentUsername?: string
  studentNo?: string
  status: SubmissionStatus
  autoScore: number | null
  manualScore: number | null
  totalScore: number | null
  teacherComment?: string | null
  answers: ExamAnswerDetail[]
}

export interface GradeExamPayload {
  answers?: Array<{ questionId: number; score: number; teacherComment?: string }>
  manualScore?: number
}

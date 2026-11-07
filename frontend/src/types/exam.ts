import type { UnifiedQuestionAnswerPayload } from './question-answer'

export type ScoreVisibilityMode = 'IMMEDIATE' | 'AFTER_TEACHER_CONFIRM' | 'AFTER_DEADLINE' | 'MANUAL_RELEASE'

export interface ExamListItem {
  id: number
  title: string
  description?: string
  classId: number
  className?: string
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED'
  startAt: string | null
  endAt: string | null
  durationMinutes: number
  submissionCount?: number
  questionCount?: number
  submissionStatus?: string | null
  totalScore?: number | null
  canStart?: boolean
  canResume?: boolean
  canViewResult?: boolean
  scoreVisibilityMode?: ScoreVisibilityMode | null
}

export interface ExamQuestionView {
  questionId: number
  questionType?: string
  type?: string // backend returns 'type' not 'questionType'
  stem: string
  options?: Array<{ key: string; label: string }>
  optionsJson?: string | null // backend returns optionsJson as string
  score?: number
  questionScore?: number // backend returns 'questionScore'
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

export interface ExamDetail {
  id: number
  title: string
  description: string
  classId: number
  className?: string
  status: string
  startAt: string | null
  endAt: string | null
  durationMinutes: number
  scoreVisibilityMode?: ScoreVisibilityMode | null
  shuffleQuestions?: boolean
  questions: ExamQuestionView[]
  // Student-specific fields
  submissionId?: number
  submissionStatus?: string
  deadlineAt?: string
  remainingSeconds?: number
  // 成绩可见性由 resultAvailable 控制；未发布时后端将以下分数遮蔽为 null
  autoScore?: number | null
  manualScore?: number | null
  totalScore?: number | null
  resultAvailable?: boolean
  // 主观题未批完时为 true，前端显示「阅卷中」而非分数
  gradingInProgress?: boolean
  // 教师端专用：成绩是否已发布，用于「发布 / 重新发布成绩」入口
  scoreReleased?: boolean
  teacherComment?: string | null
}

export interface CreateExamPayload {
  title: string
  description?: string
  classId?: number
  startAt?: string
  endAt?: string
  durationMinutes?: number
  status?: string
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
  status: string
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
  status: string
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

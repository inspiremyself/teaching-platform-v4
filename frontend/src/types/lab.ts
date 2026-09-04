export type LabStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | string;

export type LabQuestionType =
  | 'TEXT'
  | 'SINGLE_CHOICE'
  | 'MULTIPLE_CHOICE'
  | 'TRUE_FALSE'
  | 'FILL_BLANK'
  | 'SHORT_ANSWER'
  | 'CODE'
  | string;

export type LabQuestionGradingMode = 'exact' | string;

export type LabScoreSource = 'AUTO' | 'SUGGESTED' | 'TEACHER' | string;

export type ScoreVisibilityMode = 'IMMEDIATE' | 'AFTER_TEACHER_CONFIRM' | 'AFTER_DEADLINE' | 'MANUAL_RELEASE' | string;

export interface LabStepAutoRuleOption {
  key: string;
  label: string;
}

export interface LabTextKeywordRule {
  term: string;
  weight: number;
}

export interface TextQuestionRuleConfig {
  keywords: LabTextKeywordRule[];
  minLength?: number;
  commentTemplate?: string;
}

export interface ChoiceQuestionRuleConfig {
  options: LabStepAutoRuleOption[];
  correctAnswer: string[];
  gradingMode: LabQuestionGradingMode;
}

export type LabStepRuleConfig = TextQuestionRuleConfig | ChoiceQuestionRuleConfig;

export interface FillBlankRuleItem {
  answers: string[];
  score?: number;
}

export interface FillBlankRuleConfig {
  blanks: FillBlankRuleItem[];
  ignoreCase?: boolean;
}

export interface CodeQuestionRuleConfig {
  language?: string;
  rubric?: string;
}

export type LabAdvancedRuleConfig = FillBlankRuleConfig | CodeQuestionRuleConfig | Record<string, unknown>;

export type LabReportStatus = 'SAVED' | 'SUBMITTED' | 'GRADED' | string;

export interface LabAnswerImageMeta {
  path: string;
  name: string;
  contentType: string;
  size: number;
  compressed?: boolean;
  originalSize?: number;
}

export const LAB_EXPERIMENT_TYPE_LABELS: Record<number, string> = {
  1: '基础实验',
  2: '验证实验',
  3: '综合实验',
  4: '设计实验',
};

export interface LabItem {
  id: number;
  title: string;
  description: string;
  experimentContent?: string;
  experimentType?: number | null;
  materialId?: number | null;
  materialTitle?: string | null;
  materialFileName?: string | null;
  materialDownloadUrl?: string | null;
  status: LabStatus;
  classId?: number;
  className?: string;
  scoreVisibilityMode?: ScoreVisibilityMode | null;
  summaryRequired?: boolean;
  stepCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateLabPayload {
  title: string;
  description: string;
  experimentContent: string;
  experimentType?: number | null;
  classId: number;
  status: LabStatus;
  materialId?: number | null;
  materialTitle?: string | null;
  materialFileName?: string | null;
  materialDownloadUrl?: string | null;
  scoreVisibilityMode?: ScoreVisibilityMode | null;
  summaryRequired: boolean;
}

export interface UpdateLabPayload extends CreateLabPayload {}

export interface ChangeLabStatusPayload {
  status: LabStatus;
}

export interface LabStepItem {
  id: number;
  labId: number;
  stepNo: number;
  title: string;
  questionId?: number | null;
  content: string;
  questionType: LabQuestionType;
  answerConfigJson?: string | null;
  questionSnapshotJson?: string | null;
  editorLanguage?: string | null;
  stepScore: number;
  allowPaste: boolean;
}

export interface CreateLabStepPayload {
  stepNo: number;
  title: string;
  content: string;
  questionType: LabQuestionType;
  answerConfigJson: string;
  stepScore: number;
  allowPaste: boolean;
}

export interface UpdateLabStepPayload extends CreateLabStepPayload {}

export interface LabReportItem {
  id: number;
  labId: number;
  labTitle?: string;
  classId?: number;
  studentId?: number;
  studentNo?: string;
  studentUsername?: string;
  studentName?: string;
  className?: string;
  submitStatus?: LabReportStatus;
  totalScore?: number;
  summaryRequired?: boolean;
  submittedAt?: string;
  gradedAt?: string;
}

export interface LabReportAnswerItem {
  answerId?: number;
  id?: number;
  stepId?: number;
  stepNo?: number;
  stepTitle?: string;
  title?: string;
  questionType?: LabQuestionType;
  answerText?: string;
  answerPayloadJson?: string | null;
  images?: LabAnswerImageMeta[];
  answerFilePath?: string | null;
  answerFileUrl?: string | null;
  standardAnswer?: string | null;
  studentAnswer?: string | null;
  score?: number | null;
  maxScore?: number | null;
  stepScore?: number | null;
  teacherComment?: string | null;
  plagiarismRate?: number | null;
  similarityRate?: number | null;
  autoScore?: number | null;
  suggestedScore?: number | null;
  scoreSource?: LabScoreSource | null;
  autoJudgeDetail?: string | null;
  judgeDetail?: string | null;
  acceptedAutoScore?: boolean | null;
}

export interface LabReportDetail extends LabReportItem {
  summaryRequired?: boolean;
  summaryText?: string | null;
  teacherComment?: string;
  reportComment?: string;
  answers?: LabReportAnswerItem[];
  items?: LabReportAnswerItem[];
}

export interface TeacherLabReportViewStep {
  stepId: number;
  stepNo: number;
  stepTitle: string;
  content: string;
  questionType?: LabQuestionType;
  answerText?: string | null;
}

export interface TeacherLabReportView {
  labId: number;
  studentId: number;
  courseName: string;
  reportTitle: string;
  reportDate: string;
  className?: string | null;
  studentNo?: string | null;
  studentName?: string | null;
  labTitle: string;
  purpose?: string | null;
  experimentContent?: string | null;
  totalScore?: number | null;
  teacherComment?: string | null;
  summaryText?: string | null;
  steps: TeacherLabReportViewStep[];
}

export interface StudentLabListItem {
  id: number;
  title: string;
  status: LabStatus;
  classId: number;
  className: string;
  description?: string;
  experimentContent?: string;
  experimentType?: number | null;
  materialId?: number | null;
  materialTitle?: string | null;
  materialFileName?: string | null;
  materialDownloadUrl?: string | null;
  summaryRequired?: boolean;
  submissionStatus?: LabReportStatus | null;
  stepCount?: number;
  totalScore?: number | null;
  teacherComment?: string | null;
  startAt?: string | null;
  dueAt?: string | null;
}

export interface StudentLabStepItem {
  id: number;
  stepNo: number;
  title: string;
  questionType: LabQuestionType;
  content: string;
  stepScore: number;
  allowPaste: boolean;
  answerText: string;
  options?: StudentChoiceOption[];
  blanks?: StudentBlankMeta[];
  answerPayloadJson?: string | null;
  editorLanguage?: string | null;
  score?: number | null;
  teacherComment?: string | null;
}

export interface StudentLabDetail {
  id: number;
  title: string;
  description: string;
  experimentContent?: string;
  experimentType?: number | null;
  materialId?: number | null;
  materialTitle?: string | null;
  materialFileName?: string | null;
  materialDownloadUrl?: string | null;
  status: LabStatus;
  classId: number;
  className?: string;
  startAt?: string | null;
  dueAt?: string | null;
  summaryRequired?: boolean;
  summaryText?: string | null;
  submissionStatus?: LabReportStatus | null;
  scoreVisibilityMode?: ScoreVisibilityMode | null;
  totalScore?: number | null;
  teacherComment?: string | null;
  items?: StudentLabStepItem[];
  steps?: StudentLabStepItem[];
}

export interface SaveLabAnswerPayload {
  answerText: string;
  answerPayloadJson: string;
}

export interface StudentChoiceOption {
  key: string;
  label: string;
}

export interface StudentBlankMeta {
  index: number;
  token: string;
}

export interface SubmitLabPayload {
  summaryText: string;
}

export interface SaveLabAnswerResult {
  answerId: number;
  submissionStatus: LabReportStatus;
}

export interface SubmitLabResult {
  submissionId: number;
  status: LabReportStatus;
}

export interface TeacherLabBlankItem {
  id: number;
  labId: number;
  stepId?: number;
  stepNo: number;
  title: string;
  questionType: LabQuestionType;
  questionId?: number | null;
  content: string;
  stepScore: number;
}

export interface TeacherLabBlankAnswerDistributionItem {
  answerText: string;
  normalizedAnswer: string;
  count: number;
  accepted: boolean;
}

export interface TeacherLabBlankAnswerDistribution {
  item: TeacherLabBlankItem;
  acceptedAnswers: string[];
  answerDistribution: TeacherLabBlankAnswerDistributionItem[];
}

export interface SaveTeacherLabBlankAcceptedAnswersPayload {
  experimentItemId: number;
  acceptedAnswers: string[];
}

export interface SaveTeacherLabBlankAcceptedAnswersResult {
  saved: boolean;
  experimentItemId: number;
  regradedCount: number;
  acceptedAnswers: string[];
}

export interface GradeLabReportItemPayload {
  answerId: number;
  score: number;
  teacherComment: string;
}

export interface GradeLabReportPayload {
  items: GradeLabReportItemPayload[];
  teacherComment: string;
}

export interface ConfirmLabStepScorePayload {
  answerId: number;
  score?: number;
  teacherComment?: string;
  acceptSuggested?: boolean;
}

export interface LabReportQuery {
  keyword?: string;
  status?: string;
}

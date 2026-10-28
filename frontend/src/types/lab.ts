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

/**
 * 从已有实验深度拷贝的请求体。
 * 仅承载目标班级 / 新名称 / 发布状态；题项、填空答案、实验指导引用等由服务端从源实验复制。
 */
export interface CopyLabPayload {
  classId: number;
  title?: string;
  status?: LabStatus;
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
  /** 行为指标摘要（懒算后恒有值；null 仅防御性兜底，无数据判定以 riskFlags 含 NO_BEHAVIOR_DATA 为准） */
  behavior?: LabReportBehavior | null;
}

/** 行为指标摘要（技术方案 §5.2；列表 / 导航队列 / 提交总览共用同一结构） */
export interface LabReportBehavior {
  /** 总作答时长（毫秒）；SAVED 行为"截至最近事件"口径且 durationOpen=true；无数据源为 null */
  totalDurationMs: number | null;
  /** 活跃时长（毫秒），心跳求和；无心跳为 null，前端显示 `-` */
  activeDurationMs: number | null;
  /** true = SAVED 行，时长截至最近事件（队列页加 `*` 后缀） */
  durationOpen: boolean;
  saveCount: number;
  pasteCount: number;
  pastedChars: number;
  /** 粘贴被禁题上的粘贴尝试次数（metrics.paste_blocked_count 增量暴露，D3） */
  pasteBlockedCount: number;
  hiddenCount: number;
  hiddenMs: number;
  /** 窗口失焦次数（metrics.blur_count 增量暴露，D3） */
  blurCount: number;
  /** 作答会话数（LAB_ENTER 计数，D17①） */
  sessionCount: number;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  riskFlags: string[];
}

/** 行为详情时间线条目（§5.3） */
export interface LabBehaviorTimelineItem {
  eventType: string;
  occurredAt: string;
  stepId?: number | null;
  payload?: Record<string, unknown>;
}

/** 行为详情：metrics 在 §5.2 摘要基础上扩展逐题停留与计算时间 */
export interface LabReportBehaviorDetail {
  metrics: (LabReportBehavior & {
    stepDwellMs: Record<string, number>;
    firstStartedAt?: string | null;
    lastEventAt?: string | null;
    computedAt?: string | null;
  }) | null;
  timeline: LabBehaviorTimelineItem[];
  timelineTotal: number;
}

/** 行为事件类型枚举（与后端 BehaviorEventType 对齐，恰好 11 值，D3） */
export type LabBehaviorEventType =
  | 'LAB_ENTER'
  | 'LAB_LEAVE'
  | 'STEP_VIEW'
  | 'PASTE'
  | 'VISIBILITY'
  | 'WINDOW_BLUR'
  | 'WINDOW_FOCUS'
  | 'HEARTBEAT'
  | 'SUBMIT_CLICK'
  | 'SUBMIT_BLOCKED'
  | 'SAVE_FAILED';

/** 单条行为事件（§5.1；occurredAt 为 ISO 字符串，payload 不上报 blocked——服务端回填） */
export interface LabBehaviorEventItem {
  eventUuid: string;
  eventType: LabBehaviorEventType;
  occurredAt: string;
  stepId?: number | null;
  payload?: Record<string, unknown>;
}

/** 批量上报响应；closed=true 表示实验已关闭、采集器应立即停采（D16 配对行为） */
export interface LabBehaviorIngestResult {
  accepted: number;
  dropped: number;
  closed?: boolean;
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
  images?: LabAnswerImageMeta[];
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

export interface ReturnLabReportResult {
  submissionId: number;
  status: LabReportStatus;
  labId: number;
  studentId: number;
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
  labId?: number;
  /** 行为风险等级筛选（D10）：NONE = metrics 缺失或含 NO_BEHAVIOR_DATA */
  riskLevel?: 'LOW' | 'MEDIUM' | 'HIGH' | 'NONE';
  page?: number;
  pageSize?: number;
}

/** 教师端实验报告全局统计（全量口径，不受列表筛选影响） */
export interface TeacherLabReportStats {
  total: number;
  submitted: number;
  graded: number;
}

export interface LabSubmissionOverviewItem {
  stepId: number;
  stepNo: number;
  title: string;
}

export interface LabSubmissionOverviewCell {
  stepId: number;
  answered: boolean;
  hasText: boolean;
  imageCount: number;
}

export interface LabSubmissionOverviewStudent {
  studentId: number;
  studentNo: string;
  studentName: string;
  submissionId?: number | null;
  submitStatus?: LabReportStatus | null;
  submittedAt?: string | null;
  /** 行为摘要（§5.4）；无提交的学生为 null */
  behavior?: LabReportBehavior | null;
  cells: LabSubmissionOverviewCell[];
}

export interface LabSubmissionOverview {
  labId: number;
  labTitle: string;
  classId: number;
  items: LabSubmissionOverviewItem[];
  students: LabSubmissionOverviewStudent[];
}

export interface StudentLabSubmissionItem {
  submissionId: number;
  labId: number;
  labTitle: string;
  classId?: number;
  className?: string;
  submitStatus: LabReportStatus;
  totalScore?: number | null;
  submittedAt?: string | null;
  gradedAt?: string | null;
}

export interface LabItemAnswerWallEntry {
  studentId: number;
  studentNo: string;
  studentName: string;
  submissionId?: number | null;
  submitStatus?: LabReportStatus | null;
  answerText: string;
  images: LabAnswerImageMeta[];
  score?: number | null;
  teacherComment?: string | null;
}

export interface LabItemAnswerWall {
  labId: number;
  labTitle: string;
  itemId: number;
  stepNo: number;
  title: string;
  content: string;
  answers: LabItemAnswerWallEntry[];
}

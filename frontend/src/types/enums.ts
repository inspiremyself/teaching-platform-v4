/** 已知值取自后端枚举全集；开放字符串用于兼容后端扩展值，同时保留字面量补全 */
type LiteralUnion<T extends string> = T | (string & {});

export type ActivityStatus = LiteralUnion<'DRAFT' | 'PUBLISHED' | 'CLOSED'>;
export type SubmissionStatus = LiteralUnion<'SAVED' | 'SUBMITTED' | 'GRADED'>;
export type ScoreVisibilityMode = LiteralUnion<
  'IMMEDIATE' | 'AFTER_TEACHER_CONFIRM' | 'AFTER_DEADLINE' | 'MANUAL_RELEASE'
>;
export type BackendQuestionType = LiteralUnion<
  'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'FILL_BLANK' | 'SUBJECTIVE'
>;
export type ExamSubmissionStatus = LiteralUnion<
  'IN_PROGRESS' | 'SUBMITTED' | 'AUTO_SUBMITTED' | 'GRADED'
>;

export const ACTIVITY_STATUS = {
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
  CLOSED: 'CLOSED',
} as const;

export const SUBMISSION_STATUS = {
  SAVED: 'SAVED',
  SUBMITTED: 'SUBMITTED',
  GRADED: 'GRADED',
} as const;

export const SCORE_VISIBILITY_MODE = {
  IMMEDIATE: 'IMMEDIATE',
  AFTER_TEACHER_CONFIRM: 'AFTER_TEACHER_CONFIRM',
  AFTER_DEADLINE: 'AFTER_DEADLINE',
  MANUAL_RELEASE: 'MANUAL_RELEASE',
} as const;

export const EXAM_SUBMISSION_STATUS = {
  IN_PROGRESS: 'IN_PROGRESS',
  SUBMITTED: 'SUBMITTED',
  AUTO_SUBMITTED: 'AUTO_SUBMITTED',
  GRADED: 'GRADED',
} as const;

export const BACKEND_QUESTION_TYPE = {
  SINGLE_CHOICE: 'SINGLE_CHOICE',
  MULTIPLE_CHOICE: 'MULTIPLE_CHOICE',
  TRUE_FALSE: 'TRUE_FALSE',
  FILL_BLANK: 'FILL_BLANK',
  SUBJECTIVE: 'SUBJECTIVE',
} as const;

export type CoursewareStatus = LiteralUnion<'DRAFT' | 'PUBLISHED'>;

export type ContentFilterType = LiteralUnion<
  'ALL' | 'UNMOUNTED' | 'TEXTBOOK' | 'TEXTBOOK_SECTION' | 'COURSEWARE_SECTION'
>;

export type CoursewareAssetKind = LiteralUnion<'IMAGE' | 'AUDIO'>;

export type PlatformSubjectStatus = LiteralUnion<'ACTIVE' | 'DISABLED'>;

export const COURSEWARE_STATUS = {
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
} as const;

export const CONTENT_FILTER_TYPE = {
  ALL: 'ALL',
  UNMOUNTED: 'UNMOUNTED',
  TEXTBOOK: 'TEXTBOOK',
  TEXTBOOK_SECTION: 'TEXTBOOK_SECTION',
  COURSEWARE_SECTION: 'COURSEWARE_SECTION',
} as const;

export const COURSEWARE_ASSET_KIND = {
  IMAGE: 'IMAGE',
  AUDIO: 'AUDIO',
} as const;

export const PLATFORM_SUBJECT_STATUS = {
  ACTIVE: 'ACTIVE',
  DISABLED: 'DISABLED',
} as const;

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

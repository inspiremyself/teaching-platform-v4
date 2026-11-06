export type QuestionSourceType = 'BANK' | 'INLINE'

export interface ConfiguredQuestionItem {
  localId: string
  questionBankId?: number | null
  /** 作业题目主键：已有题目才有，未保存的新题目为 null */
  homeworkQuestionId?: number | null
  sourceType: QuestionSourceType
  questionType: string
  stem: string
  sortOrder: number
  score: number
  options?: Array<{ key: string; label: string }> | null
  optionsJson?: string | null
  answerJson?: string | null
  analysisText?: string | null
  scoringConfigJson?: string | null
}

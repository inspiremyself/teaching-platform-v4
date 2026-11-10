/** 批量导入：通过校验、可直接提交的单题草稿（题库不含分值，导入不读取 score） */
export interface ImportQuestionDraft {
  code: string
  type: 'SINGLE' | 'MULTI' | 'JUDGE'
  stem: string
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  options: Array<{ key: string; content: string }>
  answer: string[]
  analysis: string
}

/** 批量导入：预览行，行号 = JSON 数组下标 + 1 */
export interface ImportPreviewRow {
  line: number
  code: string
  typeLabel: string
  stemBrief: string
  answerText: string
  hasAnalysis: boolean
  status: 'READY' | 'SKIP' | 'ERROR'
  message: string
  draft?: ImportQuestionDraft
}

/** 批量导入：解析结果汇总 */
export interface ImportParseResult {
  rows: ImportPreviewRow[]
  readyCount: number
  skipCount: number
  errorCount: number
}

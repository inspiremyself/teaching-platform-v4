import type { ImportParseResult, ImportPreviewRow, ImportQuestionDraft } from '@/types/question-import'

/** 题型展示名 */
const TYPE_LABEL: Record<ImportQuestionDraft['type'], string> = {
  SINGLE: '单选',
  MULTI: '多选',
  JUDGE: '判断',
}

const ALLOWED_TYPES: ImportQuestionDraft['type'][] = ['SINGLE', 'MULTI', 'JUDGE']
const ALLOWED_DIFFICULTIES: ImportQuestionDraft['difficulty'][] = ['EASY', 'MEDIUM', 'HARD']
/** 导入 JSON 白名单：题库只接受内容字段，任何未知键（含废止的 score）一律报错 */
const ALLOWED_IMPORT_FIELDS = new Set([
  'code',
  'type',
  'stem',
  'difficulty',
  'options',
  'answer',
  'analysis',
])
/** 判断题缺省 options */
const JUDGE_DEFAULT_OPTIONS = [
  { key: 'T', content: '正确' },
  { key: 'F', content: '错误' },
]

/** 编码规范化：去首尾空格 + 全大写（与后端 QuestionService 保持一致） */
export function normalizeImportCode(code: string): string {
  return code.trim().toUpperCase()
}

interface RowValidation {
  draft?: ImportQuestionDraft
  /** 校验失败原因；draft 存在时为空串 */
  message: string
}

/** 取字符串字段的 trim 值，非字符串返回空串 */
function textOf(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

/** 题干摘要：最多 40 字 */
function briefOf(stem: string): string {
  return stem.length > 40 ? `${stem.slice(0, 40)}…` : stem
}

/**
 * 单题阻断性校验（方案 §6.3.2 表 1-18），任一条不通过即返回失败原因。
 */
function validateRow(raw: unknown, line: number): RowValidation {
  // 1. 元素必须是对象
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) {
    return { message: `第 ${line} 行：题目必须是对象` }
  }
  const record = raw as Record<string, unknown>

  // 2. 字段白名单：题库只接受内容字段，score 等废止字段一律报错
  const unknownFields = Object.keys(record).filter(key => !ALLOWED_IMPORT_FIELDS.has(key))
  if (unknownFields.length > 0) {
    return { message: `不支持的字段：${unknownFields.join(', ')}（题库只接受内容字段）` }
  }

  // 3-4. code 非空且规范化后不超过 64 字符
  const rawCode = textOf(record.code)
  if (!rawCode) return { message: 'code 不能为空' }
  const code = normalizeImportCode(rawCode)
  if (code.length > 64) return { message: 'code 长度不能超过 64 个字符' }

  // 5. stem 非空
  const stem = textOf(record.stem)
  if (!stem) return { message: 'stem 不能为空' }

  // 6. type 仅支持 SINGLE / MULTI / JUDGE
  const typeValue = textOf(record.type).toUpperCase()
  if (!ALLOWED_TYPES.includes(typeValue as ImportQuestionDraft['type'])) {
    return { message: 'type 必须为 SINGLE / MULTI / JUDGE' }
  }
  const type = typeValue as ImportQuestionDraft['type']

  // 7. difficulty 缺省 EASY
  let difficulty: ImportQuestionDraft['difficulty'] = 'EASY'
  if (record.difficulty !== undefined && record.difficulty !== null) {
    const value = textOf(record.difficulty).toUpperCase()
    if (!ALLOWED_DIFFICULTIES.includes(value as ImportQuestionDraft['difficulty'])) {
      return { message: 'difficulty 必须为 EASY / MEDIUM / HARD' }
    }
    difficulty = value as ImportQuestionDraft['difficulty']
  }

  // 8. options 缺省：判断题补 T/F，选择题报错
  let rawOptions = record.options
  if (rawOptions === undefined || rawOptions === null) {
    if (type !== 'JUDGE') return { message: '选择题必须提供 options' }
    rawOptions = JUDGE_DEFAULT_OPTIONS
  }

  // 9. options 必须是长度 >= 2 的数组
  if (!Array.isArray(rawOptions) || rawOptions.length < 2) {
    return { message: 'options 至少 2 个' }
  }

  // 10-11. 每个选项 key/content 非空，key 不重复
  const options: Array<{ key: string; content: string }> = []
  const optionKeys = new Set<string>()
  for (const item of rawOptions) {
    if (item === null || typeof item !== 'object' || Array.isArray(item)) {
      return { message: 'options.content 不能为空' }
    }
    const option = item as Record<string, unknown>
    const key = textOf(option.key).toUpperCase()
    const content = textOf(option.content)
    if (!key || !content) return { message: 'options.content 不能为空' }
    if (optionKeys.has(key)) return { message: `options.key 重复：${key}` }
    optionKeys.add(key)
    options.push({ key, content })
  }

  // 12. 判断题 key 集合恰为 {T, F}
  if (type === 'JUDGE' && (options.length !== 2 || !optionKeys.has('T') || !optionKeys.has('F'))) {
    return { message: '判断题 options 的 key 必须为 T / F' }
  }

  // 13. answer 必须是非空数组
  const rawAnswer = record.answer
  if (!Array.isArray(rawAnswer) || rawAnswer.length === 0) {
    return { message: 'answer 不能为空' }
  }

  // 14. 判断题答案先做专属校验，保证 true/false 得到判断题文案
  if (type === 'JUDGE') {
    const invalid = rawAnswer.some(item => {
      const value = textOf(item).toUpperCase()
      return value !== 'T' && value !== 'F'
    })
    if (invalid) return { message: '判断题答案必须为 T / F' }
  }

  // 15. 答案 key 必须归属到 options
  const answer: string[] = []
  for (const item of rawAnswer) {
    const value = textOf(item).toUpperCase()
    if (!optionKeys.has(value)) {
      return { message: `答案 key 不在选项中：${textOf(item) || String(item ?? '')}` }
    }
    answer.push(value)
  }

  // 16-17. 单选恰好 1 个答案；多选至少 1 个
  if (type === 'SINGLE' && answer.length !== 1) return { message: '单选题答案必须恰好 1 个' }
  if (type === 'MULTI' && answer.length < 1) return { message: '多选题答案至少 1 个' }

  // 18. analysis 缺省空串，出现时必须为字符串
  let analysis = ''
  if (record.analysis !== undefined && record.analysis !== null) {
    if (typeof record.analysis !== 'string') return { message: 'analysis 必须为字符串' }
    analysis = record.analysis
  }

  return {
    message: '',
    draft: { code, type, stem, difficulty, options, answer, analysis },
  }
}

/**
 * 解析粘贴的 JSON 文本并产出预览行。
 * 致命错误（空文本 / 非法 JSON / 根节点非数组 / 空数组）时 result 为 null。
 */
export function parseImportJson(
  text: string,
  existingCodes: Set<string>,
): { result: ImportParseResult | null; fatalError: string } {
  const trimmed = text.trim()
  if (!trimmed) return { result: null, fatalError: '请粘贴题目 JSON' }

  let parsed: unknown
  try {
    parsed = JSON.parse(trimmed)
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error)
    return { result: null, fatalError: `JSON 解析失败：${detail}` }
  }

  if (!Array.isArray(parsed)) return { result: null, fatalError: '根节点必须是数组' }
  if (parsed.length === 0) return { result: null, fatalError: '至少需要一道题目' }

  const rows: ImportPreviewRow[] = []
  // 仅记录 READY 行的编码，SKIP 行不占用后续重复判定
  const readyCodes = new Set<string>()

  parsed.forEach((raw, index) => {
    const line = index + 1
    const record =
      raw !== null && typeof raw === 'object' && !Array.isArray(raw)
        ? (raw as Record<string, unknown>)
        : null
    const displayCode = record ? normalizeImportCode(textOf(record.code)) : ''
    const displayType = record ? textOf(record.type).toUpperCase() : ''
    const displayStem = record ? textOf(record.stem) : ''
    const displayAnswer = record && Array.isArray(record.answer) ? record.answer.map(item => String(item ?? '')).join(',') : ''
    const hasAnalysis = !!record && textOf(record.analysis).length > 0
    const baseRow = {
      line,
      code: displayCode,
      typeLabel: TYPE_LABEL[displayType as ImportQuestionDraft['type']] ?? displayType,
      stemBrief: briefOf(displayStem),
      answerText: displayAnswer,
      hasAnalysis,
    }

    const validation = validateRow(raw, line)
    if (!validation.draft) {
      rows.push({ ...baseRow, status: 'ERROR', message: validation.message })
      return
    }

    const draft = validation.draft
    if (readyCodes.has(draft.code)) {
      rows.push({ ...baseRow, status: 'SKIP', message: '跳过：JSON 内重复编码' })
      return
    }
    if (existingCodes.has(draft.code)) {
      rows.push({ ...baseRow, status: 'SKIP', message: '跳过：题库已存在该编码' })
      return
    }
    readyCodes.add(draft.code)
    rows.push({ ...baseRow, status: 'READY', message: '通过', draft })
  })

  return {
    result: {
      rows,
      readyCount: rows.filter(row => row.status === 'READY').length,
      skipCount: rows.filter(row => row.status === 'SKIP').length,
      errorCount: rows.filter(row => row.status === 'ERROR').length,
    },
    fatalError: '',
  }
}

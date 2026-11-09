/**
 * 考试结果 / 批改页共用的选项与答案解析。
 *
 * 选项 JSON 兼容题库写入的 `[{key,content}]`、快照回显的 `[{key,label}]`、
 * 历史种子 `["A.文本"]` 与纯字符串数组；答案 JSON 兼容结构化 draft
 * （`{kind:'single'|'multiple'}`）与后端 `GradingAdapter` 仍支持的历史对象形态。
 */

export interface ExamOption {
  key: string
  label: string
}

export interface ExamOptionReviewRow extends ExamOption {
  selected: boolean
  correct: boolean
}

type RawOptions = string | Array<{ key: string; label: string }> | null | undefined

export interface OptionReviewInput {
  optionsJson?: RawOptions
  questionType?: string | null
  studentAnswer?: string | null
  standardAnswer?: string | null
}

const QUESTION_TYPE_ALIASES: Record<string, string> = {
  SINGLE: 'SINGLE_CHOICE',
  MULTIPLE: 'MULTIPLE_CHOICE',
  JUDGE: 'TRUE_FALSE',
}

/** 历史对象答案字段顺序，与后端 `GradingAdapter#deriveAnswerConfig` 的兼容口径一致 */
const LEGACY_ANSWER_FIELDS = [
  'selectedKeys',
  'correctAnswers',
  'correctAnswer',
  'answers',
  'correct',
  'selectedKey',
  'answer',
  'value',
  'answerText',
] as const

const KEY_SEPARATORS = /[\n,，;；]+/

export function normalizeQuestionType(type?: string | null): string {
  const raw = String(type ?? '').trim().toUpperCase()
  return raw ? QUESTION_TYPE_ALIASES[raw] ?? raw : ''
}

/** 仅单选 / 多选展示选项明细；判断题答案本就展示「正确 / 错误」 */
export function isOptionQuestionType(type?: string | null): boolean {
  const normalized = normalizeQuestionType(type)
  return normalized === 'SINGLE_CHOICE' || normalized === 'MULTIPLE_CHOICE'
}

/** 选项解析唯一入口：空值 / 非法 JSON / 非数组统一返回空列表 */
export function parseQuestionOptions(raw: RawOptions): ExamOption[] {
  if (!raw) return []
  let list: Array<Record<string, unknown> | string> | null = null
  if (Array.isArray(raw)) {
    list = raw
  } else if (typeof raw === 'string') {
    try {
      list = JSON.parse(raw)
    } catch {
      return []
    }
  }
  if (!Array.isArray(list)) return []

  return list.map((item, index) => {
    const fallbackKey = String.fromCharCode(65 + index)
    if (item && typeof item === 'object') {
      const record = item as Record<string, unknown>
      return {
        key: String(record.key ?? '').trim() || fallbackKey,
        label: String(record.label ?? record.content ?? record.text ?? record.value ?? '').trim(),
      }
    }
    if (typeof item === 'string') {
      const dotIndex = item.indexOf('.')
      if (dotIndex > 0) {
        return { key: item.slice(0, dotIndex).trim().toUpperCase(), label: item.slice(dotIndex + 1).trim() }
      }
      return { key: fallbackKey, label: item.trim() }
    }
    return { key: fallbackKey, label: String(item ?? '').trim() }
  })
}

function collectKeys(value: unknown): string[] {
  if (value === null || value === undefined) return []
  if (Array.isArray(value)) return value.flatMap(item => collectKeys(item))
  if (typeof value === 'object') return []
  return String(value)
    .split(KEY_SEPARATORS)
    .map(item => item.trim().toUpperCase())
    .filter(Boolean)
}

function extractKeys(parsed: unknown, questionType?: string | null): string[] {
  if (Array.isArray(parsed)) return collectKeys(parsed)
  if (!parsed || typeof parsed !== 'object') return collectKeys(parsed)

  const record = parsed as Record<string, unknown>
  const kind = String(record.kind ?? '').trim()
  if (kind) {
    if (kind === 'single') return collectKeys(record.selectedKey)
    if (kind === 'multiple') return collectKeys(record.selectedKeys)
    return []
  }
  if (!isOptionQuestionType(questionType)) return []
  for (const field of LEGACY_ANSWER_FIELDS) {
    const keys = collectKeys(record[field])
    if (keys.length) return keys
  }
  return []
}

/** 答案键解析唯一入口：统一大写、去空、去重 */
export function selectedOptionKeys(raw?: string | null, questionType?: string | null): string[] {
  const text = String(raw ?? '').trim()
  if (!text) return []

  let parsed: unknown = null
  if (text.startsWith('[') || text.startsWith('{')) {
    try {
      parsed = JSON.parse(text)
    } catch {
      parsed = null
    }
  }
  const keys = parsed === null ? collectKeys(text) : extractKeys(parsed, questionType)
  return [...new Set(keys)]
}

/** 装配选项明细行：题型不符或选项缺失时返回空数组，由调用方自隐藏 */
export function buildOptionReviewRows(input: OptionReviewInput): ExamOptionReviewRow[] {
  if (!isOptionQuestionType(input.questionType)) return []

  const options = parseQuestionOptions(input.optionsJson)
  if (!options.length) return []

  const selectedKeys = new Set(selectedOptionKeys(input.studentAnswer, input.questionType))
  const correctKeys = new Set(selectedOptionKeys(input.standardAnswer, input.questionType))
  return options.map(option => {
    const normalizedKey = option.key.trim().toUpperCase()
    return {
      key: option.key,
      label: option.label,
      selected: selectedKeys.has(normalizedKey),
      correct: correctKeys.has(normalizedKey),
    }
  })
}

import Ajv2020 from 'ajv/dist/2020';
import type { ImportParseResult, ImportPreviewRow, ImportQuestionDraft } from '@/types/question-import';
import {
  IMPORT_DIFFICULTIES,
  IMPORT_QUESTION_TYPES,
  QUESTION_CODE_PATTERN,
  QUESTION_IMPORT_FORMAT_VERSION,
  QUESTION_IMPORT_MAX_QUESTIONS,
  QUESTION_IMPORT_SCHEMA,
} from './questionImportSpec';

/** 题型展示名 */
const TYPE_LABEL: Record<ImportQuestionDraft['type'], string> = {
  SINGLE: '单选',
  MULTI: '多选',
  JUDGE: '判断',
};

const ALLOWED_TYPES = new Set<string>(IMPORT_QUESTION_TYPES);
const ALLOWED_DIFFICULTIES = new Set<string>(IMPORT_DIFFICULTIES);
/** 平台编码硬规则：字符集与系统自动码保留形状（与后端 QuestionCodeRules 同源） */
const CODE_PATTERN = /^[A-Z0-9_-]+$/;
const HOMEWORK_AUTO_PATTERN = /^HW\d+$/;
const LAB_AUTO_PATTERN = /^LAB\d+S\d+$/;
/** wrapper 顶层白名单 */
const TOP_LEVEL_FIELDS = new Set(['formatVersion', 'spec', 'schema', 'questions']);
/** 判断题缺省 options */
const JUDGE_DEFAULT_OPTIONS = [
  { key: 'T', content: '正确' },
  { key: 'F', content: '错误' },
];

/** 前端内置 schema 编译结果，只校验归一后的单题对象 */
const validateQuestion = new Ajv2020({ allErrors: true, strict: false }).compile(
  QUESTION_IMPORT_SCHEMA.items,
);

/** 编码规范化：去首尾空格 + 全大写（与后端 QuestionService 保持一致） */
export function normalizeImportCode(code: string): string {
  return code.trim().toUpperCase();
}

interface RowValidation {
  draft?: ImportQuestionDraft;
  /** 校验失败原因；draft 存在时为空串 */
  message: string;
}

type NormalizedImportQuestion = Record<string, unknown> & { code: string; type: string };

/** 取字符串字段的 trim 值，非字符串返回空串 */
function textOf(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

/** 题干摘要：最多 40 字 */
function briefOf(stem: string): string {
  return stem.length > 40 ? `${stem.slice(0, 40)}…` : stem;
}

/**
 * 归一预处理：只做 trim / 大写与缺省补全，非对象、非数组、非字符串一律原样保留，不抛异常。
 * 归一对象是 AJV、手写业务校验与提交 payload 的唯一输入。
 */
function normalizeImportQuestion(raw: unknown): unknown {
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) return raw;
  const record = raw as Record<string, unknown>;
  const normalized: Record<string, unknown> = { ...record };

  for (const key of ['code', 'type', 'difficulty']) {
    if (typeof record[key] === 'string') {
      normalized[key] = (record[key] as string).trim().toUpperCase();
    }
  }
  if (record.difficulty === undefined || record.difficulty === null) normalized.difficulty = 'EASY';
  if (record.analysis === undefined || record.analysis === null) normalized.analysis = '';

  if (Array.isArray(record.options)) {
    normalized.options = record.options.map((item) => {
      if (item === null || typeof item !== 'object' || Array.isArray(item)) return item;
      const option = item as Record<string, unknown>;
      return {
        ...option,
        key: typeof option.key === 'string' ? option.key.trim().toUpperCase() : option.key,
        content: typeof option.content === 'string' ? option.content.trim() : option.content,
      };
    });
  } else if ((record.options === undefined || record.options === null) && normalized.type === 'JUDGE') {
    normalized.options = JUDGE_DEFAULT_OPTIONS;
  }

  if (Array.isArray(record.answer)) {
    normalized.answer = record.answer.map((item) =>
      typeof item === 'string' ? item.trim().toUpperCase() : item,
    );
  }
  return normalized;
}

/** code 硬规则：H1 → H3 → H2 → H4 → H6 */
function validateCodeRules(record: Record<string, unknown>): string {
  const rawCode = textOf(record.code);
  if (!rawCode) return 'code 不能为空';
  const code = normalizeImportCode(rawCode);
  if (code.length > 64) return 'code 长度不能超过 64 个字符';
  if (!CODE_PATTERN.test(code)) return 'code 只能包含 A-Z、0-9、- 和 _';
  if (HOMEWORK_AUTO_PATTERN.test(code) || LAB_AUTO_PATTERN.test(code)) {
    return 'code 不能使用系统自动编码格式（HW{数字} / LAB{数字}S{数字}）';
  }
  if (!QUESTION_CODE_PATTERN.test(code)) {
    return 'code 必须符合 <来源前缀>-<批次/章节>-<序号>（如 LINUX-OS-001）';
  }
  return '';
}

/** AJV 结构校验：白名单与必填给专用文案，其余结构错误给通用文案 */
function validateStructureWithAjv(normalized: unknown, line: number): string {
  if (validateQuestion(normalized)) return '';
  const errors = validateQuestion.errors ?? [];
  const prioritized =
    errors.find((item) => item.keyword === 'additionalProperties') ??
    errors.find((item) => item.keyword === 'required') ??
    errors[0];
  if (!prioritized) return `第 ${line} 行：字段 / 不符合导入规范`;

  if (prioritized.keyword === 'additionalProperties' && prioritized.instancePath === '') {
    const field = String(
      (prioritized.params as Record<string, unknown>).additionalProperty ?? '',
    );
    return `不支持的字段：${field}（题库只接受内容字段）`;
  }
  if (prioritized.keyword === 'required' && prioritized.instancePath === '') {
    const field = String((prioritized.params as Record<string, unknown>).missingProperty ?? '');
    return `缺少必填字段：${field}`;
  }
  return `第 ${line} 行：字段 ${prioritized.instancePath || '/'} 不符合导入规范`;
}

/** 业务语义校验：现网 validateRow 第 3–18 条，输入为归一对象 */
function validateBusinessRow(record: NormalizedImportQuestion): RowValidation {
  const code = record.code;
  const stem = textOf(record.stem);
  if (!stem) return { message: 'stem 不能为空' };

  const type = record.type as ImportQuestionDraft['type'];
  if (!ALLOWED_TYPES.has(type)) return { message: 'type 必须为 SINGLE / MULTI / JUDGE' };

  const difficulty = record.difficulty as ImportQuestionDraft['difficulty'];
  if (!ALLOWED_DIFFICULTIES.has(difficulty)) {
    return { message: 'difficulty 必须为 EASY / MEDIUM / HARD' };
  }

  const rawOptions = record.options;
  if (rawOptions === undefined || rawOptions === null) {
    return { message: '选择题必须提供 options' };
  }
  if (!Array.isArray(rawOptions) || rawOptions.length < 2) {
    return { message: 'options 至少 2 个' };
  }

  const options: Array<{ key: string; content: string }> = [];
  const optionKeys = new Set<string>();
  for (const item of rawOptions) {
    if (item === null || typeof item !== 'object' || Array.isArray(item)) {
      return { message: 'options.content 不能为空' };
    }
    const option = item as Record<string, unknown>;
    const key = textOf(option.key).toUpperCase();
    const content = textOf(option.content);
    if (!key || !content) return { message: 'options.content 不能为空' };
    if (optionKeys.has(key)) return { message: `options.key 重复：${key}` };
    optionKeys.add(key);
    options.push({ key, content });
  }

  if (type === 'JUDGE' && (options.length !== 2 || !optionKeys.has('T') || !optionKeys.has('F'))) {
    return { message: '判断题 options 的 key 必须为 T / F' };
  }

  const rawAnswer = record.answer;
  if (!Array.isArray(rawAnswer) || rawAnswer.length === 0) {
    return { message: 'answer 不能为空' };
  }

  if (type === 'JUDGE') {
    const invalid = rawAnswer.some((item) => {
      const value = textOf(item).toUpperCase();
      return value !== 'T' && value !== 'F';
    });
    if (invalid) return { message: '判断题答案必须为 T / F' };
  }

  const answer: string[] = [];
  for (const item of rawAnswer) {
    const value = textOf(item).toUpperCase();
    if (!optionKeys.has(value)) {
      return { message: `答案 key 不在选项中：${textOf(item) || String(item ?? '')}` };
    }
    answer.push(value);
  }

  if (type === 'SINGLE' && answer.length !== 1) return { message: '单选题答案必须恰好 1 个' };
  if (type === 'MULTI' && answer.length < 1) return { message: '多选题答案至少 1 个' };

  const analysis = record.analysis;
  if (typeof analysis !== 'string') return { message: 'analysis 必须为字符串' };

  return {
    message: '',
    draft: { code, type, stem, difficulty, options, answer, analysis },
  };
}

/**
 * 解析上传的 wrapper JSON 文本并产出预览行。
 * 致命错误时 result 为 null；text 只接收已剥离 BOM 的文本。
 */
export function parseImportDocument(
  text: string,
  existingCodes: Set<string>,
): { result: ImportParseResult | null; fatalError: string } {
  const trimmed = text.trim();
  if (!trimmed) return { result: null, fatalError: '导入文件为空' };

  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    return { result: null, fatalError: `JSON 解析失败：${detail}` };
  }

  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return {
      result: null,
      fatalError: '导入文件必须是包含 formatVersion 与 questions 的 JSON 对象，请下载规范示例',
    };
  }

  const document = parsed as Record<string, unknown>;
  const unexpectedFields = Object.keys(document).filter((key) => !TOP_LEVEL_FIELDS.has(key));
  if (unexpectedFields.length > 0) {
    return {
      result: null,
      fatalError: `导入文件包含不支持的顶层字段：${unexpectedFields[0]}`,
    };
  }
  if (document.formatVersion !== QUESTION_IMPORT_FORMAT_VERSION) {
    return { result: null, fatalError: '导入文件版本不支持，请重新下载模板' };
  }
  if (!Array.isArray(document.questions)) {
    return { result: null, fatalError: 'questions 必须是题目数组' };
  }
  if (document.questions.length === 0) {
    return { result: null, fatalError: '至少需要一道题目' };
  }
  if (document.questions.length > QUESTION_IMPORT_MAX_QUESTIONS) {
    return { result: null, fatalError: '单次导入不能超过 500 道题' };
  }

  const rows: ImportPreviewRow[] = [];
  const readyCodes = new Set<string>();

  document.questions.forEach((raw, index) => {
    const line = index + 1;
    const origin =
      raw !== null && typeof raw === 'object' && !Array.isArray(raw)
        ? (raw as Record<string, unknown>)
        : null;
    const displayCode = origin ? normalizeImportCode(textOf(origin.code)) : '';
    const displayType = origin ? textOf(origin.type).toUpperCase() : '';
    const displayStem = origin ? textOf(origin.stem) : '';
    const displayAnswer =
      origin && Array.isArray(origin.answer)
        ? origin.answer.map((item) => String(item ?? '')).join(',')
        : '';
    const hasAnalysis = !!origin && textOf(origin.analysis).length > 0;
    const baseRow = {
      line,
      code: displayCode,
      typeLabel: TYPE_LABEL[displayType as ImportQuestionDraft['type']] ?? displayType,
      stemBrief: briefOf(displayStem),
      answerText: displayAnswer,
      hasAnalysis,
    };

    if (!origin) {
      rows.push({ ...baseRow, status: 'ERROR', message: `第 ${line} 行：题目必须是对象` });
      return;
    }

    const normalized = normalizeImportQuestion(raw) as NormalizedImportQuestion;
    const codeError = validateCodeRules(normalized);
    if (codeError) {
      rows.push({ ...baseRow, status: 'ERROR', message: codeError });
      return;
    }

    const structureError = validateStructureWithAjv(normalized, line);
    if (structureError) {
      rows.push({ ...baseRow, status: 'ERROR', message: structureError });
      return;
    }

    const validation = validateBusinessRow(normalized);
    if (!validation.draft) {
      rows.push({ ...baseRow, status: 'ERROR', message: validation.message });
      return;
    }

    const draft = validation.draft;
    if (readyCodes.has(draft.code)) {
      rows.push({ ...baseRow, status: 'SKIP', message: '跳过：JSON 内重复编码' });
      return;
    }
    if (existingCodes.has(draft.code)) {
      rows.push({ ...baseRow, status: 'SKIP', message: '跳过：题库已存在该编码' });
      return;
    }
    readyCodes.add(draft.code);
    rows.push({ ...baseRow, status: 'READY', message: '通过', draft });
  });

  // 阻断性错误存在时禁止提交：保留错误行原有文案，把本批次其余待导入行一并收口为 ERROR
  if (rows.some((row) => row.status === 'ERROR')) {
    rows.forEach((row) => {
      if (row.status === 'READY') {
        row.status = 'ERROR';
        row.message = '本批次存在校验错误行，请修正文件后重新上传';
        delete row.draft;
      }
    });
  }

  return {
    result: {
      rows,
      readyCount: rows.filter((row) => row.status === 'READY').length,
      skipCount: rows.filter((row) => row.status === 'SKIP').length,
      errorCount: rows.filter((row) => row.status === 'ERROR').length,
    },
    fatalError: '',
  };
}

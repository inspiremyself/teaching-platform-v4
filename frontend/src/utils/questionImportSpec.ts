import type { ImportQuestionDraft } from '@/types/question-import';

export const QUESTION_IMPORT_FORMAT_VERSION = '1.0';
export const QUESTION_IMPORT_TEMPLATE_FILE_NAME = '题目批量导入模板.json';
export const QUESTION_IMPORT_MAX_FILE_SIZE = 2 * 1024 * 1024;
export const QUESTION_IMPORT_MAX_QUESTIONS = 500;

export const IMPORT_QUESTION_TYPES = ['SINGLE', 'MULTI', 'JUDGE'] as const;
export const IMPORT_DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD'] as const;
export const QUESTION_CODE_PATTERN = /^[A-Z][A-Z0-9]{0,7}-[A-Z0-9]{1,15}-\d{3,6}$/;

export interface ImportFieldDefinition {
  name: string;
  type: string;
  required: boolean;
  defaultValue: string | null;
  description: string;
  jsonSchema: Record<string, unknown>;
}

/** 字段要求、页面字段表与 AJV items.properties 的唯一来源 */
export const IMPORT_FIELD_DEFINITIONS: ImportFieldDefinition[] = [
  {
    name: 'code',
    type: 'string',
    required: true,
    defaultValue: null,
    description: '题目编码，须符合 code 编码要求；在本教师题库内唯一',
    jsonSchema: { type: 'string', pattern: QUESTION_CODE_PATTERN.source },
  },
  {
    name: 'type',
    type: 'string',
    required: true,
    defaultValue: null,
    description: 'SINGLE / MULTI / JUDGE',
    jsonSchema: { enum: IMPORT_QUESTION_TYPES },
  },
  {
    name: 'stem',
    type: 'string',
    required: true,
    defaultValue: null,
    description: '题干，去首尾空格后非空',
    jsonSchema: { type: 'string', minLength: 1 },
  },
  {
    name: 'difficulty',
    type: 'string',
    required: false,
    defaultValue: 'EASY',
    description: 'EASY / MEDIUM / HARD',
    jsonSchema: { enum: IMPORT_DIFFICULTIES },
  },
  {
    name: 'options',
    type: 'array',
    required: false,
    defaultValue: null,
    description: '选择题必填，元素为 { key, content }；判断题可省略，系统补 T / F',
    jsonSchema: {
      type: 'array',
      minItems: 2,
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['key', 'content'],
        properties: {
          key: { type: 'string', minLength: 1 },
          content: { type: 'string', minLength: 1 },
        },
      },
    },
  },
  {
    name: 'answer',
    type: 'array',
    required: true,
    defaultValue: null,
    description: '正确答案 key 数组',
    jsonSchema: { type: 'array', minItems: 1, items: { type: 'string', minLength: 1 } },
  },
  {
    name: 'analysis',
    type: 'string',
    required: false,
    defaultValue: '',
    description: '题目解析，缺解析在预览中标黄但不阻断',
    jsonSchema: { type: 'string' },
  },
];

export const IMPORT_CODE_REQUIREMENTS = {
  pattern: QUESTION_CODE_PATTERN.source,
  hardRules: [
    'H1 去首尾空格并统一大写存储；小写输入按大写判重与保存',
    'H2 仅 A-Z、0-9、-、_',
    'H3 长度不超过 64 个字符',
    'H4 禁止系统自动编码形状 HW{数字} / LAB{数字}S{数字}',
    'H5（判重，非阻断）同一教师本人题库内唯一，重复编码自动跳过',
    'H6 必须符合 <来源前缀>-<批次/章节>-<序号> 三段式',
  ],
  softRules: ['序号位数保持一致便于排序，平台不校验'],
  validExamples: ['LINUX-OS-001', 'LINUX-OS-002', 'T-2026-001', 'Q-FOUND-001'],
  invalidExamples: ['S-001', 'LINUX-OS-1', 'LINUX-OS-ABC', 'LINUX_OS_001', 'HW12', 'LAB3S1', '中文编码'],
  duplicateRule: '同一教师本人题库内唯一，重复编码自动跳过',
};

export const IMPORT_FILE_REQUIREMENTS = [
  { name: '文件后缀', requirement: '.json' },
  { name: '文件编码', requirement: 'UTF-8（无 BOM）' },
  { name: '文件大小', requirement: `不超过 ${QUESTION_IMPORT_MAX_FILE_SIZE / 1024 / 1024} MB` },
  { name: '题目数量', requirement: `1–${QUESTION_IMPORT_MAX_QUESTIONS} 道` },
  { name: '文件数量', requirement: '单文件，重新选择覆盖旧文件' },
];

export const QUESTION_IMPORT_SCHEMA = {
  $schema: 'https://json-schema.org/draft/2020-12/schema',
  type: 'array',
  minItems: 1,
  maxItems: QUESTION_IMPORT_MAX_QUESTIONS,
  items: {
    type: 'object',
    additionalProperties: false,
    required: IMPORT_FIELD_DEFINITIONS.filter((field) => field.required).map((field) => field.name),
    properties: Object.fromEntries(
      IMPORT_FIELD_DEFINITIONS.map((field) => [field.name, field.jsonSchema]),
    ),
  },
};

/** 下载文件里的字段要求只暴露 5 个说明字段，与 PRD §3.2 展示一致 */
const TEMPLATE_FIELD_REQUIREMENTS = IMPORT_FIELD_DEFINITIONS.map((field) => ({
  name: field.name,
  type: field.type,
  required: field.required,
  default: field.defaultValue,
  description: field.description,
}));

export const QUESTION_IMPORT_TEMPLATE = {
  formatVersion: QUESTION_IMPORT_FORMAT_VERSION,
  spec: {
    title: '题库批量导入规范',
    fileEncoding: 'UTF-8（无 BOM）',
    fieldRequirements: TEMPLATE_FIELD_REQUIREMENTS,
    codeRequirements: IMPORT_CODE_REQUIREMENTS,
    importRules: [
      '根节点必须为 wrapper 对象，纯数组文件不被接受',
      '仅接受 7 个白名单字段，未知字段（含 score）所在行报错',
      '任一行校验错误则整批不导入',
      '文件内重复编码与题库已存在编码自动跳过',
      '按行顺序串行写入，服务端 40900 记跳过',
      '提交 payload 由归一后的草稿生成',
    ],
  },
  schema: QUESTION_IMPORT_SCHEMA,
  questions: [
    {
      code: 'LINUX-OS-001',
      type: 'SINGLE',
      stem: 'Linux 中用于显示当前工作目录的命令是？',
      difficulty: 'EASY',
      options: [
        { key: 'A', content: 'pwd' },
        { key: 'B', content: 'cd' },
        { key: 'C', content: 'ls' },
        { key: 'D', content: 'cat' },
      ],
      answer: ['A'],
      analysis: 'pwd 用于打印当前工作目录。',
    },
    {
      code: 'LINUX-OS-002',
      type: 'MULTI',
      stem: '以下哪些属于 Linux 常见文件系统？',
      difficulty: 'MEDIUM',
      options: [
        { key: 'A', content: 'ext4' },
        { key: 'B', content: 'xfs' },
        { key: 'C', content: 'NTFS' },
        { key: 'D', content: 'btrfs' },
      ],
      answer: ['A', 'B', 'D'],
      analysis: 'ext4、xfs、btrfs 为 Linux 常用文件系统。',
    },
    {
      code: 'LINUX-OS-003',
      type: 'JUDGE',
      stem: 'ext4 是 Linux 常见的文件系统之一。',
      difficulty: 'EASY',
      options: [
        { key: 'T', content: '正确' },
        { key: 'F', content: '错误' },
      ],
      answer: ['T'],
      analysis: 'ext4 是 Linux 主流文件系统之一。',
    },
  ],
};

export const stripBom = (text: string): string =>
  text.startsWith('\uFEFF') ? text.slice(1) : text;

export const validateImportFileMeta = (fileName: string, size: number): string => {
  if (!fileName.toLowerCase().endsWith('.json')) return '仅支持 .json 文件';
  if (size > QUESTION_IMPORT_MAX_FILE_SIZE) return '导入文件不能超过 2 MB';
  return '';
};

export const buildFailedItemsDocument = (drafts: ImportQuestionDraft[]): object => ({
  ...QUESTION_IMPORT_TEMPLATE,
  questions: drafts,
});

export const downloadQuestionImportTemplate = (): void => {
  const blob = new Blob([JSON.stringify(QUESTION_IMPORT_TEMPLATE, null, 2)], {
    type: 'application/json;charset=utf-8',
  });
  const objectUrl = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = QUESTION_IMPORT_TEMPLATE_FILE_NAME;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(objectUrl);
};

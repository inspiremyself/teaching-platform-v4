import { describe, expect, it } from 'vitest'
import type { ImportQuestionDraft } from '@/types/question-import'
import { parseImportDocument } from '../questionImport'
import {
  IMPORT_CODE_REQUIREMENTS,
  IMPORT_FIELD_DEFINITIONS,
  QUESTION_CODE_PATTERN,
  QUESTION_IMPORT_SCHEMA,
  QUESTION_IMPORT_TEMPLATE,
  buildFailedItemsDocument,
  stripBom,
  validateImportFileMeta,
} from '../questionImportSpec'

const validRow: ImportQuestionDraft = {
  code: 'LINUX-OS-001',
  type: 'SINGLE',
  stem: '题干内容',
  difficulty: 'EASY',
  options: [
    { key: 'A', content: '选项A' },
    { key: 'B', content: '选项B' },
  ],
  answer: ['A'],
  analysis: '解析说明',
}

describe('规范常量单一事实源', () => {
  it('模板 schema 与 QUESTION_IMPORT_SCHEMA 序列化一致', () => {
    expect(QUESTION_IMPORT_TEMPLATE.schema).toBe(QUESTION_IMPORT_SCHEMA)
    expect(JSON.stringify(QUESTION_IMPORT_TEMPLATE.schema)).toBe(JSON.stringify(QUESTION_IMPORT_SCHEMA))
  })

  it('code 正则在 spec、schema 中同源', () => {
    expect(IMPORT_CODE_REQUIREMENTS.pattern).toBe(QUESTION_CODE_PATTERN.source)
    expect((QUESTION_IMPORT_SCHEMA.items.properties as Record<string, { pattern?: string }>).code.pattern)
      .toBe(QUESTION_CODE_PATTERN.source)
  })

  it('字段定义与模板 fieldRequirements 的 7 字段白名单一致', () => {
    const names = IMPORT_FIELD_DEFINITIONS.map(field => field.name)
    expect(names).toEqual(['code', 'type', 'stem', 'difficulty', 'options', 'answer', 'analysis'])
    expect(QUESTION_IMPORT_TEMPLATE.spec.fieldRequirements.map(field => field.name)).toEqual(names)
    expect(QUESTION_IMPORT_TEMPLATE.spec.fieldRequirements[0]).toEqual({
      name: 'code',
      type: 'string',
      required: true,
      default: null,
      description: IMPORT_FIELD_DEFINITIONS[0].description,
    })
    expect(Object.keys(QUESTION_IMPORT_SCHEMA.items.properties as object)).toEqual(names)
  })
})

describe('文件级纯函数', () => {
  it('BOM 文本可解析', () => {
    const text = '\uFEFF{"formatVersion":"1.0","questions":[]}'

    expect(() => JSON.parse(stripBom(text))).not.toThrow()
    expect(JSON.parse(stripBom(text))).toEqual({ formatVersion: '1.0', questions: [] })
  })

  it('stripBom 幂等', () => {
    expect(stripBom(stripBom('\uFEFF{}'))).toBe('{}')
    expect(stripBom('{}')).toBe('{}')
  })

  it('validateImportFileMeta 后缀与 2 MB 上限', () => {
    expect(validateImportFileMeta('题目批量导入模板.json', 1024)).toBe('')
    expect(validateImportFileMeta('题目.xlsx', 1024)).toBe('仅支持 .json 文件')
    expect(validateImportFileMeta('题目.json', 2 * 1024 * 1024 + 1)).toBe('导入文件不能超过 2 MB')
  })
})

describe('失败项导出', () => {
  it('失败项导出为可再次上传的 wrapper', () => {
    const document = buildFailedItemsDocument([validRow])

    expect(Object.keys(document)).toEqual(['formatVersion', 'spec', 'schema', 'questions'])
    expect(document).toMatchObject({
      formatVersion: '1.0',
      schema: QUESTION_IMPORT_SCHEMA,
      questions: [validRow],
    })

    const reparsed = parseImportDocument(JSON.stringify(document), new Set())
    expect(reparsed.fatalError).toBe('')
    expect(reparsed.result?.readyCount).toBe(1)
  })
})

import { describe, expect, it } from 'vitest'
import { parseImportJson } from '../questionImport'

const validRow = {
  code: 'S-001',
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

describe('parseImportJson 字段白名单', () => {
  it('七个白名单字段可正常导入', () => {
    const { result, fatalError } = parseImportJson(JSON.stringify([validRow]), new Set())

    expect(fatalError).toBe('')
    expect(result?.readyCount).toBe(1)
    expect(result?.rows[0].status).toBe('READY')
  })

  it('含历史 score 字段时该行报错并指出字段名', () => {
    const { result } = parseImportJson(
      JSON.stringify([{ ...validRow, score: 2 }]),
      new Set(),
    )

    expect(result?.errorCount).toBe(1)
    expect(result?.rows[0].status).toBe('ERROR')
    expect(result?.rows[0].message).toContain('score')
  })

  it('含其它未知字段时该行报错并指出字段名', () => {
    const { result } = parseImportJson(
      JSON.stringify([{ ...validRow, referenceScore: 5 }]),
      new Set(),
    )

    expect(result?.errorCount).toBe(1)
    expect(result?.rows[0].message).toContain('referenceScore')
  })
})

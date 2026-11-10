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

describe('parseImportJson 编码规则', () => {
  const parse = (row: Record<string, unknown>, existingCodes: Set<string> = new Set()) =>
    parseImportJson(JSON.stringify([row]), existingCodes)

  it('含中文编码时该行报错', () => {
    const { result } = parse({ ...validRow, code: '中文编码' })

    expect(result?.rows[0].status).toBe('ERROR')
    expect(result?.rows[0].message).toContain('只能包含 A-Z、0-9、- 和 _')
  })

  it('编码含内部空格时该行报错', () => {
    const { result } = parse({ ...validRow, code: 'S 001' })

    expect(result?.rows[0].status).toBe('ERROR')
    expect(result?.rows[0].message).toContain('只能包含 A-Z、0-9、- 和 _')
  })

  it('编码含特殊字符时该行报错', () => {
    const { result } = parse({ ...validRow, code: 'S@001' })

    expect(result?.rows[0].status).toBe('ERROR')
    expect(result?.rows[0].message).toContain('只能包含 A-Z、0-9、- 和 _')
  })

  it('编码超过 64 字符时该行报错', () => {
    const { result } = parse({ ...validRow, code: 'A'.repeat(65) })

    expect(result?.rows[0].status).toBe('ERROR')
    expect(result?.rows[0].message).toContain('长度不能超过 64 个字符')
  })

  it('编码为空时该行报错', () => {
    const { result } = parse({ ...validRow, code: '   ' })

    expect(result?.rows[0].status).toBe('ERROR')
    expect(result?.rows[0].message).toContain('code 不能为空')
  })

  it('编码命中 HW 自动码形状时该行报错', () => {
    const { result } = parse({ ...validRow, code: 'hw12' })

    expect(result?.rows[0].status).toBe('ERROR')
    expect(result?.rows[0].message).toContain('不能使用系统自动编码格式')
  })

  it('编码命中 LAB 自动码形状时该行报错', () => {
    const { result } = parse({ ...validRow, code: 'lab3s1' })

    expect(result?.rows[0].status).toBe('ERROR')
    expect(result?.rows[0].message).toContain('不能使用系统自动编码格式')
  })

  it('系统自动码 Q- 前缀可正常导入', () => {
    const { result } = parse({ ...validRow, code: 'Q-SIN-251110120000ABCD' })

    expect(result?.readyCount).toBe(1)
    expect(result?.rows[0].status).toBe('READY')
  })

  it('大小写规范化判重仍生效', () => {
    const { result } = parse({ ...validRow, code: 's-001' }, new Set(['S-001']))

    expect(result?.rows[0].status).toBe('SKIP')
    expect(result?.rows[0].message).toContain('题库已存在该编码')
  })

  it('错误行与合法行混合时全批禁止提交', () => {
    const { result } = parseImportJson(
      JSON.stringify([{ ...validRow, score: 2 }, validRow]),
      new Set(),
    )

    expect(result?.readyCount).toBe(0)
    expect(result?.rows).toHaveLength(2)
    expect(result?.rows.every(row => row.status === 'ERROR')).toBe(true)
    expect(result?.rows[1].message).toContain('本批次存在校验错误行')
    expect(result?.rows[1].draft).toBeUndefined()
  })
})

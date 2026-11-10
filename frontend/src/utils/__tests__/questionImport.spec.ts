import { describe, expect, it } from 'vitest'
import { parseImportDocument } from '../questionImport'
import { QUESTION_IMPORT_TEMPLATE } from '../questionImportSpec'

const validRow = {
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

const wrap = (rows: unknown[]) => ({ formatVersion: '1.0', questions: rows })
const parse = (rows: unknown[], existingCodes: Set<string> = new Set()) =>
  parseImportDocument(JSON.stringify(wrap(rows)), existingCodes)

describe('parseImportDocument wrapper 结构', () => {
  it('wrapper 文件解析预览通过', () => {
    const { result, fatalError } = parseImportDocument(
      JSON.stringify(QUESTION_IMPORT_TEMPLATE),
      new Set(),
    )

    expect(fatalError).toBe('')
    expect(result?.readyCount).toBe(3)
    expect(result?.rows.every(row => row.status === 'READY')).toBe(true)
  })

  it('纯数组文件报致命错误', () => {
    const { result, fatalError } = parseImportDocument(JSON.stringify([validRow]), new Set())

    expect(result).toBeNull()
    expect(fatalError).toContain('导入文件必须是包含 formatVersion 与 questions 的 JSON 对象')
  })

  it('formatVersion 不匹配报致命错误', () => {
    const { result, fatalError } = parseImportDocument(
      JSON.stringify({ formatVersion: '2.0', questions: [validRow] }),
      new Set(),
    )

    expect(result).toBeNull()
    expect(fatalError).toBe('导入文件版本不支持，请重新下载模板')
  })

  it('顶层未知字段报致命错误', () => {
    const { result, fatalError } = parseImportDocument(
      JSON.stringify({ ...wrap([validRow]), extra: 1 }),
      new Set(),
    )

    expect(result).toBeNull()
    expect(fatalError).toBe('导入文件包含不支持的顶层字段：extra')
  })
})

describe('parseImportDocument 编码规则', () => {
  it.each(['LINUX-OS-001', 'T-2026-001', 'Q-FOUND-001', 'linux-os-001'])(
    'code 三段式合规样例通过：%s',
    code => {
      const { result, fatalError } = parse([{ ...validRow, code }])

      expect(fatalError).toBe('')
      expect(result?.readyCount).toBe(1)
      expect(result?.rows[0].status).toBe('READY')
    },
  )

  it.each([
    ['S-001', 'code 必须符合'],
    ['LINUX-OS-1', 'code 必须符合'],
    ['LINUX-OS-ABC', 'code 必须符合'],
    ['LINUX_OS_001', 'code 必须符合'],
    ['Q-SIN-251110120000ABCD', 'code 必须符合'],
    ['HW12', '不能使用系统自动编码格式'],
    ['LAB3S1', '不能使用系统自动编码格式'],
    ['中文编码', '只能包含 A-Z、0-9、- 和 _'],
    ['S 001', '只能包含 A-Z、0-9、- 和 _'],
    ['S@001', '只能包含 A-Z、0-9、- 和 _'],
    ['A'.repeat(65), '长度不能超过 64 个字符'],
    ['   ', 'code 不能为空'],
  ])('code 不合规矩阵逐行 ERROR：%s', (code, expected) => {
    const { result } = parse([{ ...validRow, code }])

    expect(result?.readyCount).toBe(0)
    expect(result?.rows[0].status).toBe('ERROR')
    expect(result?.rows[0].message).toContain(expected)
  })

  it('小写 code 与小写 type 归一后可导入且提交为大写', () => {
    const { result } = parse([{ ...validRow, code: 'linux-os-001', type: 'single' }])

    expect(result?.rows[0].status).toBe('READY')
    expect(result?.rows[0].draft?.code).toBe('LINUX-OS-001')
    expect(result?.rows[0].draft?.type).toBe('SINGLE')
  })
})

describe('parseImportDocument 字段与文案边界', () => {
  it('含 score 等未知字段的行级 ERROR 且文案含字段名', () => {
    const scoreResult = parse([{ ...validRow, score: 2 }]).result
    expect(scoreResult?.errorCount).toBe(1)
    expect(scoreResult?.rows[0].status).toBe('ERROR')
    expect(scoreResult?.rows[0].message).toContain('score')
    expect(scoreResult?.rows[0].message).toContain('不支持的字段')

    const otherResult = parse([{ ...validRow, referenceScore: 5 }]).result
    expect(otherResult?.rows[0].message).toContain('referenceScore')
  })

  it('结构类错误给通用文案，业务语义错误保留现网专用文案', () => {
    expect(parse([{ ...validRow, type: 'FILL' }]).result?.rows[0].message).toContain('不符合导入规范')
    expect(parse([{ ...validRow, answer: [] }]).result?.rows[0].message).toContain('不符合导入规范')
    expect(parse([{ ...validRow, stem: '' }]).result?.rows[0].message).toContain('不符合导入规范')
    expect(
      parse([{ ...validRow, options: [{ key: 'A', content: 'A' }, { key: 'A', content: 'B' }] }]).result
        ?.rows[0].message,
    ).toContain('options.key 重复')
    expect(parse([{ ...validRow, answer: ['Z'] }]).result?.rows[0].message).toContain('答案 key 不在选项中')
    expect(parse(['不是对象']).result?.rows[0].message).toContain('题目必须是对象')
  })

  it('判断题缺省 options、difficulty、analysis 时按现网补齐', () => {
    const { result } = parse([
      { code: 'LINUX-OS-004', type: 'JUDGE', stem: '判断题题干', answer: ['T'] },
    ])

    expect(result?.readyCount).toBe(1)
    expect(result?.rows[0].draft?.options).toEqual([
      { key: 'T', content: '正确' },
      { key: 'F', content: '错误' },
    ])
    expect(result?.rows[0].draft?.difficulty).toBe('EASY')
    expect(result?.rows[0].draft?.analysis).toBe('')
  })

  it('错误行与合法行混合时整批不可提交', () => {
    const { result } = parse([{ ...validRow, score: 2 }, validRow])

    expect(result?.readyCount).toBe(0)
    expect(result?.rows).toHaveLength(2)
    expect(result?.rows.every(row => row.status === 'ERROR')).toBe(true)
    expect(result?.rows[1].message).toContain('请修正文件后重新上传')
    expect(result?.rows[1].draft).toBeUndefined()
  })

  it('文件内重复编码与题库已存在编码记 SKIP', () => {
    const repeated = parse([validRow, validRow]).result
    expect(repeated?.rows[0].status).toBe('READY')
    expect(repeated?.rows[1].status).toBe('SKIP')
    expect(repeated?.rows[1].message).toContain('JSON 内重复编码')

    const existing = parse([validRow], new Set(['LINUX-OS-001'])).result
    expect(existing?.rows[0].status).toBe('SKIP')
    expect(existing?.rows[0].message).toContain('题库已存在该编码')
  })
})

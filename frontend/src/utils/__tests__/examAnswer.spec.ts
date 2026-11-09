import { describe, expect, it } from 'vitest'
import {
  buildOptionReviewRows,
  isOptionQuestionType,
  normalizeQuestionType,
  parseQuestionOptions,
  selectedOptionKeys,
} from '@/utils/examAnswer'

describe('parseQuestionOptions', () => {
  it('解析 {key,label} 形态', () => {
    expect(parseQuestionOptions('[{"key":"A","label":"选项一"},{"key":"B","label":"选项二"}]')).toEqual([
      { key: 'A', label: '选项一' },
      { key: 'B', label: '选项二' },
    ])
  })

  it('解析题库写入的 {key,content} 形态', () => {
    expect(parseQuestionOptions('[{"key":"A","content":"内容一"}]')).toEqual([{ key: 'A', label: '内容一' }])
  })

  it('解析历史字符串数组 ["A.文本"]', () => {
    expect(parseQuestionOptions('["A.降低复杂度","B.增加偶然性"]')).toEqual([
      { key: 'A', label: '降低复杂度' },
      { key: 'B', label: '增加偶然性' },
    ])
  })

  it('纯文本数组按位置补 A/B 键', () => {
    expect(parseQuestionOptions('["正确","错误"]')).toEqual([
      { key: 'A', label: '正确' },
      { key: 'B', label: '错误' },
    ])
  })

  it('空值与非法 JSON 返回空列表', () => {
    expect(parseQuestionOptions(null)).toEqual([])
    expect(parseQuestionOptions('')).toEqual([])
    expect(parseQuestionOptions('not-json')).toEqual([])
    expect(parseQuestionOptions('{"key":"A"}')).toEqual([])
  })
})

describe('selectedOptionKeys', () => {
  it('解析数组形态并大写归一', () => {
    expect(selectedOptionKeys('["A"]')).toEqual(['A'])
    expect(selectedOptionKeys('["a","A","C"]')).toEqual(['A', 'C'])
  })

  it('解析结构化 draft 形态', () => {
    expect(selectedOptionKeys('{"kind":"single","selectedKey":"a"}')).toEqual(['A'])
    expect(selectedOptionKeys('{"kind":"multiple","selectedKeys":["A","C"]}')).toEqual(['A', 'C'])
    expect(selectedOptionKeys('{"kind":"judge","value":"TRUE"}', 'TRUE_FALSE')).toEqual([])
  })

  it('解析逗号分隔的兼容文本', () => {
    expect(selectedOptionKeys('A,C')).toEqual(['A', 'C'])
    expect(selectedOptionKeys('A，C；B')).toEqual(['A', 'C', 'B'])
  })

  it('解析后端仍兼容的历史对象形态', () => {
    expect(selectedOptionKeys('{"correctAnswer":"A"}', 'SINGLE_CHOICE')).toEqual(['A'])
    expect(selectedOptionKeys('{"correctAnswers":["A","C"]}', 'MULTIPLE_CHOICE')).toEqual(['A', 'C'])
    expect(selectedOptionKeys('{"answers":["A"]}', 'SINGLE_CHOICE')).toEqual(['A'])
  })

  it('空值返回空列表', () => {
    expect(selectedOptionKeys(null)).toEqual([])
    expect(selectedOptionKeys('')).toEqual([])
  })
})

describe('buildOptionReviewRows', () => {
  const optionsJson = '[{"key":"A","label":"选项一"},{"key":"B","label":"选项二"},{"key":"C","label":"选项三"}]'

  it('单选：选中项与正确项落位', () => {
    const rows = buildOptionReviewRows({
      optionsJson,
      questionType: 'SINGLE_CHOICE',
      studentAnswer: '{"kind":"single","selectedKey":"A"}',
      standardAnswer: '["B"]',
    })
    expect(rows.map(row => [row.key, row.selected, row.correct])).toEqual([
      ['A', true, false],
      ['B', false, true],
      ['C', false, false],
    ])
  })

  it('多选：多个选中项与正确项同时标注', () => {
    const rows = buildOptionReviewRows({
      optionsJson,
      questionType: 'MULTIPLE_CHOICE',
      studentAnswer: '{"kind":"multiple","selectedKeys":["A","C"]}',
      standardAnswer: '["A"]',
    })
    expect(rows.filter(row => row.selected).map(row => row.key)).toEqual(['A', 'C'])
    expect(rows.filter(row => row.correct).map(row => row.key)).toEqual(['A'])
  })

  it('判断题与非客观题不生成选项行', () => {
    expect(buildOptionReviewRows({ optionsJson, questionType: 'TRUE_FALSE', studentAnswer: '["A"]' })).toEqual([])
    expect(buildOptionReviewRows({ optionsJson, questionType: 'TEXT', studentAnswer: '["A"]' })).toEqual([])
  })

  it('选项缺失或非法时不生成选项行', () => {
    expect(buildOptionReviewRows({ optionsJson: null, questionType: 'SINGLE_CHOICE' })).toEqual([])
    expect(buildOptionReviewRows({ optionsJson: 'bad-json', questionType: 'SINGLE_CHOICE' })).toEqual([])
    expect(buildOptionReviewRows({ optionsJson: '[]', questionType: 'SINGLE_CHOICE' })).toEqual([])
  })

  it('答案键不在选项内时不产生标记', () => {
    const rows = buildOptionReviewRows({
      optionsJson,
      questionType: 'SINGLE_CHOICE',
      studentAnswer: '["Z"]',
      standardAnswer: '["Y"]',
    })
    expect(rows.some(row => row.selected || row.correct)).toBe(false)
  })
})

describe('题型归一', () => {
  it('历史别名映射到标准题型', () => {
    expect(normalizeQuestionType('SINGLE')).toBe('SINGLE_CHOICE')
    expect(normalizeQuestionType('MULTIPLE')).toBe('MULTIPLE_CHOICE')
    expect(normalizeQuestionType('JUDGE')).toBe('TRUE_FALSE')
  })

  it('仅单选 / 多选视为可展示选项的题型', () => {
    expect(isOptionQuestionType('SINGLE_CHOICE')).toBe(true)
    expect(isOptionQuestionType('MULTIPLE')).toBe(true)
    expect(isOptionQuestionType('TRUE_FALSE')).toBe(false)
    expect(isOptionQuestionType('TEXT')).toBe(false)
  })
})

import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = (relative: string): string =>
  readFileSync(new URL(relative, import.meta.url), 'utf8')

describe('学生端三域进入层级', () => {
  it('实验列表直达作答页，不再经过详情中转页', () => {
    const labs = source('../Labs.vue')
    expect(labs).toContain('/student/labs/${id}/steps')
    expect(labs).not.toContain('student-lab-detail')
  })

  it('考试列表在可参加行提供开考确认弹窗', () => {
    const list = source('../exams/List.vue')
    expect(list).toContain('确认开始考试')
    expect(list).toContain('startExam')
    expect(list).toContain('handleCardAction')
    expect(list).toContain('考试尚未开始')
    expect(list).not.toContain('查看并开始')
    expect(list).not.toContain('进入详情页后可先查看说明，再开始考试。')
  })

  it('作答页承载实验信息且不再存在中转页文件', () => {
    const panel = source('../components/LabStepAnswerPanel.vue')
    expect(panel).toContain('实验信息')
    expect(panel).toContain('下载实验指导')
    expect(panel).toContain('教师总评')
    expect(() => source('../LabDetail.vue')).toThrow()
  })
})

import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = (relative: string): string => readFileSync(new URL(relative, import.meta.url), 'utf8')

const labs = () => source('../Labs.vue')
const homeworks = () => source('../Homeworks.vue')
const exams = () => source('../exams/List.vue')

describe('学生端三域任务列表页 UI 一致性', () => {
  it('页头骨架统一为 workbench-header', () => {
    expect(labs()).toContain('workbench-header')
    expect(labs()).toContain('labs-header-card')
  })

  it('统计胶囊统一为 AppMetaChip', () => {
    for (const page of [labs(), homeworks(), exams()]) {
      expect(page).toContain('AppMetaChip')
    }
    expect(homeworks()).not.toContain('workbench-meta__item')
    expect(exams()).not.toContain('workbench-meta__item')
    expect(labs().match(/<AppMetaChip\b/g) ?? []).toHaveLength(3)
    expect(homeworks().match(/<AppMetaChip\b/g) ?? []).toHaveLength(4)
    expect(exams().match(/<AppMetaChip\b/g) ?? []).toHaveLength(4)
    expect(homeworks()).toContain('tone="accent"')
    expect(exams()).toContain('tone="accent"')
  })

  it('工具条容器统一为 AppToolbarCard', () => {
    for (const page of [labs(), homeworks(), exams()]) {
      expect(page).toContain('AppToolbarCard')
    }
    expect(homeworks()).not.toContain('<section class="student-toolbar-card"')
    expect(exams()).not.toContain('<section class="exams-toolbar-card"')
  })

  it('卡片骨架类下沉且保持页面私有外观', () => {
    for (const page of [labs(), homeworks(), exams()]) {
      expect(page).toContain('app-task-card')
      expect(page).toContain('app-task-meta-grid')
    }
    const layout = source('../../../assets/layout.css')
    expect(layout).toContain('.app-task-card')
    expect(layout).toContain('.app-task-meta-grid')
    expect(layout).not.toMatch(/\.app-task-card\s*\{[^}]*box-sizing/)
    expect(labs()).toContain('grid-template-columns: repeat(4, minmax(0, 1fr))')
    expect(homeworks()).toContain('grid-template-columns: repeat(4, minmax(0, 1fr))')
    expect(exams()).toContain('grid-template-columns: repeat(3, minmax(0, 1fr))')
    for (const page of [labs(), homeworks(), exams()]) {
      expect(page).not.toMatch(/--task-[a-z-]+:\s*(?!var\()/)
    }
    expect(labs()).toContain('padding: 16px 18px')
    expect(homeworks()).toContain('padding: 22px 24px')
    expect(exams()).toContain('padding: 14px 16px')
  })

  it('状态解析走共享语义层且共享表未被改写', () => {
    expect(labs()).not.toContain('statusFilterLabel(taskFilterOf')
    expect(labs()).toContain('resolveStatusPresentation')
    expect(homeworks()).toContain('resolveStatusPresentation')
    expect(exams()).toContain('resolveStatusPresentation')
    const presentation = source('../../../components/ui/statusPresentation.ts')
    expect(presentation).toContain("label: '已保存草稿', tone: 'info'")
  })

  it('主按钮统一为 el-button 且保留考试动作文案', () => {
    expect(exams()).not.toContain('<button class="exam-action"')
    expect(exams()).not.toContain('.exam-action')
    for (const page of [labs(), homeworks(), exams()]) {
      expect(page).toContain('el-button')
    }
    for (const label of ['继续考试', '开始考试', '查看进度', '查看结果', '暂不可用']) {
      expect(exams()).toContain(label)
    }
  })

  it('空态统一为 AppEmptyState', () => {
    expect(homeworks()).not.toContain('<el-empty')
    expect(labs()).toContain('暂无匹配实验任务')
    expect(homeworks()).toContain('暂无匹配作业')
    expect(exams()).toContain('暂无匹配考试')
    for (const page of [labs(), homeworks(), exams()]) {
      expect(page).toContain('AppEmptyState')
    }
  })

  it('加载态统一为 v-loading', () => {
    expect(exams()).not.toContain('el-skeleton')
    expect(exams()).not.toContain('loading-shell')
    for (const page of [labs(), homeworks(), exams()]) {
      expect(page).toContain('v-loading="loading"')
    }
  })

  it('三页均有页面级错误态与重新加载入口', () => {
    const cases: Array<[string, string, string]> = [
      [labs(), '实验任务加载失败', 'fetchData'],
      [homeworks(), '作业任务加载失败', 'fetchData'],
      [exams(), '考试任务加载失败', 'loadExams'],
    ]
    for (const [page, title, loader] of cases) {
      expect(page).toContain('loadError')
      expect(page).toContain('tone="error"')
      expect(page).toContain(title)
      expect(page).toContain(`@click="${loader}"`)
      expect(page).toContain('重新加载')
    }
  })

  it('实验列表移除伪进度并保留右对齐状态提示', () => {
    const page = labs()
    expect(page).not.toContain('progressPercent')
    expect(page).not.toContain('.student-lab-card__progress-row')
    expect(page).not.toContain('.progress-track')
    expect(page).not.toContain('.progress-fill')
    expect(page).toContain('progressText')
    expect(page).toContain('lab-progress-text')
    expect(page).toMatch(/\.lab-progress-text\s*\{[^}]*margin-left:\s*auto/)
    expect(page).toMatch(/\.lab-progress-text\s*\{[^}]*text-align:\s*right/)
  })

  it('教师端考试主操作文案与新/创建口径一致', () => {
    const panel = source('../../teacher/components/ExamListPanel.vue')
    expect(panel).toContain('<ActionButton label="新建考试" tone="primary"')
  })
})

import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = (relative: string): string =>
  readFileSync(new URL(relative, import.meta.url), 'utf8')

describe('workbench shell adoption', () => {
  const shellPages = [
    '../ExamListPanel.vue',
    '../HomeworkListPanel.vue',
    '../LabListPanel.vue',
    '../../LabSubmissionOverview.vue',
  ]
  const gradingPages = [
    '../../exams/Results.vue',
    '../../HomeworkSubmissions.vue',
    '../../LabReportDetail.vue',
    '../../LabReportBatchGrade.vue',
    '../../LabGradingWorkspace.vue',
  ]

  it.each(shellPages)('%s uses WorkbenchShell', path => {
    expect(source(path)).toContain('WorkbenchShell')
  })

  it.each(gradingPages)('%s uses GradingWorkspace', path => {
    expect(source(path)).toContain('GradingWorkspace')
  })

  it.each(shellPages.slice(0, 3))('%s uses TaskEditorDialog', path => {
    expect(source(path)).toContain('TaskEditorDialog')
  })

  it('removes card-row and quick-grade markup from the unified pages', () => {
    expect(source('../HomeworkListPanel.vue')).not.toContain('class="homework-row"')
    expect(source('../LabListPanel.vue')).not.toContain('class="lab-row"')
    expect(source('../../HomeworkSubmissions.vue')).not.toContain('class="quick-grade-card"')
  })

  it('keeps the exam draft port and grade payload chain', () => {
    const results = source('../../exams/Results.vue')
    expect(results).toContain('examDraftPort')
    expect(results).toContain('buildGradePayload')
  })

  it('does not pull workbench.css into global.css', () => {
    const global = readFileSync(new URL('../../../../assets/global.css', import.meta.url), 'utf8')
    expect(global).not.toContain('workbench.css')
  })
})

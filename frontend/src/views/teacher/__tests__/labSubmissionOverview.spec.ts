import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const overviewSource = readFileSync(
  new URL('../LabSubmissionOverview.vue', import.meta.url),
  'utf-8',
);

describe('实验班级总览（矩阵页为三栏结果页的下级视图）', () => {
  it('学生行保留批改报告与实验报告两个入口，批改报告带 submissionId 回三栏', () => {
    expect(overviewSource).toContain('批改报告');
    expect(overviewSource).toContain('实验报告');
    expect(overviewSource).toContain('query: { submissionId: student.submissionId }');
    expect(overviewSource).toContain("name: 'teacher-lab-submission-overview'");
    expect(overviewSource).toContain("name: 'teacher-lab-report-view'");
    expect(overviewSource).toContain('window.open');
    expect(overviewSource).not.toContain('goReportDetail');
    expect(overviewSource).not.toContain("'/teacher/lab-reports/'");
  });

  it('无提交学生不显示批改入口', () => {
    expect(overviewSource).toContain('student.submissionId != null');
    expect(overviewSource).toContain('student.submissionId == null');
  });

  it('返回文案为返回批改工作区且改为命名路由跳转', () => {
    expect(overviewSource).toContain('overview-matrix__action-head');
    expect(overviewSource).toContain('批改操作');
    expect(overviewSource).toContain('返回批改工作区');
    expect(overviewSource).toContain('goBackToGrading');
    expect(overviewSource).not.toContain('返回实验台账');
    expect(overviewSource).not.toContain('router.back()');
  });
});

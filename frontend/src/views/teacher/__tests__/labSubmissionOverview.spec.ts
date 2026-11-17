import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const overviewSource = readFileSync(
  new URL('../LabSubmissionOverview.vue', import.meta.url),
  'utf-8',
);

describe('实验结果页（报告批改入口下沉）', () => {
  it('学生行提供批改报告与实验报告两个入口', () => {
    expect(overviewSource).toContain('批改报告');
    expect(overviewSource).toContain('实验报告');
    expect(overviewSource).toContain('goReportDetail');
    expect(overviewSource).toContain("name: 'teacher-lab-report-view'");
    expect(overviewSource).toContain('window.open');
  });

  it('无提交学生不显示批改入口', () => {
    expect(overviewSource).toContain('student.submissionId != null');
    expect(overviewSource).toContain('student.submissionId == null');
  });

  it('新增批改操作列且返回文案为返回实验台账', () => {
    expect(overviewSource).toContain('overview-matrix__action-head');
    expect(overviewSource).toContain('批改操作');
    expect(overviewSource).toContain('返回实验台账');
    expect(overviewSource).not.toContain('返回批改队列');
  });
});

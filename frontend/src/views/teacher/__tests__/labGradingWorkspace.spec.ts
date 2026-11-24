import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const source = readFileSync(
  new URL('../LabGradingWorkspace.vue', import.meta.url),
  'utf-8',
);

describe('实验结果三栏工作区', () => {
  it('使用共享三栏骨架、提交队列与 Facade', () => {
    expect(source).toContain('GradingWorkspace');
    expect(source).toContain('SubmissionQueue');
    expect(source).toContain('useGradingWorkspace');
    expect(source).toContain('createSharedDraftPort');
  });

  it('队列来自全量报告队列并按当前实验 labId 过滤', () => {
    expect(source).toContain('listTeacherLabReportQueue');
    expect(source).toContain('row.labId === labId.value');
  });

  it('学生 ID 与实验标题取详情而非队列 VM', () => {
    expect(source).toContain('detail.value?.studentId');
    expect(source).toContain('detail.value.labTitle');
    expect(source).not.toContain('QueueItemVM.studentId');
  });

  it('提供班级总览入口并支持 submissionId 首屏选中', () => {
    expect(source).toContain("name: 'teacher-lab-matrix'");
    expect(source).toContain('route.query.submissionId');
    expect(source).toContain('workspace.selectedId.value = initialSelectedId()');
  });

  it('空态有明确文案且行为摘要按 number 类型收窄', () => {
    expect(source).toContain('本实验暂无提交，可到班级总览查看未提交学生');
    expect(source).toContain('workspace.queue.value.length === 0');
    expect(source).toContain("typeof workspace.selectedId.value === 'number'");
  });
});

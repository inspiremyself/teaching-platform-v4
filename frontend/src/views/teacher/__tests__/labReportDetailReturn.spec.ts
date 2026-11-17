import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const detailSource = readFileSync(
  new URL('../LabReportDetail.vue', import.meta.url),
  'utf-8',
);

describe('评分台（打回能力迁移）', () => {
  it('页头提供打回按钮并调用现网打回 API', () => {
    expect(detailSource).toContain('打回');
    expect(detailSource).toContain('returnTeacherLabReport');
    expect(detailSource).toContain('canReturn');
    expect(detailSource).toContain('returning');
  });

  it('打回仅限已提交与已批改状态', () => {
    expect(detailSource).toContain('SUBMISSION_STATUS.SUBMITTED');
    expect(detailSource).toContain('SUBMISSION_STATUS.GRADED');
  });

  it('复用现网确认文案与打回成功提示', () => {
    expect(detailSource).toContain('成绩将清除');
    expect(detailSource).toContain('可重新编辑提交');
    expect(detailSource).toContain('打回确认');
    expect(detailSource).toContain('已打回，学生可重新编辑提交');
  });

  it('返回按钮文案改为返回，不再称返回批改队列', () => {
    expect(detailSource).toContain('>返回</el-button>');
    expect(detailSource).not.toContain('返回批改队列');
  });
});

import { readFileSync } from 'node:fs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const panelSource = readFileSync(
  new URL('../components/ParticipationPanel.vue', import.meta.url),
  'utf-8',
);

const api = vi.hoisted(() => ({
  getTeacherParticipation: vi.fn(),
}));

vi.mock('@/api/teacher', () => api);

import { useTeacherParticipation } from '@/composables/useTeacherParticipation';
import type { TeacherParticipationDetail } from '@/types/analysis';

const detail: TeacherParticipationDetail = {
  businessType: 'LAB',
  businessId: 910001,
  businessTitle: '参与口径实验',
  classId: 1,
  className: 'SE2026-1',
  expectedCount: 2,
  participatedCount: 1,
  completedCount: 1,
  notStartedCount: 1,
  participationRate: 50,
  completionRate: 50,
  students: [
    {
      studentId: 11,
      studentNo: '20260011',
      studentName: '学生甲',
      participation: 'NOT_STARTED',
      completion: 'IN_PROGRESS',
      submissionId: null,
      submitStatus: null,
      submittedAt: null,
    },
  ],
};

describe('教师参与面板', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('模板保留固定文案与指标字段', () => {
    expect(panelSource).toContain('查看名单');
    expect(panelSource).toContain('未开始');
    expect(panelSource).toContain('已参与');
    expect(panelSource).toContain('暂无活动数据');
    expect(panelSource).toContain('participationRate');
    expect(panelSource).toContain('completionRate');
  });

  it('openDetail 成功后写入详情并打开弹层', async () => {
    api.getTeacherParticipation.mockResolvedValue(detail);
    const state = useTeacherParticipation();

    await state.openDetail('LAB', 910001);

    expect(api.getTeacherParticipation).toHaveBeenCalledWith('LAB', 910001);
    expect(state.detail.value).toEqual(detail);
    expect(state.dialogVisible.value).toBe(true);
    expect(state.loading.value).toBe(false);
  });

  it('失败时只写错误文案，不弹全局提示', async () => {
    api.getTeacherParticipation.mockRejectedValue(new Error('名单加载失败'));
    const state = useTeacherParticipation();

    await state.openDetail('EXAM', 910002);

    expect(state.error.value).toBe('名单加载失败');
    expect(state.detail.value).toBeNull();
    expect(state.loading.value).toBe(false);
  });

  it('closeDetail 清理详情、错误与弹层状态', async () => {
    api.getTeacherParticipation.mockResolvedValue(detail);
    const state = useTeacherParticipation();
    await state.openDetail('LAB', 910001);

    state.closeDetail();

    expect(state.dialogVisible.value).toBe(false);
    expect(state.detail.value).toBeNull();
    expect(state.error.value).toBe('');
    expect(state.loading.value).toBe(false);
  });

  it('连续打开时过期响应不覆盖最新结果', async () => {
    const pending: Array<(value: TeacherParticipationDetail) => void> = [];
    api.getTeacherParticipation.mockImplementation(
      () => new Promise<TeacherParticipationDetail>((resolve) => pending.push(resolve)),
    );
    const state = useTeacherParticipation();
    const first = state.openDetail('LAB', 1);
    const second = state.openDetail('EXAM', 2);
    const newer: TeacherParticipationDetail = { ...detail, businessType: 'EXAM', businessId: 2 };

    pending[1](newer);
    pending[0](detail);
    await Promise.all([first, second]);

    expect(state.detail.value).toEqual(newer);
    expect(api.getTeacherParticipation).toHaveBeenCalledTimes(2);
  });
});

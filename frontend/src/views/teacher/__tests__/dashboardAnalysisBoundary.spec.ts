import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), 'utf-8');

const navigationSource = read('../../../layout/navigation.ts');
const routerSource = read('../../../router/index.ts');
const dashboardSource = read('../components/DashboardOverviewPanel.vue');
const analysisSource = read('../Analysis.vue');
const composableSource = read('../../../composables/useDashboard.ts');
const typesSource = read('../../../types/analysis.ts');

describe('教师总览与分析报表边界', () => {
  it('双入口与路由保持不变', () => {
    expect(navigationSource).toContain("{ to: '/teacher/dashboard', label: '总览'");
    expect(navigationSource).toContain("{ to: '/teacher/analysis', label: '分析报表'");
    expect(routerSource).toContain("path: 'dashboard', name: 'teacher-dashboard'");
    expect(routerSource).toContain("path: 'analysis', component: () => import('@/views/teacher/Analysis.vue')");
  });

  it('总览趋势区只保留摘要与下钻入口', () => {
    expect(dashboardSource).not.toContain('trend-chart__line');
    expect(dashboardSource).not.toContain('trend-bar-item');
    expect(dashboardSource).toContain('查看分析');
    expect(dashboardSource).toContain('/teacher/analysis');
  });

  it('分析页不再重复汇总卡且保留完整趋势图', () => {
    expect(analysisSource).not.toContain('analysis-meta');
    expect(analysisSource).toContain('trend-chart__line');
    expect(analysisSource).toContain('口径说明：汇总卡、班级对比与类型分布为全时段');
  });

  it('composable 提供四条口径说明且不再导出图表 computed', () => {
    expect(composableSource).toContain('我创建的全时段活动（含草稿）');
    expect(composableSource).toContain('已提交待批改，不含草稿');
    expect(composableSource).toContain('任教班级全时段评分均值');
    expect(composableSource).toContain('已发布/关闭活动，尚无提交行');
    expect(composableSource).not.toContain('trendChartPoints');
    expect(composableSource).not.toContain('trendAxisTicks');
  });

  it('分析契约类型不再声明 dashboard 专属字段', () => {
    const start = typesSource.indexOf('export interface TeacherAnalysisOverview');
    const end = typesSource.indexOf('export interface TeacherDashboardData');
    expect(start).toBeGreaterThan(-1);
    expect(end).toBeGreaterThan(start);
    const overviewBlock = typesSource.slice(start, end);
    expect(overviewBlock).not.toContain('recentTasks');
    expect(overviewBlock).not.toContain('quickLinks');
    expect(typesSource).toContain('export interface TeacherDashboardData');
    expect(typesSource).toContain('recentTasks');
  });
});

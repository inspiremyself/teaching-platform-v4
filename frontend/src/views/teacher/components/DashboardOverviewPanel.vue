<template>
  <div class="page dashboard-page workbench-page">
    <section class="workbench-header dashboard-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <h1>教师工作台</h1>
          <p>{{ workbenchHeadline }}</p>
        </div>
        <div class="workbench-header__actions">
          <el-button type="primary" @click="loadDashboard" :loading="loading">刷新工作台</el-button>
        </div>
      </div>

      <div class="workbench-meta dashboard-meta">
        <span class="workbench-meta__item"><strong>{{ recentTasks.length }}</strong> 待办</span>
        <span class="workbench-meta__item"><strong>{{ urgentTaskCount }}</strong> 优先</span>
        <span class="workbench-meta__item"><strong>{{ distinctActorsCount }}</strong> 对象</span>
        <span class="workbench-meta__item"><strong>{{ trendTotalCount }}</strong> 近 7 日评分</span>
        <span class="workbench-meta__item workbench-meta__item--accent"><strong>{{ riskAlertCount }}</strong> 风险</span>
        <span class="workbench-meta__item">{{ taskPressureLevel }}</span>
        <span class="workbench-meta__item">{{ heroStatusText }}</span>
      </div>
    </section>

    <el-skeleton v-if="loading && !loaded" :rows="8" animated />

    <template v-else>
      <div v-if="loadError" class="state-card">
        <AppEmptyState tone="error" title="教师工作台加载失败" :description="loadError">
          <template #actions>
            <el-button type="primary" @click="loadDashboard">重新加载</el-button>
          </template>
        </AppEmptyState>
      </div>

      <div v-else class="dashboard-grid dashboard-grid--refactored">
        <section class="dashboard-main dashboard-main--refactored">
          <section class="workbench-panel dashboard-panel dashboard-panel--summary">
            <div class="workbench-panel__header">
              <div>
                <h2>关键指标</h2>
                <p>用更紧凑的指标排布快速判断当前工作量、风险与近期评分节奏。</p>
              </div>
              <span class="dashboard-inline-note">{{ taskPressureText }}</span>
            </div>
            <div class="dashboard-summary-strip">
              <article v-for="(card, index) in localizedSummaryCards" :key="card.key" class="dashboard-kpi-row">
                <div class="dashboard-kpi-row__icon">
                  <span class="material-symbols-outlined app-icon--sm">{{ summaryIcons[index] }}</span>
                </div>
                <div class="dashboard-kpi-row__body">
                  <div class="dashboard-kpi-row__topline">
                    <span>{{ card.title }}</span>
                    <AppMetaChip :label="summaryCardPills[index]" />
                  </div>
                  <strong>{{ card.value }}</strong>
                  <small>{{ localizedSummarySignal(card.key) }}</small>
                </div>
              </article>
            </div>
          </section>

          <el-card class="dashboard-card dashboard-card--trend" shadow="never">
            <template #header>
              <div class="dashboard-card__header dashboard-card__header--stacked">
                <div>
                  <span class="section-title">近期评分趋势</span>
                  <p>聚焦最近 7 天评分节奏与高峰日。</p>
                </div>
                <div class="dashboard-card__metrics">
                  <div class="dashboard-card__metric">
                    <span>峰值</span>
                    <strong>{{ peakTrendValue }}</strong>
                  </div>
                  <div class="dashboard-card__metric">
                    <span>日均</span>
                    <strong>{{ trendAverageText }}</strong>
                  </div>
                  <div class="dashboard-card__metric dashboard-card__metric--accent">
                    <span>节奏</span>
                    <strong>{{ trendPulseShortText }}</strong>
                  </div>
                </div>
              </div>
              <el-button link type="primary" @click="router.push('/teacher/analysis')">查看分析</el-button>
            </template>

            <div v-if="trend.length" class="trend-summary-grid trend-summary-grid--compact">
              <article class="trend-summary-card">
                <span class="trend-summary-card__label">高峰日</span>
                <strong>{{ peakTrendDate }}</strong>
                <p>{{ peakTrendHint }}</p>
              </article>
              <article class="trend-summary-card">
                <span class="trend-summary-card__label">近期关注</span>
                <strong>{{ trendFocusTitle }}</strong>
                <p>{{ trendFocusDescription }}</p>
              </article>
            </div>
            <AppEmptyState v-else description="暂无趋势数据" />
          </el-card>
        </section>

        <aside class="dashboard-side dashboard-side--refactored">
          <el-card class="dashboard-card" shadow="never">
            <template #header>
              <div class="dashboard-card__header">
                <div>
                  <span class="section-title">待办与提醒</span>
                  <p>直接列出要处理的任务，不做额外大摘要。</p>
                </div>
                <el-tag :type="recentTasks.length ? 'danger' : 'success'" effect="plain">{{ recentTasks.length }} 项</el-tag>
              </div>
            </template>

            <div class="task-panel task-panel--compact">
              <div class="task-overview-strip task-overview-strip--compact">
                <article class="task-overview-item">
                  <span class="task-overview-item__label">优先</span>
                  <strong>{{ urgentTaskCount }}</strong>
                </article>
                <article class="task-overview-item">
                  <span class="task-overview-item__label">对象</span>
                  <strong>{{ distinctActorsCount }}</strong>
                </article>
                <article class="task-overview-item">
                  <span class="task-overview-item__label">节奏</span>
                  <strong>{{ taskPressureLevel }}</strong>
                </article>
              </div>

              <div v-if="recentTasks.length" class="task-list">
                <div v-for="(task, index) in recentTasks" :key="`${task.label}-${task.title}-${index}`" class="task-item">
                  <div class="task-item__badge"></div>
                  <div class="task-item__content">
                    <div class="task-item__header">
                      <strong>{{ localizedTaskLabel(task.label) }}</strong>
                      <span class="task-item__index">{{ padIndex(index + 1) }}</span>
                    </div>
                    <p class="task-item__title">{{ localizedTaskTitle(task.title) }}</p>
                    <div class="task-item__meta-row">
                      <span>{{ localizedActorText(task.actor) }}</span>
                      <button type="button" class="task-item__link" @click="router.push(task.route)">处理</button>
                    </div>
                  </div>
                </div>
              </div>
              <AppEmptyState v-else description="当前没有待处理任务" />
            </div>
          </el-card>

          <el-card class="dashboard-card" shadow="never">
            <template #header>
              <div class="dashboard-card__header">
                <div>
                  <span class="section-title">快捷入口</span>
                  <p>保留真实跳转路径，压成高密度入口列表。</p>
                </div>
                <el-tag effect="plain">{{ quickLinks.length }} 个入口</el-tag>
              </div>
            </template>

            <div v-if="quickLinks.length" class="quick-link-grid quick-link-grid--single">
              <button v-for="link in quickLinks" :key="link.route" type="button" class="quick-link-card quick-link-card--compact" @click="router.push(link.route)">
                <div class="quick-link-card__topline">
                  <span class="quick-link-card__title">{{ localizedQuickLinkLabel(link.label) }}</span>
                  <span class="quick-link-card__badge">{{ link.badge }}</span>
                </div>
                <div class="quick-link-card__footer">
                  <span>{{ quickLinkDescription(link) }}</span>
                  <span class="material-symbols-outlined">arrow_forward</span>
                </div>
              </button>
            </div>
            <AppEmptyState v-else description="暂无可用快捷入口" />
          </el-card>

          <el-card class="dashboard-card" shadow="never">
            <template #header>
              <div class="dashboard-card__header">
                <div>
                  <span class="section-title">风险与建议</span>
                  <p>只保留会影响下一步动作的结论。</p>
                </div>
              </div>
            </template>

            <div class="guidance-list guidance-list--compact">
              <article class="guidance-item">
                <span class="guidance-item__title">风险提醒</span>
                <strong>{{ riskAlertSummary }}</strong>
                <p>{{ riskAlertDetail }}</p>
              </article>
              <article class="guidance-item">
                <span class="guidance-item__title">推荐动作</span>
                <strong>{{ recommendedActionTitle }}</strong>
                <p>{{ recommendedActionDescription }}</p>
              </article>
              <article class="guidance-item guidance-item--warn">
                <span class="guidance-item__title">处置建议</span>
                <strong>{{ riskActionTitle }}</strong>
                <p>{{ riskActionDescription }}</p>
              </article>
            </div>
          </el-card>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { inject } from 'vue';
import AppEmptyState from '@/components/ui/AppEmptyState.vue';
import { DashboardKey } from '@/composables/useDashboard';
import AppMetaChip from '@/components/ui/AppMetaChip.vue';
const { router, loading, loaded, loadError, trend, recentTasks, quickLinks, summaryIcons, summaryCardPills, localizedSummaryCards, trendTotalCount, trendAverageText, trendPulseShortText, peakTrendValue, peakTrendDate, peakTrendHint, urgentTaskCount, distinctActorsCount, riskAlertCount, taskPressureLevel, taskPressureText, workbenchHeadline, heroStatusText, trendFocusTitle, trendFocusDescription, riskAlertSummary, riskAlertDetail, recommendedActionTitle, recommendedActionDescription, riskActionTitle, riskActionDescription, localizedSummarySignal, loadDashboard, padIndex, quickLinkDescription, localizedQuickLinkLabel, localizedTaskLabel, localizedTaskTitle, localizedActorText } = inject(DashboardKey)!;
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.dashboard-grid--refactored {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(340px, 0.95fr);
  gap: 14px;
}

.dashboard-main--refactored,
.dashboard-side--refactored {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.dashboard-panel {
  border: 1px solid var(--color-border-soft);
  border-radius: 16px;
  background: var(--color-surface);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.dashboard-inline-note {
  max-width: 20rem;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-body);
  text-align: right;
}

.dashboard-summary-strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 14px 16px 16px;
}

.dashboard-kpi-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-bg-app);
}

.dashboard-kpi-row__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: var(--color-border);
  color: var(--color-brand-teacher);
  flex-shrink: 0;
}

.dashboard-kpi-row__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.dashboard-kpi-row__topline {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.dashboard-kpi-row__body strong {
  margin-top: 6px;
  color: var(--color-text-primary);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
}

.dashboard-kpi-row__body small {
  margin-top: 4px;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.dashboard-card {
  border: 1px solid var(--color-border);
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.dashboard-card :deep(.el-card__header) {
  border-bottom: 1px solid var(--color-border);
  padding: 14px 16px;
}

.dashboard-card :deep(.el-card__body) {
  padding: 14px 16px 16px;
}

.dashboard-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.dashboard-card__header--stacked {
  align-items: flex-start;
}

.dashboard-card__header p {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-body);
}

.dashboard-card__metrics {
  display: flex;
  gap: 10px;
}

.dashboard-card__metric {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  min-width: 82px;
  padding: 9px 11px;
  border-radius: 16px;
  background: var(--color-bg-app);
}

.dashboard-card__metric--accent {
  background: linear-gradient(180deg, #ecfeff, #ccfbf1);
}

.dashboard-card__metric span {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.dashboard-card__metric strong {
  margin-top: 4px;
  font-size: var(--font-size-2xl);
  color: var(--color-text-primary);
}

.section-title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-text-primary);
}

.trend-summary-grid {
  display: grid;
  gap: 12px;
}

.trend-summary-grid--compact {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.trend-summary-card {
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, var(--color-bg-app), var(--color-surface));
  border: 1px solid var(--color-border);
}

.trend-summary-card__label {
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: var(--letter-spacing-wide);
  color: var(--color-text-muted);
}

.trend-summary-card strong {
  display: block;
  margin-top: 8px;
  font-size: var(--font-size-xl);
  color: var(--color-text-primary);
}

.trend-summary-card p {
  margin: 6px 0 0;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-caption);
  color: var(--color-text-muted);
}

.quick-link-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.quick-link-grid--single {
  grid-template-columns: 1fr;
}

.quick-link-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 112px;
  border: 1px solid var(--color-border);
  background: linear-gradient(135deg, var(--color-surface), var(--color-bg-app));
  border-radius: 16px;
  padding: 14px;
  text-align: left;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.quick-link-card--compact {
  min-height: 88px;
}

.quick-link-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 30px rgba(15, 23, 42, 0.08);
}

.quick-link-card__topline {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.quick-link-card__title {
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--color-text-primary);
}

.quick-link-card__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.12);
  color: var(--color-brand-teacher);
  font-size: var(--font-size-base);
  font-weight: 700;
}

.quick-link-card p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-body);
}

.quick-link-card__footer {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--color-info);
  font-size: var(--font-size-sm);
  font-weight: 700;
  gap: 12px;
}

.task-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-panel--compact {
  gap: 10px;
}

.task-overview-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.task-overview-strip--compact {
  gap: 8px;
}

.task-overview-item {
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--color-bg-app);
  border: 1px solid var(--color-border);
}

.task-overview-item__label {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.task-overview-item strong {
  display: block;
  margin-top: 4px;
  font-size: var(--font-size-lg);
  color: var(--color-text-primary);
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.task-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  background: var(--color-bg-app);
  border: 1px solid var(--color-border);
}

.task-item__badge {
  width: 10px;
  border-radius: 999px;
  background: linear-gradient(180deg, var(--color-danger), #f97316);
  flex-shrink: 0;
}

.task-item__content {
  min-width: 0;
  flex: 1;
}

.task-item__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.task-item__header strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-base);
}

.task-item__index {
  font-size: var(--font-size-xs);
  font-weight: 700;
  color: var(--color-text-placeholder);
}

.task-item__title {
  margin: 6px 0 0;
  color: #334155;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-caption);
}

.task-item__meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.task-item__link {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-info);
  font-size: var(--font-size-sm);
  font-weight: 700;
  cursor: pointer;
}

.guidance-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.guidance-list--compact {
  gap: 10px;
}

.guidance-item {
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-app);
}

.guidance-item__title {
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: var(--letter-spacing-wide);
  color: var(--color-text-muted);
}

.guidance-item strong {
  display: block;
  margin-top: 6px;
  font-size: var(--font-size-lg);
  line-height: var(--line-height-heading);
  color: var(--color-text-primary);
}

.guidance-item p {
  margin: 6px 0 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-caption);
}

.guidance-item--warn {
  background: linear-gradient(180deg, var(--color-warning-soft), #fffbeb);
}

.dashboard-side--refactored {
  align-self: start;
}

@media (max-width: 1280px) {
  .dashboard-summary-strip,
  .trend-summary-grid--compact,
  .dashboard-grid--refactored {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .dashboard-inline-note {
    max-width: none;
    text-align: left;
  }

  .dashboard-card__header,
  .dashboard-card__header--stacked {
    flex-direction: column;
    align-items: flex-start;
  }

  .dashboard-card__metrics,
  .quick-link-grid,
  .trend-summary-grid,
  .task-overview-strip,
  .dashboard-summary-strip {
    grid-template-columns: 1fr;
  }

  .dashboard-card__metrics {
    width: 100%;
    display: grid;
  }

}

@media (max-width: 768px) {
  .quick-link-grid,
  .trend-summary-grid,
  .task-overview-strip,
  .dashboard-summary-strip {
    grid-template-columns: 1fr;
  }

  .dashboard-card :deep(.el-card__header),
  .dashboard-card :deep(.el-card__body) {
    padding-left: 18px;
    padding-right: 18px;
  }
}
</style>

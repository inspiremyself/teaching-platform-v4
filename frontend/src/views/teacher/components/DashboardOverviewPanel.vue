<template>
  <div class="page dashboard-page workbench-page">
    <section class="workbench-header dashboard-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="dashboard-header__eyebrow">教师控制台 / 工作台总览</div>
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
        <el-result icon="error" title="教师工作台加载失败" :sub-title="loadError">
          <template #extra>
            <el-button type="primary" @click="loadDashboard">重新加载</el-button>
          </template>
        </el-result>
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
                  <el-icon :size="18"><component :is="summaryIcons[index]" /></el-icon>
                </div>
                <div class="dashboard-kpi-row__body">
                  <div class="dashboard-kpi-row__topline">
                    <span>{{ card.title }}</span>
                    <span class="workbench-chip">{{ summaryCardPills[index] }}</span>
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
            </template>

            <div v-if="trend.length" class="trend-panel">
              <div class="trend-chart-shell">
                <div class="trend-y-axis">
                  <span v-for="tick in trendAxisTicks" :key="tick">{{ tick }}</span>
                </div>

                <div class="trend-chart">
                  <div class="trend-chart__grid">
                    <span v-for="tick in trendAxisTicks" :key="`grid-${tick}`" class="trend-chart__grid-line"></span>
                  </div>

                  <svg
                    v-if="trendChartPoints.length"
                    class="trend-chart__line"
                    viewBox="0 0 100 100"
                    preserveAspectRatio="none"
                    aria-hidden="true"
                  >
                    <path v-if="trendLinePath" :d="trendLinePath" class="trend-chart__line-path" />
                    <circle
                      v-for="point in trendChartPoints"
                      :key="`${point.date}-dot`"
                      :cx="point.x"
                      :cy="point.y"
                      r="1.8"
                      class="trend-chart__line-dot"
                    />
                  </svg>

                  <div v-for="point in trendChartPoints" :key="point.date" class="trend-bar-item">
                    <strong class="trend-bar-value">{{ point.scoreCount }}</strong>
                    <div class="trend-bar-track">
                      <div class="trend-bar-fill" :style="{ height: `${Math.max((point.scoreCount / maxTrendCount) * 100, 8)}%` }"></div>
                    </div>
                    <span class="trend-bar-label">{{ point.date.slice(5) }}</span>
                    <small class="trend-bar-meta">均分 {{ point.avgScore.toFixed(1) }}</small>
                  </div>
                </div>
              </div>

              <div class="trend-summary-grid trend-summary-grid--compact">
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
            </div>
            <el-empty v-else description="暂无趋势数据" />
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
              <el-empty v-else description="当前没有待处理任务" />
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
            <el-empty v-else description="暂无可用快捷入口" />
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
import { DashboardKey } from '@/composables/useDashboard';
const { router, loading, loaded, loadError, trend, recentTasks, quickLinks, summaryIcons, summaryCardPills, localizedSummaryCards, maxTrendCount, trendTotalCount, trendAverageText, trendChartPoints, trendLinePath, trendAxisTicks, trendPulseShortText, peakTrendValue, peakTrendDate, peakTrendHint, urgentTaskCount, distinctActorsCount, riskAlertCount, taskPressureLevel, taskPressureText, workbenchHeadline, heroStatusText, trendFocusTitle, trendFocusDescription, riskAlertSummary, riskAlertDetail, recommendedActionTitle, recommendedActionDescription, riskActionTitle, riskActionDescription, localizedSummarySignal, loadDashboard, padIndex, quickLinkDescription, localizedQuickLinkLabel, localizedTaskLabel, localizedTaskTitle, localizedActorText } = inject(DashboardKey)!;
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.dashboard-header__eyebrow {
  margin-bottom: 6px;
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
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
  border: 1px solid #dbe4f0;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.dashboard-inline-note {
  max-width: 20rem;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
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
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #f8fafc;
}

.dashboard-kpi-row__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: #e2e8f0;
  color: #0f766e;
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
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.dashboard-kpi-row__body strong {
  margin-top: 6px;
  color: #0f172a;
  font-size: 22px;
  font-weight: 800;
}

.dashboard-kpi-row__body small {
  margin-top: 4px;
  color: #64748b;
  font-size: 11px;
}

.dashboard-card,
.state-card {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.dashboard-card :deep(.el-card__header) {
  border-bottom: 1px solid #e2e8f0;
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
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
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
  background: #f8fafc;
}

.dashboard-card__metric--accent {
  background: linear-gradient(180deg, #ecfeff, #ccfbf1);
}

.dashboard-card__metric span {
  font-size: 11px;
  color: #64748b;
}

.dashboard-card__metric strong {
  margin-top: 4px;
  font-size: 20px;
  color: #0f172a;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.trend-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.trend-chart-shell {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 12px;
  height: 220px;
  padding: 12px 10px 10px 0;
  border-radius: 18px;
  border: 1px solid #dbe4f0;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.96), rgba(255, 255, 255, 0.98));
  overflow: hidden;
}

.trend-y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  height: 100%;
  padding: 2px 0 24px;
}

.trend-y-axis span {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
}

.trend-chart {
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 100%;
  min-height: 0;
  padding-top: 2px;
}

.trend-chart__grid {
  position: absolute;
  inset: 0 0 24px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  pointer-events: none;
}

.trend-chart__grid-line {
  display: block;
  width: 100%;
  border-top: 1px dashed rgba(148, 163, 184, 0.34);
}

.trend-bar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-width: 0;
  z-index: 1;
}

.trend-bar-value {
  font-size: 16px;
  color: #0f172a;
}

.trend-bar-track {
  width: 22px;
  height: 148px;
  display: flex;
  align-items: flex-end;
  border-radius: 999px;
  border: 1px solid rgba(203, 213, 225, 0.7);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.82), rgba(241, 245, 249, 0.45));
  padding: 0 0 6px;
}

.trend-bar-fill {
  width: 100%;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(20, 184, 166, 0.5), rgba(15, 118, 110, 0.86));
  box-shadow: 0 10px 18px rgba(15, 118, 110, 0.14);
}

.trend-chart__line {
  position: absolute;
  inset: 0 0 24px;
  pointer-events: none;
  z-index: 2;
}

.trend-chart__line-path {
  fill: none;
  stroke: rgba(15, 118, 110, 0.88);
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.trend-chart__line-dot {
  fill: #ffffff;
  stroke: #0f766e;
  stroke-width: 1.5;
  filter: drop-shadow(0 2px 5px rgba(15, 118, 110, 0.18));
}

.trend-bar-label {
  font-size: 12px;
  color: #334155;
  font-weight: 700;
}

.trend-bar-meta {
  font-size: 11px;
  color: #64748b;
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
  border-radius: 14px;
  background: linear-gradient(180deg, #f8fafc, #ffffff);
  border: 1px solid #e2e8f0;
}

.trend-summary-card__label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #64748b;
}

.trend-summary-card strong {
  display: block;
  margin-top: 8px;
  font-size: 17px;
  color: #0f172a;
}

.trend-summary-card p {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: #64748b;
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
  border: 1px solid #e2e8f0;
  background: linear-gradient(135deg, #ffffff, #f8fafc);
  border-radius: 14px;
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
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
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
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
}

.quick-link-card p {
  margin: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.65;
}

.quick-link-card__footer {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #1d4ed8;
  font-size: 12px;
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
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.task-overview-item__label {
  font-size: 11px;
  color: #64748b;
}

.task-overview-item strong {
  display: block;
  margin-top: 4px;
  font-size: 16px;
  color: #0f172a;
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
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.task-item__badge {
  width: 10px;
  border-radius: 999px;
  background: linear-gradient(180deg, #ef4444, #f97316);
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
  color: #0f172a;
  font-size: 13px;
}

.task-item__index {
  font-size: 11px;
  font-weight: 700;
  color: #94a3b8;
}

.task-item__title {
  margin: 6px 0 0;
  color: #334155;
  font-size: 12px;
  line-height: 1.45;
}

.task-item__meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  font-size: 12px;
  color: #64748b;
}

.task-item__link {
  padding: 0;
  border: none;
  background: transparent;
  color: #1d4ed8;
  font-size: 12px;
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
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.guidance-item__title {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #64748b;
}

.guidance-item strong {
  display: block;
  margin-top: 6px;
  font-size: 16px;
  line-height: 1.35;
  color: #0f172a;
}

.guidance-item p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.guidance-item--warn {
  background: linear-gradient(180deg, #fff7ed, #fffbeb);
}

.state-card {
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
}

.dashboard-side--refactored {
  align-self: start;
}

@media (max-width: 1240px) {
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

  .trend-chart-shell {
    grid-template-columns: 1fr;
    height: auto;
    padding-left: 14px;
  }

  .trend-y-axis {
    display: none;
  }

  .trend-chart {
    height: 220px;
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

  .trend-chart {
    gap: 10px;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .trend-bar-item {
    min-width: 66px;
  }
}
</style>

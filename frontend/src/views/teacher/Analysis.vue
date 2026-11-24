<template>
  <div class="analysis-page workbench-page">
    <section class="workbench-header analysis-header analysis-header--refactored">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <h1>成绩分析与统计</h1>
          <p>查看当前教学活动在班级、任务类型与趋势维度的真实分析数据。</p>
        </div>
        <div class="workbench-header__actions">
          <el-button type="primary" @click="loadAnalysis" :loading="loading">刷新分析</el-button>
        </div>
      </div>
    </section>

    <el-skeleton v-if="loading && !loaded" :rows="8" animated />

    <template v-else>
      <div v-if="loadError" class="state-card">
        <AppEmptyState tone="error" title="分析页加载失败" :description="loadError">
          <template #actions>
            <el-button type="primary" @click="loadAnalysis">重新加载</el-button>
          </template>
        </AppEmptyState>
      </div>

      <template v-else>
        <section v-if="summaryCards.length" class="analysis-summary-grid">
          <article v-for="(card, index) in summaryCards" :key="card.key" class="analysis-summary-card">
            <div class="analysis-summary-card__icon">
              <span class="material-symbols-outlined app-icon--sm">{{ summaryIcons[index % summaryIcons.length] }}</span>
            </div>
            <div>
              <span class="analysis-summary-card__label">{{ card.title }}</span>
              <strong>{{ card.value }}</strong>
            </div>
          </article>
        </section>

        <p class="analysis-scope-note">口径说明：汇总卡、班级对比与类型分布为全时段；趋势为近 7 日已评分记录；参与 / 完成仅统计已发布 / 关闭活动</p>

        <div class="analysis-grid">
          <section class="analysis-main">
            <el-card class="analysis-card" shadow="never">
              <template #header>
                <div class="card-header-row">
                  <div>
                    <span class="section-title">最近评分趋势</span>
                    <p>基于近 7 日已评分记录的提交量与平均分变化。</p>
                  </div>
                </div>
              </template>
              <div v-if="trend.length" class="trend-card-panel">
                <div class="trend-card-panel__overview">
                  <article class="trend-overview-chip trend-overview-chip--accent">
                    <span>峰值提交</span>
                    <strong>{{ maxTrendCount }}</strong>
                    <small>最近 7 日最高评分量</small>
                  </article>
                  <article class="trend-overview-chip">
                    <span>平均分高点</span>
                    <strong>{{ maxTrendScore.toFixed(1) }}</strong>
                    <small>当前周期最高均分</small>
                  </article>
                  <article class="trend-overview-chip">
                    <span>观察重点</span>
                    <strong>{{ trendFocusLabel }}</strong>
                    <small>同时查看提交量与均分变化</small>
                  </article>
                </div>

                <div class="trend-legend">
                  <span class="trend-legend__item">
                    <i class="trend-legend__swatch trend-legend__swatch--bars"></i>
                    评分量
                  </span>
                  <span class="trend-legend__item">
                    <i class="trend-legend__swatch trend-legend__swatch--score"></i>
                    平均分参考点
                  </span>
                </div>

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
                      <div class="trend-bar-meta">
                        <strong>{{ point.scoreCount }}</strong>
                        <span>均分 {{ point.avgScore.toFixed(1) }}</span>
                      </div>
                      <div class="trend-bar-track">
                        <div class="trend-bar-fill" :style="{ height: `${Math.max((point.scoreCount / maxTrendCount) * 100, 8)}%` }"></div><!-- 动态值：按题量占比计算高度 -->
                      </div>
                      <span class="trend-bar-label">{{ point.date.slice(5) }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <el-empty v-else description="暂无趋势数据" />
            </el-card>

            <el-card class="analysis-card" shadow="never">
              <template #header>
                <div class="card-header-row">
                  <div>
                    <span class="section-title">班级对比</span>
                    <p>按班级查看当前均分与评分样本数。</p>
                  </div>
                </div>
              </template>
              <div v-if="sectionComparison.length" class="comparison-list">
                <div v-for="item in sectionComparison" :key="item.className" class="comparison-item">
                  <div class="comparison-item__header">
                    <span>{{ item.className }}</span>
                    <span class="comparison-item__score">{{ item.avgScore.toFixed(1) }}</span>
                  </div>
                  <div class="comparison-progress-bg">
                    <div class="comparison-progress-fill" :style="{ width: `${Math.min(item.avgScore, 100)}%` }"></div><!-- 动态值：按平均分占比计算宽度 -->
                  </div>
                  <p>{{ item.gradedCount }} 条评分记录</p>
                </div>
              </div>
              <el-empty v-else description="暂无班级对比数据" />
            </el-card>

            <ParticipationPanel v-if="participationOverview.length" :overview="participationOverview" />
          </section>

          <aside class="analysis-side">
            <el-card class="analysis-card" shadow="never">
              <template #header>
                <div class="card-header-row">
                  <span class="section-title">任务类型分布</span>
                </div>
              </template>
              <div v-if="businessBreakdown.length" class="breakdown-list">
                <div v-for="item in businessBreakdown" :key="item.businessType" class="breakdown-item">
                  <div>
                    <strong>{{ item.label }}</strong>
                    <p>{{ item.scoreCount }} 条评分记录</p>
                  </div>
                  <span class="breakdown-score">{{ item.avgScore.toFixed(1) }}</span>
                </div>
              </div>
              <el-empty v-else description="暂无类型分布" />
            </el-card>

            <el-card class="analysis-card" shadow="never">
              <template #header>
                <div class="card-header-row">
                  <span class="section-title">洞察与提示</span>
                </div>
              </template>
              <div v-if="insights.length" class="insight-list">
                <div v-for="item in insights" :key="item.title" :class="['insight-item', `insight-item--${item.tone}`]">
                  <strong>{{ item.title }}</strong>
                  <p>{{ item.description }}</p>
                </div>
              </div>
              <el-empty v-else description="暂无洞察信息" />
            </el-card>
          </aside>
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getAnalysis } from '@/api/teacher';
import ParticipationPanel from '@/views/teacher/components/ParticipationPanel.vue';
import type { AnalysisInsight, AnalysisSummaryCard, TeacherAnalysisOverview, TeacherBusinessBreakdownItem, TeacherComparisonItem, TeacherTrendItem } from '@/types/analysis';
import AppEmptyState from '@/components/ui/AppEmptyState.vue';

const loading = ref(false);
const loaded = ref(false);
const loadError = ref('');
const analysis = ref<TeacherAnalysisOverview | null>(null);
const summaryIcons = ['lightbulb', 'warning', 'monitoring', 'list'];

const summaryCards = computed<AnalysisSummaryCard[]>(() => analysis.value?.summaryCards ?? []);
const trend = computed<TeacherTrendItem[]>(() => analysis.value?.trend ?? []);
const sectionComparison = computed<TeacherComparisonItem[]>(() => analysis.value?.sectionComparison ?? []);
const businessBreakdown = computed<TeacherBusinessBreakdownItem[]>(() => analysis.value?.businessBreakdown ?? []);
const insights = computed<AnalysisInsight[]>(() => analysis.value?.insights ?? []);
const participationOverview = computed(() => analysis.value?.participationOverview ?? []);
const maxTrendCount = computed(() => Math.max(...trend.value.map(item => item.scoreCount), 1));
const maxTrendScore = computed(() => Math.max(...trend.value.map(item => item.avgScore), 0));
const trendChartPoints = computed(() => {
  const items = trend.value;
  if (!items.length) {
    return [];
  }

  return items.map((item, index) => {
    const count = Math.max(item.scoreCount, 0);
    const avgScore = Math.min(Math.max(item.avgScore, 0), 100);
    const x = items.length === 1 ? 50 : 4 + (index / (items.length - 1)) * 92;
    const y = 94 - (avgScore / 100) * 88;

    return {
      ...item,
      count,
      avgScore,
      x,
      y
    };
  });
});
const trendLinePath = computed(() => trendChartPoints.value.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`).join(' '));
const trendFocusLabel = computed(() => {
  if (!trend.value.length) {
    return '暂无数据';
  }

  const peakByCount = trend.value.reduce((highest, item) => (item.scoreCount > highest.scoreCount ? item : highest), trend.value[0]);
  return `${peakByCount.date.slice(5)} · ${peakByCount.scoreCount} 条`;
});
const trendAxisTicks = computed(() => {
  const maxValue = maxTrendCount.value;
  return [maxValue, Math.ceil(maxValue * 0.66), Math.ceil(maxValue * 0.33), 0];
});

const loadAnalysis = async () => {
  loading.value = true;
  loadError.value = '';
  try {
    analysis.value = await getAnalysis();
    loaded.value = true;
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '分析页加载失败';
  } finally {
    loading.value = false;
  }
};

onMounted(loadAnalysis);
</script>

<style scoped>
.analysis-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.analysis-header {
  display: block;
}

.analysis-header--refactored {
  align-items: stretch;
}

.analysis-header h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
}

.analysis-header p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.analysis-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(320px, 1fr);
  gap: 16px;
}

.analysis-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.analysis-summary-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.analysis-summary-card__icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: #ecfeff;
  color: #0f766e;
  flex-shrink: 0;
}

.analysis-summary-card__label {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.analysis-summary-card strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 22px;
}

.analysis-scope-note {
  margin: 0 0 2px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.analysis-main,
.analysis-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.analysis-card {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.card-header-row p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.trend-card-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.trend-card-panel__overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.trend-overview-chip {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border: 1px solid #dbe4f0;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
}

.trend-overview-chip--accent {
  border-color: rgba(20, 184, 166, 0.22);
  background: linear-gradient(180deg, rgba(240, 253, 250, 0.96), rgba(236, 254, 255, 0.98));
}

.trend-overview-chip span,
.trend-overview-chip small {
  color: #64748b;
}

.trend-overview-chip span {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.trend-overview-chip strong {
  color: #0f172a;
  font-size: 22px;
  line-height: 1.1;
}

.trend-overview-chip small {
  font-size: 12px;
  line-height: 1.5;
}

.trend-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.trend-legend__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #475569;
  font-size: 12px;
  font-weight: 600;
}

.trend-legend__swatch {
  display: inline-block;
  width: 28px;
  height: 10px;
  border-radius: 999px;
}

.trend-legend__swatch--bars {
  background: linear-gradient(90deg, #0f766e, #14b8a6);
}

.trend-legend__swatch--score {
  position: relative;
  width: 14px;
  height: 14px;
  border-radius: 999px;
  background: #ffffff;
  border: 3px solid #0f172a;
  box-shadow: 0 0 0 3px rgba(15, 23, 42, 0.08);
}

.trend-chart-shell {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 12px;
  height: 240px;
  padding: 16px 18px 14px 14px;
  border: 1px solid #dbe4f0;
  border-radius: 16px;
  background:
    linear-gradient(180deg, rgba(248, 250, 252, 0.96), rgba(255, 255, 255, 0.98)),
    radial-gradient(circle at top left, rgba(20, 184, 166, 0.08), transparent 42%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.trend-y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  height: 100%;
  padding: 2px 0 26px;
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
  gap: 14px;
  height: 100%;
  min-height: 0;
  padding: 2px 0 0;
  overflow: hidden;
}

.trend-chart__grid {
  position: absolute;
  inset: 0 0 28px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  pointer-events: none;
}

.trend-chart__grid-line {
  display: block;
  width: 100%;
  border-top: 1px dashed rgba(148, 163, 184, 0.42);
}

.trend-bar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  min-width: 0;
  z-index: 1;
}

.trend-bar-meta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  min-height: 38px;
}

.trend-bar-meta strong {
  color: #0f172a;
  font-size: 16px;
  line-height: 1;
}

.trend-bar-meta span {
  color: #64748b;
  font-size: 11px;
  font-weight: 600;
}

.trend-bar-track {
  position: relative;
  width: 26px;
  height: 162px;
  display: flex;
  align-items: flex-end;
  border: 1px solid rgba(203, 213, 225, 0.7);
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.82), rgba(241, 245, 249, 0.45));
  padding: 0 0 6px;
  overflow: visible;
}

.trend-bar-fill {
  width: 100%;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(20, 184, 166, 0.52) 0%, rgba(15, 118, 110, 0.86) 100%);
  box-shadow: 0 10px 18px rgba(15, 118, 110, 0.16);
}

.trend-chart__line {
  position: absolute;
  inset: 0 0 28px;
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
  color: #475569;
  font-weight: 700;
}

.comparison-list,
.breakdown-list,
.insight-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.comparison-item,
.breakdown-item,
.insight-item {
  padding: 16px;
  border-radius: 16px;
  background: #f8fafc;
}

.comparison-item__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 700;
}

.comparison-item__score,
.breakdown-score {
  color: #004d64;
  font-weight: 800;
}

.comparison-progress-bg {
  width: 100%;
  height: 8px;
  border-radius: 999px;
  background: #e2e8f0;
  overflow: hidden;
}

.comparison-progress-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #004d64, #14b8a6);
}

.comparison-item p,
.breakdown-item p,
.insight-item p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
}

.breakdown-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.insight-item--positive { background: #ecfeff; }
.insight-item--highlight { background: #eff6ff; }
.insight-item--warning { background: #fff7ed; }

@media (max-width: 1024px) {
  .analysis-summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .analysis-grid { grid-template-columns: 1fr; }
  .trend-card-panel__overview { grid-template-columns: 1fr; }
}

@media (max-width: 768px) {
  .analysis-header { flex-direction: column; align-items: flex-start; }
  .analysis-summary-grid { grid-template-columns: 1fr; }
  .trend-chart-shell {
    grid-template-columns: 1fr;
    height: auto;
    padding-left: 16px;
  }
  .trend-y-axis {
    display: none;
  }
  .trend-chart {
    gap: 10px;
    height: 230px;
  }
  .trend-bar-meta span,
  .trend-bar-score-marker span {
    display: none;
  }
}
</style>

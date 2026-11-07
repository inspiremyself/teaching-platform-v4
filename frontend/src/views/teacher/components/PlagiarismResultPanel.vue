<template>
  <div class="page plagiarism-console workbench-page">
    <section class="workbench-header plagiarism-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="plagiarism-header__eyebrow">教师控制台 / 查重复核</div>
          <h1>查重审阅工作台</h1>
          <p>聚焦任务切换、证据核查与连续复核。</p>
        </div>
        <div class="workbench-header__actions console-actions">
          <button class="console-button console-button--secondary" type="button" @click="fetchData" :disabled="loading">
            <span class="material-symbols-outlined">refresh</span>
            刷新
          </button>
          <button class="console-button console-button--primary" type="button" @click="focusPendingTask" :disabled="!pendingCount">
            <span class="material-symbols-outlined">priority_high</span>
            处理待复核
          </button>
        </div>
      </div>

      <div class="workbench-meta plagiarism-meta">
        <span class="workbench-meta__item"><strong>{{ rows.length }}</strong> 任务</span>
        <span class="workbench-meta__item"><strong>{{ filteredRows.length }}</strong> 当前范围</span>
        <span class="workbench-meta__item"><strong>{{ pendingCount }}</strong> 待复核</span>
        <span class="workbench-meta__item workbench-meta__item--accent"><strong>{{ highRiskCount }}</strong> 高风险</span>
        <span class="workbench-meta__item"><strong>{{ reviewedCount }}</strong> 已复核</span>
        <span class="workbench-meta__item">平均 {{ averageSimilarityText }}</span>
      </div>
    </section>

    <section class="toolbar-card toolbar-card--compact">
      <div class="toolbar-card__main">
        <div class="toolbar-search">
          <span class="material-symbols-outlined">search</span>
          <el-input v-model="searchQuery" placeholder="搜索学生、任务、摘要或匹配来源" clearable />
        </div>
        <div class="toolbar-filters">
          <div class="toolbar-filter toolbar-filter--compact">
            <label>业务</label>
            <el-select v-model="businessTypeFilter" clearable placeholder="全部类型">
              <el-option label="作业" value="HOMEWORK" />
              <el-option label="实验" value="LAB" />
              <el-option label="考试" value="EXAM" />
            </el-select>
          </div>
          <div class="toolbar-filter toolbar-filter--compact">
            <label>状态</label>
            <el-select v-model="statusFilter" clearable placeholder="全部状态">
              <el-option label="待处理" value="PENDING" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="已复核" value="REVIEWED" />
            </el-select>
          </div>
          <div class="toolbar-filter toolbar-filter--compact">
            <label>风险</label>
            <el-select v-model="riskFilter" clearable placeholder="全部风险">
              <el-option label="高风险" value="HIGH" />
              <el-option label="中风险" value="MEDIUM" />
              <el-option label="低风险" value="LOW" />
              <el-option label="暂无结果" value="UNKNOWN" />
            </el-select>
          </div>
        </div>
      </div>
      <div class="toolbar-card__meta">
        <span>筛出 {{ filteredRows.length }} / {{ rows.length }}</span>
        <span class="toolbar-card__dot"></span>
        <span>{{ toolbarMetaText }}</span>
        <span class="toolbar-card__dot"></span>
        <span>{{ activeTask ? highlightedTaskTitle : '尚未选中任务' }}</span>
        <el-button link type="primary" @click="resetFilters">清空筛选</el-button>
      </div>
    </section>

    <div v-if="listError" class="feedback-card feedback-card--error">
      <div>
        <strong>查重任务加载失败</strong>
        <p>{{ listError }}</p>
      </div>
      <el-button type="danger" plain @click="fetchData">重试</el-button>
    </div>

    <section class="governance-layout" v-loading="loading">
      <aside class="queue-panel">
        <div class="queue-panel__header">
          <div>
            <h2>任务队列</h2>
            <p>{{ filteredRows.length }} 条可切换任务</p>
          </div>
          <span class="queue-panel__badge">{{ pendingCount }} 待处理</span>
        </div>

        <div v-if="filteredRows.length" class="queue-list">
          <button
            v-for="row in filteredRows"
            :key="row.id"
            type="button"
            :class="['queue-item', activeTask?.id === row.id ? 'is-active' : '']"
            @click="selectTask(row)"
          >
            <div class="queue-item__topline">
              <div class="queue-item__identity">
                <div :class="['queue-avatar', `queue-avatar--${riskTone(normalizedRiskLevel(row))}`]">
                  {{ getAvatarText(getStudentName(row), getBusinessName(row)) }}
                </div>
                <div class="queue-item__headings">
                  <strong>{{ getStudentName(row) }}</strong>
                  <span>{{ businessTypeLabel(getBusinessType(row)) }} · {{ getBusinessName(row) }}</span>
                </div>
              </div>
              <span :class="['status-pill', `status-pill--${statusTone(normalizedStatus(row))}`]">
                <span class="status-pill__dot"></span>
                {{ statusLabel(normalizedStatus(row)) }}
              </span>
            </div>

            <div class="queue-item__stats">
              <span :class="['risk-chip', `risk-chip--${riskTone(normalizedRiskLevel(row))}`]">
                {{ riskLevelLabel(normalizedRiskLevel(row)) }}
              </span>
              <strong>{{ formatRate(getSimilarityRate(row)) }}</strong>
              <span class="queue-item__reference">{{ topMatchReference(row) }}</span>
            </div>

            <p class="queue-item__summary">{{ getTopSummary(row) || '暂无查重摘要' }}</p>
          </button>
        </div>

        <el-empty v-else description="暂无匹配查重任务" />
      </aside>

      <main class="detail-panel detail-panel--workbench">
        <div v-if="activeTask" class="detail-panel__inner">
          <section class="detail-context-card">
            <div class="detail-context-card__lead">
              <div :class="['detail-context-card__avatar', `is-${riskTone(normalizedRiskLevel(activeTask))}`]">
                {{ getAvatarText(getStudentName(activeTask), getBusinessName(activeTask)) }}
              </div>
              <div>
                <h2>{{ getStudentName(activeTask) }}</h2>
                <p>{{ activeTaskSubline }}</p>
              </div>
            </div>
            <div class="detail-context-card__aside">
              <span :class="['risk-chip', `risk-chip--${riskTone(normalizedRiskLevel(activeTask))}`]">
                {{ riskLevelLabel(normalizedRiskLevel(activeTask)) }}
              </span>
              <div class="detail-context-card__nav">{{ activeTaskOrderLabel }}</div>
            </div>
          </section>

          <div v-if="detailError" class="feedback-card feedback-card--inline-error">
            <div>
              <strong>详情加载失败</strong>
              <p>{{ detailError }}</p>
            </div>
            <el-button type="danger" plain @click="loadDetail(activeTask.id)">重试</el-button>
          </div>

          <div v-else-if="detailLoading" class="detail-loading-shell">
            <el-skeleton animated :rows="8" />
          </div>

          <div v-else-if="detail" class="detail-workbench">
            <div class="detail-main-column">
              <section class="detail-overview-card compact-card">
                <div class="panel-section__header panel-section__header--tight">
                  <div>
                    <h3>概览</h3>
                    <p>{{ highlightedTaskMeta }}</p>
                  </div>
                  <div class="detail-overview-card__chips">
                    <span :class="['status-pill', `status-pill--${statusTone(normalizedStatus(detail))}`]">
                      <span class="status-pill__dot"></span>
                      {{ statusLabel(normalizedStatus(detail)) }}
                    </span>
                  </div>
                </div>

                <div class="overview-grid overview-grid--dense">
                  <div class="overview-item">
                    <span>业务类型</span>
                    <strong>{{ businessTypeLabel(getBusinessType(detail)) }}</strong>
                  </div>
                  <div class="overview-item">
                    <span>任务名称</span>
                    <strong>{{ getBusinessName(detail) }}</strong>
                  </div>
                  <div class="overview-item">
                    <span>重复率</span>
                    <strong>{{ formatRate(getSimilarityRate(detail)) }}</strong>
                  </div>
                  <div class="overview-item">
                    <span>匹配来源</span>
                    <strong>{{ topMatchReference(detail) }}</strong>
                  </div>
                </div>

                <div class="narrative-block narrative-block--compact">
                  <div class="narrative-block__title">摘要</div>
                  <p>{{ getTopSummary(detail) || '当前未返回摘要文本。' }}</p>
                </div>

                <div v-if="reviewContextBlocks.length" class="evidence-grid evidence-grid--dense">
                  <article v-for="block in reviewContextBlocks" :key="block.title" class="evidence-card">
                    <div class="evidence-card__label">{{ block.title }}</div>
                    <div class="evidence-card__value">{{ block.content }}</div>
                  </article>
                </div>
              </section>

              <section class="detail-analysis-card compact-card">
                <div class="panel-section__header panel-section__header--tight">
                  <div>
                    <h3>算法结果</h3>
                    <p>保留 `rawResultJson` 与结构化字段兼容展示。</p>
                  </div>
                </div>

                <div class="analysis-grid analysis-grid--dense">
                  <div class="analysis-item">
                    <span>算法</span>
                    <strong>{{ parsedAlgorithmResult?.algorithm || '未返回' }}</strong>
                  </div>
                  <div class="analysis-item">
                    <span>版本</span>
                    <strong>{{ parsedAlgorithmResult?.algorithmVersion || '-' }}</strong>
                  </div>
                  <div class="analysis-item">
                    <span>Jaccard 相似度</span>
                    <strong>{{ formatRate(readNumber(parsedAlgorithmResult?.jaccardSimilarityRate)) }}</strong>
                  </div>
                  <div class="analysis-item">
                    <span>最高匹配</span>
                    <strong>{{ parsedAlgorithmResult?.topMatchReference || topMatchReference(detail) }}</strong>
                  </div>
                </div>

                <div v-if="matchedSegments.length" class="segments-list segments-list--compact">
                  <div class="segments-list__title">命中片段</div>
                  <article v-for="(segment, index) in matchedSegments" :key="`${segment.title}-${index}`" class="segment-card">
                    <div class="segment-card__header">
                      <strong>{{ segment.title }}</strong>
                      <span v-if="segment.rate">{{ segment.rate }}</span>
                    </div>
                    <p>{{ segment.content }}</p>
                  </article>
                </div>

                <el-empty v-else description="当前没有可展示的结构化命中片段" />
              </section>
            </div>

            <aside class="review-rail">
              <section class="score-panel compact-card score-panel--sticky">
                <div class="panel-section__header panel-section__header--tight">
                  <div>
                    <h3>复核操作</h3>
                    <p>{{ reviewHintText }}</p>
                  </div>
                </div>

                <div class="score-summary-list score-summary-list--compact">
                  <div class="score-summary-item">
                    <span>状态</span>
                    <strong>{{ statusLabel(normalizedStatus(activeTask)) }}</strong>
                  </div>
                  <div class="score-summary-item">
                    <span>重复率</span>
                    <strong>{{ formatRate(getSimilarityRate(activeTask)) }}</strong>
                  </div>
                  <div class="score-summary-item score-summary-item--total">
                    <span>风险</span>
                    <strong>{{ riskLevelLabel(normalizedRiskLevel(activeTask)) }}</strong>
                  </div>
                </div>

                <div class="review-summary review-summary--stacked">
                  <div class="review-summary__row">
                    <span>学生</span>
                    <strong>{{ getStudentName(activeTask) }}</strong>
                  </div>
                  <div class="review-summary__row">
                    <span>业务</span>
                    <strong>{{ getBusinessName(activeTask) }}</strong>
                  </div>
                </div>

                <el-form label-position="top" class="review-form review-form--compact">
                  <el-form-item label="复核意见">
                    <el-input
                      v-model="reviewComment"
                      type="textarea"
                      :rows="7"
                      maxlength="500"
                      show-word-limit
                      placeholder="输入教师复核意见"
                    />
                  </el-form-item>
                </el-form>

                <div class="quick-comment-block quick-comment-block--compact">
                  <p>快捷意见</p>
                  <div class="quick-comment-block__chips">
                    <button type="button" class="quick-comment-chip" @click="appendQuickComment('已核查相似来源，建议结合原文上下文进一步判定。')">
                      进一步判定
                    </button>
                    <button type="button" class="quick-comment-chip" @click="appendQuickComment('重复内容主要集中在公共模板区域，可标记为低风险留档。')">
                      模板重复
                    </button>
                    <button type="button" class="quick-comment-chip" @click="appendQuickComment('存在明显高相似片段，建议启动正式学术诚信复核流程。')">
                      升级处理
                    </button>
                  </div>
                </div>

                <div class="review-actions-row review-actions-row--stacked">
                  <button class="action-panel__primary" type="button" @click="submitReview" :disabled="reviewLoading || !activeTask">
                    <span class="material-symbols-outlined">fact_check</span>
                    {{ normalizedStatus(activeTask) === 'REVIEWED' ? '更新复核意见' : '提交复核' }}
                  </button>
                  <button class="action-panel__secondary" type="button" @click="focusNextTask" :disabled="!nextTask">
                    下一条任务
                  </button>
                </div>
              </section>
            </aside>
          </div>

          <el-empty v-else description="当前任务暂无可展示详情" />
        </div>

        <el-empty v-else description="请选择左侧查重任务查看详情" />
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { inject } from 'vue';
import { PlagiarismKey } from '@/composables/usePlagiarism';
const { loading, listError, rows, detailLoading, detailError, detail, reviewComment, reviewLoading, searchQuery, businessTypeFilter, statusFilter, riskFilter, businessTypeLabel, statusLabel, statusTone, riskTone, riskLevelLabel, readNumber, formatRate, getAvatarText, getStudentName, getBusinessType, getBusinessName, getSimilarityRate, getTopSummary, topMatchReference, normalizedStatus, normalizedRiskLevel, parsedAlgorithmResult, filteredRows, activeTask, nextTask, pendingCount, reviewedCount, highRiskCount, averageSimilarityText, highlightedTaskTitle, highlightedTaskMeta, toolbarMetaText, activeTaskSubline, activeTaskOrderLabel, reviewHintText, reviewContextBlocks, matchedSegments, loadDetail, fetchData, selectTask, focusPendingTask, focusNextTask, resetFilters, appendQuickComment, submitReview } = inject(PlagiarismKey)!;
</script>

<style scoped>
.plagiarism-console {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 0;
  color: #0f172a;
  background: transparent;
}

.plagiarism-header__eyebrow,
.toolbar-filter label,
.overview-item span,
.analysis-item span,
.evidence-card__label,
.narrative-block__title,
.segments-list__title,
.review-summary__row span,
.queue-item__headings span {
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.plagiarism-header__eyebrow {
  margin-bottom: 4px;
  color: #0f766e;
  font-weight: 800;
}

.toolbar-card,
.queue-panel,
.compact-card,
.feedback-card,
.detail-context-card {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.console-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.console-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border: 0;
  border-radius: 999px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.console-button:disabled,
.action-panel__primary:disabled,
.action-panel__secondary:disabled {
  opacity: 0.52;
  cursor: not-allowed;
}

.console-button:not(:disabled):hover,
.queue-item:hover,
.action-panel__primary:not(:disabled):hover,
.action-panel__secondary:not(:disabled):hover,
.quick-comment-chip:hover {
  transform: translateY(-1px);
}

.console-button--secondary {
  color: #0f172a;
  background: #e2e8f0;
}

.console-button--primary,
.action-panel__primary {
  color: #fff;
  background: linear-gradient(135deg, #0f766e, #0284c7);
}

.toolbar-card {
  padding: 12px 14px;
}

.toolbar-card__main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.toolbar-search {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 240px;
  padding: 9px 12px;
  border-radius: 12px;
  background: #f8fafc;
}

.toolbar-search > .material-symbols-outlined {
  color: #64748b;
}

.toolbar-search :deep(.el-input) {
  flex: 1;
}

.toolbar-search :deep(.el-input__wrapper),
.toolbar-filter :deep(.el-select__wrapper) {
  box-shadow: none;
  background: transparent;
}

.toolbar-filters {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex-wrap: wrap;
}

.toolbar-filter {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 126px;
}

.toolbar-filter label,
.overview-item span,
.analysis-item span,
.evidence-card__label,
.narrative-block__title,
.segments-list__title,
.review-summary__row span {
  color: #64748b;
  font-weight: 700;
}

.toolbar-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
}

.toolbar-card__dot {
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: #cbd5e1;
}

.feedback-card {
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.feedback-card--error,
.feedback-card--inline-error {
  border-color: rgba(239, 68, 68, 0.28);
  background: rgba(254, 242, 242, 0.96);
}

.feedback-card strong,
.panel-section__header h3,
.queue-panel__header h2,
.detail-context-card h2 {
  margin: 0;
}

.feedback-card p,
.panel-section__header p,
.queue-panel__header p,
.detail-context-card p,
.narrative-block p,
.segment-card p {
  margin: 4px 0 0;
  color: #475569;
  line-height: 1.5;
}

.governance-layout {
  display: grid;
  grid-template-columns: minmax(300px, 0.9fr) minmax(0, 1.7fr);
  gap: 14px;
  align-items: start;
}

.queue-panel {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: calc(100vh - 32px);
  min-height: calc(100vh - 32px);
  padding: 12px;
  overflow: hidden;
}

.queue-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.queue-panel__badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(251, 191, 36, 0.16);
  color: #b45309;
  font-weight: 700;
  font-size: 12px;
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-right: 6px;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.queue-list::-webkit-scrollbar {
  width: 8px;
}

.queue-list::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.5);
}

.queue-list::-webkit-scrollbar-track {
  background: transparent;
}

.queue-item {
  width: 100%;
  padding: 10px 11px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 12px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.queue-item.is-active {
  border-color: rgba(14, 165, 233, 0.4);
  box-shadow: 0 10px 20px rgba(14, 165, 233, 0.12);
}

.queue-item__topline,
.queue-item__identity,
.detail-context-card,
.detail-context-card__lead,
.detail-context-card__aside,
.segment-card__header,
.review-summary__row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.queue-item__identity {
  flex: 1;
  justify-content: flex-start;
}

.queue-avatar,
.detail-context-card__avatar {
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 14px;
}

.queue-avatar--danger,
.detail-context-card__avatar.is-danger {
  background: rgba(239, 68, 68, 0.14);
  color: #b91c1c;
}

.queue-avatar--warning,
.detail-context-card__avatar.is-warning {
  background: rgba(245, 158, 11, 0.16);
  color: #b45309;
}

.queue-avatar--safe,
.detail-context-card__avatar.is-safe {
  background: rgba(20, 184, 166, 0.14);
  color: #0f766e;
}

.queue-avatar--neutral,
.detail-context-card__avatar.is-neutral {
  background: rgba(148, 163, 184, 0.16);
  color: #475569;
}

.queue-item__headings {
  min-width: 0;
}

.queue-item__headings strong {
  display: block;
  font-size: 14px;
  line-height: 1.3;
  color: #0f172a;
}

.queue-item__headings span {
  display: block;
  margin-top: 2px;
  color: #64748b;
}

.queue-item__stats {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  margin-top: 8px;
}

.queue-item__stats strong {
  font-size: 13px;
  color: #0f172a;
}

.queue-item__reference,
.queue-item__summary {
  font-size: 12px;
  line-height: 1.45;
  color: #475569;
}

.queue-item__reference {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue-item__summary {
  display: -webkit-box;
  margin: 8px 0 0;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.detail-panel {
  min-width: 0;
}

.detail-panel__inner,
.detail-main-column,
.review-rail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-context-card {
  padding: 12px 14px;
  background: linear-gradient(135deg, #f8fafc, #eff6ff);
}

.detail-context-card__aside {
  flex-direction: column;
  align-items: flex-end;
}

.detail-context-card__nav {
  padding: 7px 10px;
  border-radius: 999px;
  background: #fff;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.detail-loading-shell,
.compact-card {
  padding: 12px 14px;
}

.detail-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(280px, 0.86fr);
  gap: 14px;
  align-items: start;
}

.panel-section__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-section__header--tight p {
  font-size: 12px;
}

.detail-overview-card__chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.overview-grid,
.analysis-grid,
.evidence-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.overview-item,
.analysis-item,
.evidence-card,
.segment-card,
.score-summary-item,
.review-summary {
  padding: 10px 11px;
  border-radius: 10px;
  background: #f8fafc;
}

.overview-item strong,
.analysis-item strong,
.score-summary-item strong,
.review-summary__row strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  line-height: 1.4;
}

.narrative-block {
  margin-top: 10px;
  padding: 10px 11px;
  border-radius: 10px;
  background: #f8fafc;
}

.evidence-grid,
.segments-list,
.score-summary-list,
.review-form,
.quick-comment-block,
.review-summary {
  margin-top: 10px;
}

.evidence-card__value,
.segment-card p {
  margin-top: 6px;
  line-height: 1.5;
  color: #334155;
  font-size: 13px;
}

.segments-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.status-pill,
.risk-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill__dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: currentColor;
}

.status-pill--warning {
  color: #b45309;
  background: rgba(251, 191, 36, 0.16);
}

.status-pill--primary {
  color: #0369a1;
  background: rgba(14, 165, 233, 0.14);
}

.status-pill--success {
  color: #0f766e;
  background: rgba(45, 212, 191, 0.16);
}

.status-pill--neutral {
  color: #475569;
  background: rgba(148, 163, 184, 0.14);
}

.risk-chip--danger {
  color: #b91c1c;
  background: rgba(239, 68, 68, 0.14);
}

.risk-chip--warning {
  color: #b45309;
  background: rgba(245, 158, 11, 0.16);
}

.risk-chip--safe {
  color: #0f766e;
  background: rgba(20, 184, 166, 0.14);
}

.risk-chip--neutral {
  color: #475569;
  background: rgba(148, 163, 184, 0.14);
}

.score-panel--sticky {
  position: sticky;
  top: 16px;
}

.score-summary-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.score-summary-item--total {
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.12), rgba(14, 165, 233, 0.14));
}

.review-summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.review-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.quick-comment-block p {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.quick-comment-block__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-comment-chip,
.action-panel__primary,
.action-panel__secondary {
  border: 0;
  cursor: pointer;
}

.quick-comment-chip {
  padding: 8px 10px;
  border-radius: 999px;
  background: #e2e8f0;
  color: #0f172a;
  font-weight: 600;
  font-size: 12px;
}

.review-actions-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(120px, 136px);
  gap: 8px;
}

.action-panel__primary,
.action-panel__secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 11px 14px;
  border-radius: 12px;
  font-weight: 700;
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.action-panel__secondary {
  color: #0f172a;
  background: #e2e8f0;
}

@media (max-width: 1280px) {
  .detail-workbench {
    grid-template-columns: 1fr;
  }

  .review-rail,
  .score-panel--sticky,
  .queue-panel {
    position: static;
    max-height: none;
  }
}

@media (max-width: 1120px) {
  .toolbar-card__main,
  .governance-layout,
  .detail-workbench,
  .overview-grid,
  .analysis-grid,
  .evidence-grid,
  .score-summary-list,
  .review-actions-row {
    grid-template-columns: 1fr;
  }

  .toolbar-card__main {
    display: flex;
    flex-direction: column;
  }
}

@media (max-width: 768px) {
  .queue-item__stats {
    grid-template-columns: 1fr;
  }

  .detail-context-card,
  .queue-item__topline {
    flex-direction: column;
  }

  .detail-context-card__aside {
    align-items: flex-start;
  }
}
</style>

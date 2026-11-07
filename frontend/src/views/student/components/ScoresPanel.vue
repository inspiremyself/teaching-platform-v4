<template>
  <div class="scores-page workbench-page">
    <section class="workbench-header scores-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="scores-header__eyebrow">学生端 / 成绩总览</div>
          <div class="scores-header__title-row">
            <h1 class="scores-hero__title">我的成绩</h1>
            <span class="scores-header__summary">{{ recentRecords.length }} 条记录</span>
          </div>
        </div>
        <div class="workbench-header__actions scores-hero__actions">
          <el-button type="primary" @click="loadScores" :loading="loading">刷新成绩</el-button>
        </div>
      </div>

      <div class="workbench-meta scores-meta" v-if="summaryCards.length">
        <span v-for="card in summaryCards" :key="card.key" class="workbench-meta__item">
          <strong>{{ card.value }}</strong> {{ card.title }}
        </span>
      </div>
    </section>

    <el-skeleton v-if="loading && !loaded" :rows="10" animated />

    <template v-else>
      <div v-if="loadError" class="state-card">
        <el-result icon="error" title="成绩总览加载失败" :sub-title="loadError">
          <template #extra>
            <el-button type="primary" @click="loadScores">重新加载</el-button>
          </template>
        </el-result>
      </div>

      <template v-else>
        <section class="overview-grid overview-grid--flat">
          <article class="overall-card overall-card--flat">
            <div class="overall-card__flat-topline">
              <div>
                <div class="overall-card__label">总评</div>
                <h2>{{ primarySummaryCard?.title ?? '当前成绩总览' }}</h2>
              </div>
              <div class="overall-card__badge">{{ scoreLevelLabel }}</div>
            </div>

            <div class="overall-card__flat-main">
              <div class="overall-card__flat-score">
                <span class="overall-card__flat-score-label">平均成绩</span>
                <strong>{{ primarySummaryCard?.value ?? '--' }}</strong>
              </div>

              <div class="overall-card__flat-stats">
                <div class="overall-stat overall-stat--compact">
                  <span>已评分任务</span>
                  <strong>{{ completedItemsDisplay }}</strong>
                </div>
                <div class="overall-stat overall-stat--compact">
                  <span>完成进度</span>
                  <strong>{{ completionRateDisplay }}</strong>
                </div>
                <div class="overall-stat overall-stat--compact">
                  <span>当前最强板块</span>
                  <strong>{{ strongestGroup?.label ?? '待形成判断' }}</strong>
                </div>
              </div>
            </div>

            <p class="overall-card__desc overall-card__desc--flat">{{ overallInterpretation }}</p>
          </article>

          <div class="metric-stack metric-stack--flat">
            <article v-for="(card, index) in secondarySummaryCards" :key="card.key" class="metric-card metric-card--compact">
              <div :class="['metric-card__icon', `metric-card__icon--${index}`]">
                <el-icon :size="18"><component :is="secondaryMetricIcon(index)" /></el-icon>
              </div>
              <div>
                <div class="metric-card__label">{{ card.title }}</div>
                <div class="metric-card__value">{{ card.value }}</div>
              </div>
            </article>

            <article class="metric-card metric-card--progress">
              <div class="metric-card__header">
                <div>
                  <div class="metric-card__label">完成率</div>
                  <div class="metric-card__value">{{ completionRateDisplay }}</div>
                </div>
                <span class="metric-card__pill">{{ groupedScores.length }} 个成绩分组</span>
              </div>
              <el-progress :percentage="completionRateNumeric" :stroke-width="10" :show-text="false" color="#0d9488" />
              <p class="metric-card__desc metric-card__desc--spacious">{{ completionRateDisplay }} · {{ completedItemsDisplay }}</p>
            </article>
          </div>
        </section>

        <div class="workspace-grid">
          <section class="workspace-main">
            <el-card class="scores-card" shadow="never">
              <template #header>
                <div class="card-header-row card-header-row--tight">
                  <div>
                    <span class="section-title">成绩构成与分组表现</span>
                    <p>按实验、作业、考试分组查看当前均分和最近记录。</p>
                  </div>
                  <div class="header-meta">共 {{ recentRecords.length }} 条已评分记录</div>
                </div>
              </template>

              <div v-if="groupSections.length" class="group-section-list">
                <section v-for="section in groupSections" :key="section.businessType" class="group-section">
                  <div class="group-section__header">
                    <div class="group-section__title-wrap">
                      <div :class="['group-section__icon', `group-section__icon--${section.businessType.toLowerCase()}`]">
                        <el-icon :size="18"><component :is="section.icon" /></el-icon>
                      </div>
                      <div>
                        <h3>{{ section.label }}</h3>
                        <p>{{ section.scoreCount }} 条记录 · {{ section.performanceLabel }}</p>
                      </div>
                    </div>
                    <div class="group-section__meta">
                      <span class="group-section__badge">{{ section.scoreCount }} 条样本</span>
                      <strong>{{ section.avgScore.toFixed(1) }}</strong>
                      <small>组内均分</small>
                    </div>
                  </div>

                  <div v-if="section.records.length" class="group-section__body">
                    <div v-if="section.businessType === 'EXAM'" class="exam-record-grid">
                      <article v-for="record in section.records" :key="record.id" class="exam-record-card">
                        <div class="exam-record-card__header">
                          <div>
                            <h4>{{ record.businessName }}</h4>
                            <p>{{ formatDate(record.gradedAt) }}</p>
                          </div>
                          <span class="score-pill score-pill--exam">{{ formatScore(record.score) }}</span>
                        </div>
                        <div class="exam-record-card__footer">
                          <span class="exam-chip">{{ typeLabel(record.businessType) }}</span>
                          <span class="exam-chip exam-chip--success">已评分</span>
                        </div>
                      </article>
                    </div>

                    <div v-else class="record-table">
                      <div class="record-table__head">
                        <span>项目名称</span>
                        <span>类型</span>
                        <span>评分时间</span>
                        <span>成绩</span>
                        <span>状态</span>
                      </div>
                      <div v-for="record in section.records" :key="record.id" class="record-table__row">
                        <div class="record-table__cell record-table__cell--name">
                          <strong>{{ record.businessName }}</strong>
                        </div>
                        <div class="record-table__cell">{{ typeLabel(record.businessType) }}</div>
                        <div class="record-table__cell">{{ formatDate(record.gradedAt) }}</div>
                        <div class="record-table__cell record-table__cell--score">{{ formatScore(record.score) }}</div>
                        <div class="record-table__cell">
                          <span class="status-chip">已评分</span>
                        </div>
                      </div>
                    </div>
                  </div>

                  <el-empty v-else description="该分组暂无最近评分记录" />
                </section>
              </div>

              <el-empty v-else description="暂无分组表现数据" />
            </el-card>

            <el-card class="scores-card" shadow="never">
              <template #header>
                <div class="card-header-row card-header-row--tight">
                  <div>
                    <span class="section-title">最近评分动态</span>
                    <p>按最近评分时间回看最新成绩变化。</p>
                  </div>
                </div>
              </template>

              <div v-if="recentHighlights.length" class="recent-record-list">
                <article v-for="record in recentHighlights" :key="record.id" class="recent-record-item">
                  <div class="recent-record-item__main">
                    <div class="recent-record-item__type">{{ typeLabel(record.businessType) }}</div>
                    <h3>{{ record.businessName }}</h3>
                    <p>{{ formatDate(record.gradedAt) }} · 已纳入成绩总览</p>
                  </div>
                  <div class="recent-record-item__score">{{ formatScore(record.score) }}</div>
                </article>
              </div>

              <el-empty v-else description="暂无最近评分记录" />
            </el-card>
          </section>

          <aside class="workspace-side">
            <el-card id="feedback-panel" class="scores-card" shadow="never">
              <template #header>
                <div class="card-header-row card-header-row--tight">
                  <div>
                    <span class="section-title">反馈说明</span>
                    <p>提炼教师反馈和系统提示。</p>
                  </div>
                </div>
              </template>

              <div v-if="feedbackNotes.length" class="feedback-panel">
                <div class="feedback-hero">
                  <div class="feedback-hero__avatar">
                    <el-icon :size="20"><ChatLineRound /></el-icon>
                  </div>
                  <div>
                    <div class="feedback-hero__label">重点反馈</div>
                    <h3>{{ feedbackNotes[0]?.title }}</h3>
                    <p>{{ feedbackNotes[0]?.description }}</p>
                  </div>
                </div>

                <div class="feedback-list">
                  <article v-for="item in feedbackNotes" :key="item.title" :class="['feedback-item', `feedback-item--${item.tone}`]">
                    <strong>{{ item.title }}</strong>
                    <p>{{ item.description }}</p>
                  </article>
                </div>
              </div>

              <el-empty v-else description="暂无反馈说明" />
            </el-card>

            <el-card class="scores-card" shadow="never">
              <template #header>
                <div class="card-header-row card-header-row--tight">
                  <div>
                    <span class="section-title">成绩解读与下一步</span>
                    <p>给出当前最值得优先处理的学习方向。</p>
                  </div>
                </div>
              </template>

              <div class="action-list">
                <article v-for="item in actionableInsights" :key="item.title" :class="['action-item', `action-item--${item.tone}`]">
                  <strong>{{ item.title }}</strong>
                  <p>{{ item.description }}</p>
                </article>
              </div>
            </el-card>
          </aside>
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { inject } from 'vue';
import { ScoresKey } from '@/composables/useScores';
const { ChatLineRound, loading, loaded, loadError, summaryCards, groupedScores, recentRecords, feedbackNotes, primarySummaryCard, secondarySummaryCards, completionRateNumeric, completionRateDisplay, completedItemsDisplay, strongestGroup, scoreLevelLabel, overallInterpretation, recentHighlights, performanceLabel, groupSections, actionableInsights, loadScores, secondaryMetricIcon, typeLabel, formatScore, formatDate } = inject(ScoresKey)!;
</script>

<style scoped>
.scores-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.scores-header {
  padding: 10px 12px 6px;
}
.scores-header__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.scores-header__summary {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: #f8fafc;
  border: 1px solid #dbe4f0;
  color: #475569;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}
.scores-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  padding: 30px 32px;
  border-radius: 32px;
  background:
    radial-gradient(circle at top right, rgba(45, 212, 191, 0.16), transparent 30%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(240, 253, 250, 0.92));
  border: 1px solid rgba(153, 246, 228, 0.6);
  box-shadow: 0 20px 44px rgba(15, 23, 42, 0.08);
}
.scores-hero__eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #0f766e;
}
.scores-hero__title {
  margin: 10px 0 0;
  font-size: 36px;
  line-height: 1.1;
  font-weight: 800;
  color: #191c22;
}
.scores-hero__subtitle {
  margin: 12px 0 0;
  max-width: 820px;
  color: #475569;
  line-height: 1.8;
}
.scores-hero__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.92fr);
  gap: 22px;
}
.overall-card {
  position: relative;
  overflow: hidden;
  min-height: 320px;
  padding: 30px;
  border-radius: 32px;
  background: linear-gradient(135deg, #0d9488, #14b8a6 58%, #5eead4);
  color: #fff;
  box-shadow: 0 24px 54px rgba(13, 148, 136, 0.28);
}
.overall-card__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 28px;
}
.overall-card__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}
.overall-card__label {
  font-size: 13px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.82);
}
.overall-card h2 {
  margin: 10px 0 0;
  font-size: 28px;
  font-weight: 800;
}
.overall-card__badge {
  align-self: flex-start;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(10px);
  font-size: 12px;
  font-weight: 700;
}
.overall-card__metric-row {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-end;
}
.overall-card__value {
  font-size: 58px;
  line-height: 1;
  font-weight: 800;
}
.overall-card__desc {
  margin: 14px 0 0;
  max-width: 540px;
  color: rgba(255, 255, 255, 0.84);
  line-height: 1.8;
}
.overall-card__trend {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(15, 23, 42, 0.14);
  color: rgba(255, 255, 255, 0.88);
  max-width: 240px;
  line-height: 1.6;
}
.overall-card__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.overall-stat {
  padding: 16px 18px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.18);
}
.overall-stat span {
  display: block;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.76);
}
.overall-stat strong {
  display: block;
  margin-top: 8px;
  font-size: 22px;
  font-weight: 800;
}
.overall-card__halo {
  position: absolute;
  right: -70px;
  bottom: -84px;
  width: 260px;
  height: 260px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.1);
}
.metric-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.metric-card {
  display: flex;
  gap: 16px;
  padding: 22px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(193, 198, 214, 0.42);
  box-shadow: 0 18px 40px rgba(25, 28, 34, 0.06);
}
.metric-card__icon {
  width: 46px;
  height: 46px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #d7e2ff;
  color: #0052ae;
}
.metric-card__icon--1 {
  background: #dcfce7;
  color: #15803d;
}
.metric-card__icon--2 {
  background: #fef3c7;
  color: #b45309;
}
.metric-card__label {
  font-size: 13px;
  color: #64748b;
}
.metric-card__value {
  margin-top: 6px;
  font-size: 28px;
  line-height: 1.1;
  font-weight: 800;
  color: #191c22;
}
.metric-card__desc {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.8;
}
.metric-card--progress {
  display: flex;
  flex-direction: column;
}
.metric-card__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.metric-card__pill {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}
.metric-card__desc--spacious {
  margin-bottom: 0;
}
.metric-card__bullets {
  margin: 14px 0 0;
  padding-left: 18px;
  color: #475569;
  font-size: 12px;
  line-height: 1.8;
}
.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.46fr) minmax(320px, 0.9fr);
  gap: 22px;
}
.workspace-main,
.workspace-side {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.scores-card,
.state-card {
  border: 1px solid rgba(193, 198, 214, 0.45);
  border-radius: 32px;
  box-shadow: 0 18px 40px rgba(25, 28, 34, 0.06);
}
.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
}
.card-header-row--tight {
  align-items: flex-start;
}
.card-header-row p {
  margin: 6px 0 0;
  color: #727785;
  font-size: 12px;
  line-height: 1.7;
}
.section-title {
  font-size: 18px;
  font-weight: 800;
  color: #191c22;
}
.header-meta {
  font-size: 12px;
  color: #0f766e;
  font-weight: 700;
}
.group-section-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.group-section {
  padding: 20px;
  border-radius: 28px;
  background: #fbfdff;
  border: 1px solid #e2e8f0;
}
.group-section__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.group-section__title-wrap {
  display: flex;
  align-items: center;
  gap: 14px;
}
.group-section__icon {
  width: 44px;
  height: 44px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.group-section__icon--lab {
  background: #ccfbf1;
  color: #0f766e;
}
.group-section__icon--homework {
  background: #dbeafe;
  color: #1d4ed8;
}
.group-section__icon--exam {
  background: #ffedd5;
  color: #c2410c;
}
.group-section h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #191c22;
}
.group-section__title-wrap p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
}
.group-section__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}
.group-section__badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 11px;
  font-weight: 700;
}
.group-section__meta strong {
  font-size: 28px;
  line-height: 1;
  font-weight: 800;
  color: #191c22;
}
.group-section__meta small {
  color: #64748b;
  font-size: 11px;
}
.record-table {
  overflow: hidden;
  border-radius: 22px;
  border: 1px solid #e2e8f0;
}
.record-table__head,
.record-table__row {
  display: grid;
  grid-template-columns: minmax(180px, 1.4fr) 0.65fr 1fr 0.7fr 0.65fr;
  gap: 14px;
  align-items: center;
}
.record-table__head {
  padding: 14px 18px;
  background: #f8fafc;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.record-table__row {
  padding: 16px 18px;
  border-top: 1px solid #eef2f7;
}
.record-table__cell {
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}
.record-table__cell--name strong {
  color: #191c22;
}
.record-table__cell--score {
  color: #0f766e;
  font-weight: 800;
}
.exam-record-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.exam-record-card {
  padding: 18px;
  border-radius: 24px;
  border: 1px solid #fed7aa;
  background: linear-gradient(180deg, #fff, #fff7ed);
}
.exam-record-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.exam-record-card h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  color: #191c22;
}
.exam-record-card p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
}
.score-pill {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 16px;
  font-size: 14px;
  font-weight: 800;
  color: #0f766e;
  background: #ccfbf1;
}
.score-pill--exam {
  color: #c2410c;
  background: #ffedd5;
}
.exam-record-card__footer {
  display: flex;
  gap: 8px;
  margin-top: 18px;
}
.exam-chip {
  background: #ffedd5;
  color: #c2410c;
}
.exam-chip--success {
  background: #dcfce7;
  color: #166534;
}
.recent-record-list,
.feedback-list,
.action-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.recent-record-item {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
  padding: 18px 20px;
  border-radius: 24px;
  background: #f8fafc;
}
.recent-record-item__type {
  display: inline-flex;
  align-items: center;
  padding: 5px 9px;
  border-radius: 999px;
  background: #e2e8f0;
  color: #475569;
  font-size: 11px;
  font-weight: 700;
}
.recent-record-item h3 {
  margin: 10px 0 0;
  font-size: 16px;
  font-weight: 800;
  color: #191c22;
}
.recent-record-item p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
}
.recent-record-item__score {
  flex-shrink: 0;
  font-size: 24px;
  font-weight: 800;
  color: #0f766e;
}
.feedback-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.feedback-hero {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 18px;
  border-radius: 24px;
  background: linear-gradient(135deg, #f0fdfa, #ecfeff);
}
.feedback-hero__avatar {
  width: 46px;
  height: 46px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ccfbf1;
  color: #0f766e;
  flex-shrink: 0;
}
.feedback-hero__label {
  font-size: 12px;
  font-weight: 700;
  color: #0f766e;
}
.feedback-hero h3 {
  margin: 8px 0 0;
  font-size: 18px;
  font-weight: 800;
  color: #191c22;
}
.feedback-hero p,
.feedback-item p,
.action-item p {
  margin: 8px 0 0;
  color: #64748b;
  line-height: 1.8;
  font-size: 13px;
}
.feedback-item,
.action-item {
  padding: 16px 18px;
  border-radius: 20px;
}
.feedback-item strong,
.action-item strong {
  color: #191c22;
  font-size: 14px;
}
.feedback-item--positive,
.action-item--positive {
  background: #ecfdf5;
}
.feedback-item--highlight,
.action-item--highlight {
  background: #eff6ff;
}
.feedback-item--warning,
.action-item--warning {
  background: #fff7ed;
}
@media (max-width: 1180px) {
  .overview-grid,
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .overall-card__stats {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 860px) {
  .scores-hero {
    flex-direction: column;
  }

  .scores-hero__actions {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .overall-card__metric-row,
  .group-section__header,
  .recent-record-item,
  .card-header-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .record-table__head {
    display: none;
  }

  .record-table__row {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .exam-record-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 640px) {
  .scores-hero,
  .overall-card,
  .metric-card,
  .group-section {
    padding: 20px;
    border-radius: 24px;
  }

  .scores-hero__title {
    font-size: 30px;
  }

  .overall-card__value {
    font-size: 44px;
  }
}
/* Student score refactor overrides */
.scores-page {
  gap: 16px;
}
.scores-header__eyebrow {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #0f766e;
  margin-bottom: 6px;
}
.scores-hero__title {
  margin: 0;
  font-size: 22px;
}
.scores-hero__subtitle {
  display: none;
}
.overview-grid,
.workspace-grid {
  gap: 10px;
}
.metric-stack,
.workspace-main,
.workspace-side,
.group-section-list,
.recent-record-list,
.feedback-list,
.action-list,
.feedback-panel {
  gap: 6px;
}
.overall-card,
.metric-card,
.scores-card,
.state-card,
.group-section,
.recent-record-item,
.feedback-item,
.action-item,
.exam-record-card,
.feedback-hero {
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(25, 28, 34, 0.05);
}
.overall-card {
  min-height: 0;
  padding: 10px 12px;
  box-shadow: 0 8px 20px rgba(13, 148, 136, 0.12);
}
.overview-grid--compact {
  grid-template-columns: minmax(0, 1.1fr) minmax(280px, 0.9fr);
  align-items: start;
}
.overall-card--compact {
  min-height: auto;
}
.overall-card__content {
  gap: 18px;
}
.overall-card__metric-row {
  align-items: flex-start;
}
.overall-card__value {
  font-size: 42px;
}
.overall-card__desc {
  margin-top: 8px;
  line-height: 1.5;
  max-width: none;
}
.overall-card__stats {
  gap: 10px;
}
.overall-card__stats--compact {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.overall-stat {
  padding: 10px 12px;
  border-radius: 14px;
}
.overall-card h2 {
  margin: 6px 0 0;
  font-size: 16px;
}
.overall-card__badge {
  padding: 5px 8px;
  font-size: 10px;
}
.metric-card__value {
  font-size: 24px;
}
.metric-card__desc {
  line-height: 1.55;
}
@media (max-width: 860px) {
  .overall-card__stats--compact {
    grid-template-columns: 1fr;
  }
}
.metric-stack--compact {
  gap: 10px;
}
.metric-card,
.group-section,
.recent-record-item,
.feedback-item,
.action-item,
.exam-record-card,
.feedback-hero {
  padding: 14px;
}
.overview-grid--dense {
  grid-template-columns: minmax(0, 1fr) minmax(260px, 0.82fr);
  align-items: start;
}
.overall-card--dense {
  min-height: 0;
  padding: 16px 18px;
}
.overall-card--plain {
  background: #ffffff;
  color: #191c22;
  border: 1px solid rgba(193, 198, 214, 0.45);
  box-shadow: 0 8px 24px rgba(25, 28, 34, 0.05);
}
.overall-card--dense .overall-card__content {
  gap: 12px;
}
.overall-card__metric-row--dense {
  display: block;
}
.overall-card__value-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.overall-card--dense .overall-card__value {
  font-size: 34px;
  color: #191c22;
}
.overall-card--dense h2 {
  font-size: 18px;
  color: #191c22;
}
.overall-card--plain .overall-card__label,
.overall-card--plain .overall-card__desc {
  color: #64748b;
}
.overall-card--plain .overall-card__badge {
  align-self: flex-start;
  justify-self: end;
  background: #ecfeff;
  color: #0f766e;
}
.overall-card--plain .overall-stat {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}
.overall-card--plain .overall-stat span {
  color: #64748b;
}
.metric-stack--grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
}
.metric-stack--grid .metric-card--progress {
  grid-column: 1 / -1;
}
.overall-card--dense .overall-card__stats--dense {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.overall-card--dense .overall-stat {
  padding: 8px 10px;
}
.overall-stat--compact {
  min-height: 0;
}
.overall-card--dense .overall-stat strong {
  margin-top: 4px;
  font-size: 16px;
}
.metric-card--compact {
  padding: 10px;
}
.metric-card--compact .metric-card__value {
  font-size: 18px;
}
.metric-card--compact .metric-card__desc {
  margin-top: 4px;
}
@media (max-width: 860px) {
  .overview-grid--dense,
  .overall-card--dense .overall-card__stats--dense,
  .metric-stack--grid {
    grid-template-columns: 1fr;
  }
}
.overview-grid--flat {
  grid-template-columns: minmax(0, 1fr) minmax(260px, 0.82fr);
  align-items: start;
}
.overall-card--flat {
  min-height: 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: #ffffff;
  color: #191c22;
  border: 1px solid rgba(193, 198, 214, 0.45);
  box-shadow: 0 8px 24px rgba(25, 28, 34, 0.05);
}
.overall-card__flat-topline {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}
.overall-card__flat-main {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
}
.overall-card__flat-score {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.overall-card__flat-score-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 700;
}
.overall-card__flat-score strong {
  font-size: 24px;
  line-height: 1;
  font-weight: 800;
  color: #191c22;
}
.overall-card__flat-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}
.overall-card__desc--flat {
  margin-top: 0;
  color: #64748b;
  line-height: 1.3;
  font-size: 11px;
}
.metric-stack--flat {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  align-items: start;
}
.metric-card {
  gap: 12px;
  padding: 10px;
  border-radius: 12px;
}
.metric-card__icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
}
.metric-card__label {
  font-size: 11px;
}
.metric-card__value {
  margin-top: 4px;
  font-size: 18px;
}
.metric-card__desc {
  margin-top: 2px;
  font-size: 10px;
  line-height: 1.3;
}
.metric-card--progress {
  gap: 8px;
}
.metric-card__pill {
  padding: 4px 8px;
  font-size: 10px;
}
.workspace-main,
.workspace-side {
  gap: 10px;
}
.scores-card,
.state-card {
  border-radius: 12px;
}
.card-header-row p {
  margin: 4px 0 0;
  font-size: 10px;
  line-height: 1.4;
}
.section-title {
  font-size: 15px;
}
.header-meta {
  font-size: 11px;
}
.group-section-list {
  gap: 10px;
}
.group-section {
  padding: 10px;
  border-radius: 12px;
}
.group-section__header {
  gap: 10px;
  margin-bottom: 8px;
}
.group-section__title-wrap {
  gap: 10px;
}
.group-section__icon {
  width: 30px;
  height: 30px;
  border-radius: 12px;
}
.group-section h3 {
  font-size: 14px;
}
.group-section__title-wrap p {
  margin: 4px 0 0;
  font-size: 11px;
}
.group-section__badge {
  padding: 4px 8px;
}
.group-section__meta strong {
  font-size: 22px;
}
.record-table {
  border-radius: 12px;
}
.record-table__head {
  padding: 8px 10px;
}
.record-table__row {
  padding: 8px 10px;
}
.record-table__cell {
  font-size: 12px;
  line-height: 1.45;
}
.exam-record-grid {
  gap: 6px;
}
.exam-record-card {
  padding: 10px;
  border-radius: 12px;
}
.exam-record-card h4 {
  font-size: 14px;
}
.exam-record-card p {
  margin: 4px 0 0;
  font-size: 11px;
}
.score-pill {
  padding: 6px 9px;
  border-radius: 12px;
  font-size: 11px;
}
.exam-record-card__footer {
  margin-top: 8px;
}
.recent-record-item {
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
}
.recent-record-item h3 {
  margin: 6px 0 0;
  font-size: 14px;
}
.recent-record-item p {
  margin: 4px 0 0;
  font-size: 11px;
}
.recent-record-item__score {
  font-size: 16px;
}
.feedback-panel {
  gap: 6px;
}
.feedback-hero {
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
}
.feedback-hero__avatar {
  width: 32px;
  height: 32px;
  border-radius: 12px;
}
.feedback-hero h3 {
  margin: 4px 0 0;
  font-size: 14px;
}
.feedback-hero p,
.feedback-item p,
.action-item p {
  margin: 4px 0 0;
  line-height: 1.45;
  font-size: 11px;
}
.feedback-item,
.action-item {
  padding: 8px 10px;
  border-radius: 12px;
}
.feedback-item strong,
.action-item strong {
  font-size: 11px;
}
.metric-stack--flat .metric-card--progress {
  grid-column: 1 / -1;
}
@media (max-width: 860px) {
  .overview-grid--flat,
  .overall-card__flat-main,
  .overall-card__flat-stats,
  .metric-stack--flat {
    grid-template-columns: 1fr;
  }
}
</style>

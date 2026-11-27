<template>
  <div class="lab-steps-page workbench-page workbench-page--locked" v-loading="loading">
    <div class="workbench-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <h2>{{ detail?.title || '实验作答' }}</h2>
          <p>{{ detail?.description || '按题目顺序作答。' }}</p>
          <div v-if="detail" class="workbench-meta">
            <span class="header-meta-chip">{{ submissionLabel(detail.submissionStatus) }}</span>
            <span class="header-meta-chip">{{ experimentTypeLabel(detail.experimentType) }}</span>
            <span v-if="showLabScore" class="header-meta-chip">
              总分 {{ detail.totalScore }}<template v-if="planTotalScore > 0"> / 计划满分 {{ planTotalScore }}</template>
            </span>
            <span class="header-meta-chip">开始：{{ detail.startAt ? formatDateTime(detail.startAt) : '不限' }}</span>
            <span class="header-meta-chip">截止：{{ detail.dueAt ? formatDateTime(detail.dueAt) : '不限' }}</span>
            <span class="header-meta-chip">{{ currentIndex + 1 }}/{{ labItems.length }}</span>
            <span class="header-meta-chip">{{ detail.summaryRequired ? '实验小结必填' : '实验小结选填' }}</span>
            <span class="header-meta-chip">{{ readonly ? '只读' : '可编辑' }}</span>
            <AppStatusTag v-if="isNotStarted" size="small" v-bind="resolveStatusOf('labTimeGate', 'NOT_STARTED')" />
            <AppStatusTag v-else-if="isOverdue" size="small" v-bind="resolveStatusOf('labTimeGate', 'OVERDUE')" />
          </div>
        </div>
        <div class="workbench-header__actions">
          <span v-if="submitHint" class="submit-hint">{{ submitHint }}</span>
          <el-button type="success" :loading="submitting" :disabled="!canSubmit" @click="submitAll">
            提交实验
          </el-button>
        </div>
      </div>
    </div>

    <AppEmptyState v-if="!loading && !detail" description="未获取到实验题项数据" />

    <template v-else-if="detail">
      <section class="lab-info-card">
        <header class="lab-info-card__header">
          <div>
            <h3>实验信息</h3>
            <p>{{ readonly ? '查看实验要求、内容与教师反馈。' : '先阅读实验要求与实验内容，再进入题目作答。' }}</p>
          </div>
          <el-button
            type="primary"
            link
            size="small"
            class="lab-info-card__toggle"
            :aria-expanded="infoExpanded ? 'true' : 'false'"
            @click="toggleInfoExpanded"
          >
            {{ infoExpanded ? '收起' : '展开详情' }}
            <span class="material-symbols-outlined lab-info-card__chevron">{{ infoExpanded ? 'expand_less' : 'expand_more' }}</span>
          </el-button>
        </header>

        <div v-show="infoExpanded" class="lab-info-grid">
          <template v-if="hasLabIntro">
            <article v-if="detail.materialFileName" class="lab-info-tile">
              <div class="lab-info-section__title">
                <span class="material-symbols-outlined">description</span>实验指导
              </div>
              <div class="lab-info-guidance">
                <span>{{ detail.materialFileName }}</span>
                <el-button v-if="hasGuidanceFile" type="primary" plain size="small" @click="downloadGuidance">下载实验指导</el-button>
              </div>
            </article>
            <article v-if="detail.description" class="lab-info-tile">
              <div class="lab-info-section__title">
                <span class="material-symbols-outlined">fact_check</span>实验要求
              </div>
              <p>{{ detail.description }}</p>
            </article>
            <article v-if="detail.experimentContent" class="lab-info-tile lab-info-section--wide">
              <div class="lab-info-section__title">
                <span class="material-symbols-outlined">article</span>实验内容
              </div>
              <p>{{ detail.experimentContent }}</p>
            </article>
          </template>
          <div v-else class="lab-info-empty lab-info-section--wide">
            <span class="material-symbols-outlined">description</span>
            <span>本实验暂未提供实验指导、要求与内容。</span>
          </div>
          <div v-if="showLabFeedback" class="lab-info-comment lab-info-section--wide">
            <div class="lab-info-comment__title">教师总评</div>
            <p>{{ detail.teacherComment || '当前尚未开放教师总评。' }}</p>
          </div>
        </div>
      </section>

      <section class="steps-layout">
        <aside class="steps-sidebar">
          <article class="sidebar-card status-card">
            <div class="status-card__header">
              <div>
                <span class="status-card__eyebrow">当前状态</span>
                <strong>{{ submissionLabel(detail.submissionStatus) }}</strong>
              </div>
              <AppStatusTag v-bind="resolveStatusOf('labEditMode', readonly ? 'READONLY' : 'EDITABLE')" />
            </div>
            <div class="status-card__meta">
              <span>{{ labItems.length }} 个题项</span>
              <span>{{ detail.summaryRequired ? '实验小结必填' : '实验小结选填' }}</span>
            </div>
            <div class="progress-panel">
              <div class="progress-panel__header">
                <span>已保存进度</span>
                <span>{{ progressPercent }}%</span>
              </div>
              <div class="progress-track"><span class="progress-fill" :style="{ width: `${progressPercent}%` }"></span></div><!-- 动态值：按分步作答进度计算宽度 -->
              <p>{{ progressText }}</p>
            </div>
          </article>

          <article class="sidebar-card workflow-card">
            <div class="workflow-card__header">
              <div>
                <h3>题项导航</h3>
                <p>切换题目或提交前，请先保存当前内容。</p>
              </div>
            </div>
            <div class="workflow-list">
              <button
                v-for="(item, index) in labItems"
                :key="item.id"
                type="button"
                class="workflow-item"
                :class="{
                  'workflow-item--active': index === currentIndex,
                  'workflow-item--done': hasSavedAnswer(item),
                  'workflow-item--dirty': isItemDirty(item),
                }"
                @click="selectItem(index)"
              >
                <div class="workflow-item__badge">{{ item.stepNo }}</div>
                <div class="workflow-item__body">
                  <span class="workflow-item__eyebrow">{{ answerStateLabel(item) }}</span>
                  <strong>{{ item.title }}</strong>
                  <small>
                    {{ questionTypeLabel(item.questionType) }} · {{ item.stepScore }} 分
                    <template v-if="showLabScore && item.score !== null && item.score !== undefined">
                      · 得分 {{ item.score }}<template v-if="item.stepScore > 0">/{{ item.stepScore }}</template>
                    </template>
                  </small>
                </div>
              </button>
            </div>
          </article>
        </aside>

        <div class="steps-main">
          <div class="steps-main__scroll">
            <section v-if="currentItem" class="question-focus-strip">
              <div class="question-focus-strip__main">
                <span class="question-focus-strip__eyebrow">当前题目</span>
                <strong>第 {{ currentItem.stepNo }} 题 · {{ currentItem.title }}</strong>
                <small>{{ questionTypeLabel(currentItem.questionType) }} · {{ currentItem.stepScore }} 分</small>
              </div>
              <div class="question-focus-strip__status">
                <span class="focus-state-pill" :class="focusStateClass">{{ currentSaveStateLabel }}</span>
              </div>
            </section>

            <section v-if="currentItem" class="question-card">
              <header class="question-card__header">
                <div>
                  <div class="question-card__eyebrow">第 {{ currentItem.stepNo }} 题 / {{ questionTypeLabel(currentItem.questionType) }}</div>
                  <h3>{{ currentItem.title }}</h3>
                </div>
                <div class="question-card__score">{{ currentItem.stepScore }} 分</div>
              </header>

              <div class="question-card__body" @paste="onAnswerPaste">
                <section class="question-card__prompt" :class="{ 'question-card__prompt--embedded': currentQuestionType === 'FILL_BLANK' }">
                  <div class="pane-title">题目</div>
                  <FillBlankInlineEditor
                    v-if="currentQuestionType === 'FILL_BLANK'"
                    :model-value="currentDraft"
                    :prompt="currentItem.content"
                    :blanks="currentItem.blanks"
                    :disabled="readonly"
                    @update:model-value="updateCurrentDraft"
                  />
                  <div v-else class="prompt-text">{{ currentItem.content }}</div>
                </section>

                <section v-if="currentItem.teacherComment || currentItem.score !== null && currentItem.score !== undefined" class="feedback-card">
                  <div class="pane-title">当前反馈</div>
                  <p>{{ itemFeedback(currentItem) }}</p>
                </section>

                <QuestionAnswerShell
                  v-if="currentQuestionType !== 'FILL_BLANK'"
                  title="作答区"
                  :hint="answerHint(currentItem.questionType)"
                  :chip="currentItemCodeLanguage ? `${questionTypeLabel(currentItem.questionType)} · ${currentItemCodeLanguage}` : questionTypeLabel(currentItem.questionType)"
                >
                  <template #callout>
                    <div v-if="currentQuestionType === 'CODE'" class="code-callout">
                      <div class="code-callout__title">代码作答</div>
                      <p>请在下方输入代码内容。</p>
                    </div>
                  </template>

                  <ChoiceAnswerEditor
                    v-if="currentQuestionType === 'SINGLE_CHOICE'"
                    :model-value="currentDraft"
                    :options="currentItem.options"
                    mode="single"
                    :disabled="readonly"
                    @update:model-value="updateCurrentDraft"
                  />

                  <ChoiceAnswerEditor
                    v-else-if="currentQuestionType === 'MULTIPLE_CHOICE'"
                    :model-value="currentDraft"
                    :options="currentItem.options"
                    mode="multiple"
                    :disabled="readonly"
                    @update:model-value="updateCurrentDraft"
                  />

                  <ChoiceAnswerEditor
                    v-else-if="currentQuestionType === 'TRUE_FALSE'"
                    :model-value="currentDraft"
                    :options="currentItem.options"
                    mode="judge"
                    :disabled="readonly"
                    @update:model-value="updateCurrentDraft"
                  />

                  <CodeLabAnswerEditor
                    v-else-if="currentQuestionType === 'CODE'"
                    :model-value="currentDraft"
                    :language="currentItemCodeLanguage"
                    :rows="16"
                    :maxlength="20000"
                    :disabled="readonly"
                    placeholder="// 在此输入代码答案"
                    @update:model-value="updateCurrentDraft"
                  />

                  <TextAnswerEditor
                    v-else-if="currentQuestionType === 'SHORT_ANSWER'"
                    :model-value="currentDraft"
                    :lab-id="labId"
                    :step-id="currentItem.id"
                    enable-images
                    :rows="7"
                    :maxlength="3000"
                    :disabled="readonly"
                    placeholder="请输入简答内容"
                    @update:model-value="updateCurrentDraft"
                    @images-removed="syncImagesAfterRemove"
                  />

                  <TextAnswerEditor
                    v-else
                    :model-value="currentDraft"
                    :lab-id="labId"
                    :step-id="currentItem.id"
                    enable-images
                    :rows="12"
                    :maxlength="5000"
                    :disabled="readonly"
                    placeholder="请输入本题作答内容"
                    @update:model-value="updateCurrentDraft"
                    @images-removed="syncImagesAfterRemove"
                  />
                </QuestionAnswerShell>
              </div>
            </section>

            <section class="summary-card">
              <div class="summary-card__header">
                <div>
                  <div class="summary-card__eyebrow">实验小结</div>
                  <h3>实验小结</h3>
                </div>
                <AppStatusTag v-bind="resolveStatusOf('labSummaryRequirement', detail.summaryRequired ? 'REQUIRED' : 'OPTIONAL')" />
              </div>
              <p class="summary-card__desc">
                {{ detail.summaryRequired ? '提交前需填写实验小结。' : '可在提交前补充实验小结。' }}
              </p>
              <div v-if="detail.summaryRequired && !summaryDraft.trim() && !readonly" class="summary-required-tip">
                请在提交前补充实验小结。
              </div>
              <el-input
                v-model="summaryDraft"
                type="textarea"
                :rows="6"
                maxlength="5000"
                show-word-limit
                :disabled="readonly"
                placeholder="请输入实验小结、结论或问题分析"
              />
            </section>
          </div>

          <footer class="sticky-footer">
            <div class="sticky-footer__status">
              <span class="material-symbols-outlined">check_circle</span>
              <span>{{ footerStatusText }}</span>
            </div>
            <div class="sticky-footer__actions">
              <el-button :disabled="currentIndex === 0" @click="goPrev">上一题</el-button>
              <el-button :disabled="currentIndex === labItems.length - 1" @click="goNext">下一题</el-button>
              <el-button type="primary" :loading="saving" :disabled="readonly || !currentItem" @click="saveCurrent">保存当前题项</el-button>
            </div>
          </footer>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { inject } from 'vue';
import AppEmptyState from '@/components/ui/AppEmptyState.vue';
import { StudentLabStepsKey } from '@/composables/useStudentLabSteps';
import { formatDateTime } from '@/utils/format';
import AppStatusTag from '@/components/ui/AppStatusTag.vue';
import { resolveStatusOf } from '@/components/ui/statusPresentation';
const { QuestionAnswerShell, ChoiceAnswerEditor, FillBlankInlineEditor, TextAnswerEditor, CodeLabAnswerEditor, loading, saving, submitting, detail, currentIndex, summaryDraft, isNotStarted, isOverdue, labId, labItems, currentItem, currentDraft, currentQuestionType, currentItemCodeLanguage, readonly, canSubmit, submitHint, progressPercent, progressText, currentSaveStateLabel, focusStateClass, footerStatusText, updateCurrentDraft, onAnswerPaste, syncImagesAfterRemove, selectItem, hasSavedAnswer, isItemDirty, answerStateLabel, saveCurrent, goPrev, goNext, submitAll, submissionLabel, questionTypeLabel, answerHint, itemFeedback, showLabScore, planTotalScore, showLabFeedback, hasGuidanceFile, experimentTypeLabel, downloadGuidance, hasLabIntro, infoExpanded, toggleInfoExpanded } = inject(StudentLabStepsKey)!;
</script>

<style scoped>
.lab-steps-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}
.submit-hint {
  align-self: center;
  color: var(--el-text-color-secondary);
  font-size: var(--font-size-sm);
  margin-right: 8px;
}
.header-meta-chip {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: var(--color-surface);
  color: #334155;
  font-size: var(--font-size-sm);
  font-weight: 600;
}
/* 实验信息卡默认收起为单行摘要条；展开态仍限高并内部滚动，避免挤压作答区 */
.lab-info-card {
  padding: 12px 18px;
  flex-shrink: 0;
  max-height: 38vh;
  overflow-y: auto;
}
.lab-info-card__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.lab-info-card__header h3 {
  margin: 0;
  font-size: var(--font-size-lg);
}
.lab-info-card__header p {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-base);
}
.lab-info-card__toggle {
  height: auto;
  padding: 0;
}
.lab-info-card__toggle .material-symbols-outlined {
  font-size: var(--font-size-lg);
  margin-left: 2px;
}
.lab-info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
  margin-top: 12px;
}
.lab-info-section {
  margin-top: 0;
}
.lab-info-section--wide {
  grid-column: 1 / -1;
}
.lab-info-tile {
  padding: 12px 14px;
  border-radius: var(--radius-lg);
  background: var(--color-bg-app);
}
.lab-info-tile .lab-info-section__title {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
}
.lab-info-tile .material-symbols-outlined {
  font-size: var(--font-size-lg);
}
.lab-info-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  border-radius: var(--radius-lg);
  background: var(--color-bg-app);
  color: var(--color-text-muted);
}
.lab-info-empty .material-symbols-outlined {
  font-size: var(--font-size-xl);
}
.lab-info-section__title {
  margin-bottom: 6px;
  color: var(--color-action-primary);
  font-size: var(--font-size-sm);
  font-weight: 700;
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
}
.lab-info-section p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: var(--line-height-body);
  white-space: pre-wrap;
}
.lab-info-guidance {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.lab-info-comment {
  border-left: 3px solid var(--color-brand-student-border);
  padding-left: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 0;
}
.lab-info-comment__title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-text-secondary);
}
.status-card__eyebrow,
.workflow-item__eyebrow,
.question-card__eyebrow,
.summary-card__eyebrow {
  font-size: var(--font-size-sm);
  font-weight: 700;
  letter-spacing: var(--letter-spacing-caps);
  text-transform: uppercase;
}
.steps-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 880px);
  gap: 18px;
  align-items: stretch;
  justify-content: center;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  height: 100%;
}
.steps-sidebar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
.workflow-card {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.workflow-card__header {
  flex-shrink: 0;
}
.sidebar-card,
.lab-info-card,
.question-focus-strip,
.question-card,
.summary-card,
.sticky-footer {
  border: 1px solid var(--color-border-soft);
  border-radius: 16px;
  background: var(--color-surface);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}
.sidebar-card,
.summary-card {
  padding: 16px;
}
.status-card__header,
.summary-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.status-card__eyebrow,
.workflow-item__eyebrow,
.question-card__eyebrow,
.summary-card__eyebrow {
  color: var(--color-action-primary);
}
.status-card strong,
.workflow-item strong,
.question-card__score {
  color: var(--color-text-primary);
}
.status-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 600;
}
.status-card__meta span {
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--color-bg-app);
}
.progress-panel {
  margin-top: 14px;
}
.progress-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 600;
}
.progress-track {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-border);
}
.progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--color-action-primary), #60a5fa);
}
.progress-panel p {
  margin: 8px 0 0;
  font-size: var(--font-size-sm);
}
.workflow-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: auto;
  padding-right: 4px;
  overscroll-behavior: contain;
}
.workflow-item {
  display: flex;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border-soft);
  border-radius: 12px;
  background: var(--color-bg-app);
  text-align: left;
  cursor: pointer;
}
.workflow-item__badge {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--color-border);
  color: var(--color-text-secondary);
  font-weight: 700;
}
.workflow-item__body {
  min-width: 0;
}
.workflow-item__body strong {
  display: block;
  margin-top: 2px;
  font-size: var(--font-size-md);
}
.workflow-item__body small {
  display: block;
  margin-top: 4px;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}
.workflow-item--active {
  border-color: #93c5fd;
  background: var(--color-brand-student-soft);
}
.workflow-item--active .workflow-item__badge {
  background: var(--color-action-primary);
  color: var(--color-text-inverse);
}
.workflow-item--done .workflow-item__badge {
  background: var(--color-success-soft);
  color: #15803d;
}
.workflow-item--dirty {
  border-color: #fbbf24;
  background: #fffbeb;
}
.steps-main {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
.steps-main__scroll {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
  overflow: auto;
  padding-right: 4px;
  overscroll-behavior: contain;
}
.question-focus-strip {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.95), rgba(255, 255, 255, 1));
}
.question-focus-strip__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.question-focus-strip__eyebrow {
  color: var(--color-action-primary);
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: var(--letter-spacing-caps);
  text-transform: uppercase;
}
.question-focus-strip__main strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}
.question-focus-strip__main small,
.question-focus-strip__status {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.question-focus-strip__status {
  display: flex;
  align-items: center;
}
.focus-state-pill {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--color-brand-student-soft);
  color: var(--color-action-primary);
  font-size: var(--font-size-sm);
  font-weight: 700;
}
.focus-state-pill--saved {
  background: var(--color-success-soft);
  color: #15803d;
}
.focus-state-pill--saving,
.focus-state-pill--dirty {
  background: #fffbeb;
  color: #b45309;
}
.focus-state-pill--error {
  background: var(--color-danger-soft);
  color: #b91c1c;
}
.focus-state-pill--readonly,
.focus-state-pill--idle {
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
}
.question-card {
  padding: 18px 20px;
}
.question-card__header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border-soft);
}.question-card__score {
  padding: 6px 10px;
  border-radius: 12px;
  background: var(--color-brand-student-soft);
  font-size: var(--font-size-md);
  font-weight: 700;
}
.question-card__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 14px;
}
.question-card__prompt,
.feedback-card,
.answer-card {
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--color-bg-app);
}
.question-card__prompt--embedded {
  line-height: var(--line-height-body);
}
.pane-title {
  margin-bottom: 8px;
  color: var(--color-text-primary);
  font-weight: 700;
}
.prompt-text {
  white-space: pre-wrap;
  line-height: var(--line-height-body);
  color: var(--color-text-primary);
}
.answer-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}
.answer-card__hint {
  margin: 0;
}
.save-state-banner--saved {
  border-color: #86efac;
  background: #f0fdf4;
  color: #166534;
}
.save-state-banner--saving,
.save-state-banner--dirty {
  border-color: #fcd34d;
  background: #fffbeb;
  color: #92400e;
}
.save-state-banner--error {
  border-color: #fca5a5;
  background: var(--color-danger-soft);
  color: #b91c1c;
}
.save-state-banner--readonly,
.save-state-banner--idle {
  border-color: var(--color-border-strong);
  background: var(--color-bg-app);
  color: var(--color-text-secondary);
}
.code-callout,
.summary-required-tip {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 16px;
  font-size: var(--font-size-sm);
}
.code-callout {
  background: var(--color-brand-student-soft);
  border: 1px solid #bfdbfe;
}
.code-callout__title {
  margin-bottom: 6px;
  color: var(--color-brand-student);
  font-weight: 700;
}
.summary-required-tip {
  background: var(--color-danger-soft);
  border: 1px solid #fecaca;
  color: #b91c1c;
}
.sticky-footer {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(14px);
  flex-shrink: 0;
}
.sticky-footer__status {
  display: inline-flex;
  gap: 10px;
  align-items: center;
}
.sticky-footer__status .material-symbols-outlined {
  color: var(--color-success);
}
.sticky-footer__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}
@media (max-width: 640px) {
  .lab-info-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 1280px) {
  .lab-steps-page {
    height: auto;
    overflow: visible;
    padding-bottom: 96px;
  }

  .steps-layout {
    grid-template-columns: 1fr;
    justify-content: stretch;
    overflow: visible;
  }

  .steps-sidebar {
    overflow: visible;
  }

  .workflow-list,
  .steps-main,
  .steps-main__scroll {
    overflow: visible;
  }
}
</style>

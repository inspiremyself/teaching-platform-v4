<template>
  <div class="student-homework-detail" v-loading="loading">
    <el-empty v-if="!loading && !detail" description="未获取到作业详情" />

    <template v-else-if="detail">
      <section class="detail-hero">
        <div class="detail-hero__main">
          <div class="detail-hero__topline">
            <button type="button" class="back-button" @click="router.back()">
              <span class="material-symbols-outlined">arrow_back</span>
              <span>返回</span>
            </button>
            <div class="detail-hero__actions">
              <el-button
                round
                :disabled="readonly || (detail?.submissionStatus !== SUBMISSION_STATUS.SAVED && !lastSavedDraft)"
                @click="restoreSavedDraft"
              >
                重置为已保存内容
              </el-button>
              <el-button type="primary" round :loading="submitting" :disabled="readonly" @click="submitForm">提交作业</el-button>
            </div>
          </div>

          <div class="detail-hero__body">
            <div class="detail-hero__title-block">
              <h1>{{ detail.title }}</h1>
            </div>

            <div class="detail-hero__chips">
              <span class="detail-chip">
                <span class="material-symbols-outlined">school</span>
                {{ detail.className || '-' }}
              </span>
              <span class="detail-chip">
                <span class="material-symbols-outlined">event</span>
                截止：{{ formatDateTime(detail.dueAt) }}
              </span>
              <span class="detail-chip detail-chip--status">
                <span class="material-symbols-outlined">task_alt</span>
                {{ submissionStatusLabel(detail.submissionStatus) }}
              </span>
              <span v-if="autoSaveStatus !== 'idle'" class="detail-chip detail-chip--status">
                <span class="material-symbols-outlined">{{ autoSaveStatus === 'failed' ? 'error' : autoSaveStatus === 'saving' ? 'sync' : 'cloud_done' }}</span>
                {{ autoSaveStatus === 'saving' ? '保存中…' : autoSaveStatus === 'saved' ? '已保存草稿' : '草稿保存失败' }}
              </span>
            </div>
          </div>
        </div>
      </section>

      <section class="detail-workspace">
        <div v-if="hasQuestionItems" class="detail-nav-column">
          <article class="detail-card detail-card--nav">
            <div class="detail-card__header detail-card__header--stacked">
              <div>
                <span class="detail-card__eyebrow">题目导航</span>
                <h2>作业题目导航</h2>
              </div>
              <p class="detail-card__muted">切换题目并查看当前完成状态。</p>
            </div>

            <div class="progress-panel">
              <div class="progress-panel__header">
                <span>已完成进度</span>
                <span>{{ homeworkProgressPercent }}%</span>
              </div>
              <div class="progress-track">
                <span class="progress-fill" :style="{ width: `${homeworkProgressPercent}%` }"></span>
              </div>
              <p>{{ answeredQuestionCount }}/{{ detail.questions?.length || 0 }} 题已填写</p>
            </div>

            <div class="homework-question-wheel">
              <button
                v-for="(question, index) in detail.questions"
                :key="question.id"
                type="button"
                class="homework-question-wheel__item"
                :class="{
                  'is-active': currentQuestionIndex === index,
                  'is-done': isQuestionAnswered(question),
                }"
                @click="currentQuestionIndex = index"
              >
                <span class="homework-question-wheel__badge">{{ question.sortOrder }}</span>
                <span class="homework-question-wheel__body">
                  <span class="homework-question-wheel__state">
                    {{ isQuestionAnswered(question) ? '已填写' : '待作答' }}
                  </span>
                  <strong>第{{ question.sortOrder }}题</strong>
                  <small>{{ questionTypeLabel(question.type) }} · {{ question.score }} 分</small>
                </span>
              </button>
            </div>
          </article>
        </div>

        <div class="detail-main-column">
          <article class="detail-card">
            <div class="detail-card__header">
              <div>
                <span class="detail-card__eyebrow">作业说明</span>
                <h2>作业说明</h2>
              </div>
            </div>

            <p class="detail-card__paragraph">{{ detail.description || '当前作业暂无额外说明。' }}</p>

            <div v-if="detail.attachmentPath" class="attachment-note">
              <span class="material-symbols-outlined">attach_file</span>
              <div>
                <strong>教师提供的附件信息</strong>
                <p>{{ detail.attachmentPath }}</p>
              </div>
            </div>
          </article>

          <article class="detail-card">
            <div class="detail-card__header">
              <div>
                <span class="detail-card__eyebrow">作答区</span>
                <h2>{{ hasQuestionItems ? '逐题作答' : '在线作答' }}</h2>
              </div>
              <span class="detail-card__tip">{{ hasQuestionItems ? '填写后统一提交' : '完成作答后提交' }}</span>
            </div>

            <QuestionAnswerShell
              v-if="!hasQuestionItems"
              title="作业答案"
              hint="完成整份在线作答内容后统一提交。"
              chip="文本作答"
            >
              <TextAnswerEditor
                :model-value="textHomeworkDraft"
                :rows="16"
                :maxlength="20000"
                :disabled="readonly"
                placeholder="请输入整份文本作业内容"
                @update:model-value="updateTextHomeworkDraft"
              />
            </QuestionAnswerShell>

            <div v-else class="homework-question-list">
              <article v-if="currentQuestion" class="result-question-card">
                <div class="result-question-card__header">
                  <div>
                    <span class="result-question-card__badge">第{{ currentQuestion.sortOrder }}题</span>
                    <h2>{{ currentQuestion.stem }}</h2>
                  </div>
                  <span class="result-question-card__score">{{ currentQuestion.score }} 分</span>
                </div>

                <section
                  v-if="currentQuestionType === 'FILL_BLANK'"
                  class="homework-question-prompt"
                >
                  <div class="homework-question-prompt__label">题目内容</div>
                  <FillBlankInlineEditor
                    :model-value="currentQuestionDraft"
                    :prompt="currentQuestion.stem"
                    :disabled="readonly"
                    @update:model-value="updateCurrentQuestionDraft"
                  />
                </section>

                <QuestionAnswerShell
                  v-if="currentQuestion && currentQuestionType !== 'FILL_BLANK'"
                  title="作答区"
                  :hint="answerHint(currentQuestion.type)"
                  :chip="questionTypeLabel(currentQuestion.type)"
                >
                  <template #callout>
                    <div v-if="currentQuestionType === 'CODE'" class="code-callout">
                      <div class="code-callout__title">代码作答</div>
                      <p>请按照实验答题模式输入代码内容，提交时会同时保存兼容文本和结构化答案。</p>
                    </div>
                  </template>

                  <ChoiceAnswerEditor
                    v-if="currentQuestionType === 'SINGLE_CHOICE'"
                    :model-value="currentQuestionDraft"
                    :options="currentQuestionOptions"
                    mode="single"
                    :disabled="readonly"
                    @update:model-value="updateCurrentQuestionDraft"
                  />

                  <ChoiceAnswerEditor
                    v-else-if="currentQuestionType === 'MULTIPLE_CHOICE'"
                    :model-value="currentQuestionDraft"
                    :options="currentQuestionOptions"
                    mode="multiple"
                    :disabled="readonly"
                    @update:model-value="updateCurrentQuestionDraft"
                  />

                  <ChoiceAnswerEditor
                    v-else-if="currentQuestionType === 'TRUE_FALSE'"
                    :model-value="currentQuestionDraft"
                    :options="currentQuestionOptions"
                    mode="judge"
                    :disabled="readonly"
                    @update:model-value="updateCurrentQuestionDraft"
                  />

                  <CodeLabAnswerEditor
                    v-else-if="currentQuestionType === 'CODE'"
                    :model-value="currentQuestionDraft"
                    language="CODE"
                    :rows="14"
                    :maxlength="20000"
                    :disabled="readonly"
                    placeholder="// 请输入该题代码答案"
                    @update:model-value="updateCurrentQuestionDraft"
                  />

                  <TextAnswerEditor
                    v-else-if="currentQuestionType === 'SHORT_ANSWER'"
                    :model-value="currentQuestionDraft"
                    :rows="8"
                    :maxlength="5000"
                    :disabled="readonly"
                    placeholder="请输入该题答案"
                    @update:model-value="updateCurrentQuestionDraft"
                  />

                  <TextAnswerEditor
                    v-else
                    :model-value="currentQuestionDraft"
                    :rows="10"
                    :maxlength="5000"
                    :disabled="readonly"
                    placeholder="请输入该题答案"
                    @update:model-value="updateCurrentQuestionDraft"
                  />
                </QuestionAnswerShell>

                <div
                  v-if="showQuestionFeedback(currentQuestion)"
                  class="homework-question-feedback"
                >
                  <div class="homework-question-feedback__meta">
                    <span v-if="currentQuestion.answer?.score !== null && currentQuestion.answer?.score !== undefined">
                      得分 {{ currentQuestion.answer?.score }}/{{ currentQuestion.score }}
                    </span>
                    <span v-if="currentQuestion.answer?.teacherComment">教师已给出评语</span>
                  </div>
                  <p>{{ currentQuestion.answer?.teacherComment || '当前题目暂无教师评语。' }}</p>
                </div>
              </article>
            </div>
          </article>
        </div>

        <aside class="detail-side-column">
          <article class="detail-card detail-card--meta">
            <div class="detail-meta-grid">
              <div class="detail-meta-item">
                <span>开始时间</span>
                <strong>{{ formatDateTime(detail.startAt) }}</strong>
              </div>
              <div class="detail-meta-item">
                <span>截止时间</span>
                <strong>{{ formatDateTime(detail.dueAt) }}</strong>
              </div>
              <div class="detail-meta-item">
                <span>提交状态</span>
                <strong>{{ submissionStatusLabel(detail.submissionStatus) }}</strong>
              </div>
              <div class="detail-meta-item">
                <span>查重率</span>
                <strong>{{ formatRate(detail.plagiarismRate) }}</strong>
              </div>
              <div v-if="showHomeworkResult" class="detail-meta-item detail-meta-item--accent">
                <span>总分</span>
                <strong>{{ detail.totalScore ?? '-' }}</strong>
              </div>
            </div>
          </article>

          <article class="detail-card detail-card--dark">
            <span class="detail-card__eyebrow detail-card__eyebrow--light">剩余时间</span>
            <h2>{{ countdownLabel }}</h2>
            <p>{{ isNotStarted ? '作业尚未开始' : isOverdue ? '作业已截止' : readonly ? '当前为结果查看状态。' : '截止前确认作答后提交。' }}</p>
          </article>

          <article class="detail-card">
            <div class="detail-card__header">
              <div>
                <span class="detail-card__eyebrow">附件信息</span>
                <h2>附件信息</h2>
              </div>
            </div>

            <el-input
              v-model="form.attachmentPath"
              :disabled="readonly"
                placeholder="如需补充附件，请填写附件位置或说明后再提交"
              />

            <p class="detail-card__muted">需要附件时再补充说明。</p>
          </article>

          <article class="detail-card">
            <div class="detail-card__header">
              <div>
                <span class="detail-card__eyebrow">提交清单</span>
                <h2>提交清单</h2>
              </div>
            </div>

            <ul class="check-list">
                <li :class="{ 'check-list__item--done': hasQuestionItems ? answeredQuestionCount > 0 : !!(form.answerText || '').trim() }">
                  <span class="material-symbols-outlined">check_circle</span>
                  {{ hasQuestionItems ? `已填写 ${answeredQuestionCount}/${detail.questions?.length || 0} 题` : '已填写文本作答' }}
                </li>
              <li :class="{ 'check-list__item--done': !detail.attachmentPath || !!form.attachmentPath?.trim() }">
                <span class="material-symbols-outlined">check_circle</span>
                已确认附件信息
              </li>
              <li :class="{ 'check-list__item--done': detail.submissionStatus === SUBMISSION_STATUS.SUBMITTED || detail.submissionStatus === SUBMISSION_STATUS.GRADED }">
                <span class="material-symbols-outlined">check_circle</span>
                已进入正式提交状态
              </li>
            </ul>
          </article>

          <article class="detail-card" v-if="showHomeworkResult">
            <div class="detail-card__header">
              <div>
                <span class="detail-card__eyebrow">反馈回看</span>
                <h2>反馈回看</h2>
              </div>
            </div>

            <div class="feedback-stack">
              <div class="feedback-pill">
                <span>总分</span>
                <strong>{{ detail.totalScore ?? '-' }}</strong>
              </div>
              <div class="feedback-pill feedback-pill--blue">
                <span>查重率</span>
                <strong>{{ formatRate(detail.plagiarismRate) }}</strong>
              </div>
            </div>

            <p class="detail-card__paragraph detail-card__paragraph--compact">{{ detail.teacherComment || '当前尚无教师评语。' }}</p>
          </article>
        </aside>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { inject } from 'vue';
import { HomeworkDetailKey } from '@/composables/useHomeworkDetail';
const { SUBMISSION_STATUS, QuestionAnswerShell, ChoiceAnswerEditor, FillBlankInlineEditor, TextAnswerEditor, CodeLabAnswerEditor, router, loading, submitting, detail, currentQuestionIndex, form, autoSaveStatus, lastSavedDraft, isOverdue, isNotStarted, readonly, hasQuestionItems, currentQuestion, currentQuestionDraft, currentQuestionType, currentQuestionOptions, textHomeworkDraft, answeredQuestionCount, homeworkProgressPercent, showHomeworkResult, countdownLabel, submitForm, restoreSavedDraft, submissionStatusLabel, isQuestionAnswered, updateCurrentQuestionDraft, updateTextHomeworkDraft, showQuestionFeedback, answerHint, questionTypeLabel, formatRate, formatDateTime } = inject(HomeworkDetailKey)!;
</script>

<style scoped>
.student-homework-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-hero {
  padding: 14px 16px;
  border-radius: 16px;
  background:
    radial-gradient(circle at top right, rgba(191, 219, 254, 0.28), transparent 24%),
    linear-gradient(135deg, rgba(255,255,255,0.96), rgba(248,250,252,0.96));
  border: 1px solid rgba(191, 219, 254, 0.72);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
}

.detail-hero__main {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-hero__topline {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.back-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  border: none;
  border-radius: 999px;
  background: #ffffff;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.detail-hero__actions {
  display: flex;
  gap: 6px;
}

.detail-hero__actions :deep(.el-button) {
  min-height: 30px;
  padding-inline: 12px;
}

.detail-hero__body {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.detail-hero__title-block {
  min-width: 0;
}

.detail-hero h1 {
  margin: 0;
  font-size: 22px;
  color: #191c22;
}

.detail-hero__desc {
  margin: 0;
  max-width: 680px;
  color: #475569;
  font-size: 12px;
  line-height: 1.35;
}

.detail-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.detail-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 9px;
  border-radius: 999px;
  background: #ffffff;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.detail-chip--status {
  background: #dbeafe;
  color: #1d4ed8;
}

.info-card__label,
.detail-card__eyebrow {
  display: block;
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #94a3b8;
}

.detail-card__eyebrow--light {
  color: rgba(191, 219, 254, 0.78);
}

.detail-workspace {
  display: grid;
  grid-template-columns: minmax(220px, 0.72fr) minmax(0, 1.48fr) minmax(220px, 0.62fr);
  gap: 10px;
}

.detail-nav-column,
.detail-main-column,
.detail-side-column {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.04);
}

.detail-card--meta {
  padding: 10px;
}

.detail-card--nav {
  position: sticky;
  top: 12px;
}

.detail-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.detail-meta-item {
  padding: 8px 10px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.detail-meta-item span {
  display: block;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.detail-meta-item strong {
  display: block;
  margin-top: 4px;
  color: #191c22;
  font-size: 12px;
}

.detail-meta-item--accent {
  background: #eff6ff;
}

.detail-card--dark {
  background: linear-gradient(135deg, #0f172a, #1e3a8a);
  color: #ffffff;
}

.detail-card--dark h2,
.detail-card--dark p {
  color: #ffffff;
}

.detail-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.detail-card__header--stacked {
  flex-direction: column;
  align-items: stretch;
}

.detail-card__header h2,
.detail-card--dark h2 {
  margin: 0;
  font-size: 17px;
  color: #191c22;
}

.detail-card__tip {
  color: #64748b;
  font-size: 11px;
  line-height: 1.3;
  max-width: 140px;
  text-align: right;
}

.detail-card__paragraph,
.detail-card__muted {
  margin: 0;
  color: #475569;
  font-size: 11px;
  line-height: 1.35;
}

.detail-card__paragraph--compact {
  line-height: 1.5;
}

.attachment-note {
  display: flex;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #eff6ff;
  color: #1d4ed8;
}

.attachment-note strong {
  display: block;
  font-size: 14px;
}

.attachment-note p {
  margin: 4px 0 0;
  color: #334155;
  font-size: 12px;
}

.answer-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.answer-form :deep(.el-textarea__inner),
.result-question-card :deep(.el-textarea__inner) {
  border-radius: 12px;
  padding: 12px 14px;
}

.homework-question-wheel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.homework-question-wheel__item {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.homework-question-wheel__item:hover {
  border-color: #93c5fd;
  box-shadow: 0 8px 20px rgba(30, 64, 175, 0.08);
}

.homework-question-wheel__item.is-active {
  border-color: #2563eb;
  background: #eff6ff;
}

.homework-question-wheel__item.is-done:not(.is-active) {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.homework-question-wheel__badge {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: #e2e8f0;
  color: #334155;
  font-size: 14px;
  font-weight: 800;
}

.homework-question-wheel__item.is-active .homework-question-wheel__badge {
  background: #2563eb;
  color: #fff;
}

.homework-question-wheel__item.is-done:not(.is-active) .homework-question-wheel__badge {
  background: #16a34a;
  color: #fff;
}

.homework-question-wheel__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.homework-question-wheel__state {
  color: #2563eb;
  font-size: 11px;
  font-weight: 700;
}

.homework-question-wheel__body strong {
  color: #191c22;
  font-size: 13px;
}

.homework-question-wheel__body small {
  color: #64748b;
  font-size: 11px;
}

.homework-question-prompt {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
}

.homework-question-prompt__label {
  margin-bottom: 8px;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.code-callout {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #eff6ff;
  color: #1d4ed8;
}

.code-callout__title {
  font-size: 12px;
  font-weight: 800;
}

.code-callout p {
  margin: 4px 0 0;
  color: #334155;
  font-size: 12px;
}

.homework-question-feedback {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.homework-question-feedback__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 6px;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
}

.homework-question-feedback p {
  margin: 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
}

.check-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.check-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #64748b;
  font-size: 12px;
}

.check-list__item--done {
  color: #15803d !important;
  background: #f0fdf4 !important;
  border-color: #bbf7d0 !important;
}

.feedback-stack {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.feedback-pill {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.feedback-pill span {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.feedback-pill strong {
  color: #191c22;
  font-size: 15px;
}

.feedback-pill--blue {
  background: #eff6ff;
}

.homework-question-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.progress-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.progress-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.progress-track {
  width: 100%;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #dbeafe;
}

.progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(135deg, #2563eb, #38bdf8);
}

.progress-panel p {
  margin: 0;
  color: #64748b;
  font-size: 11px;
}

.result-question-card {
  padding: 10px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.result-question-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 10px;
}

.result-question-card__badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
}

.result-question-card h2 {
  margin: 8px 0 0;
  font-size: 15px;
  line-height: 1.5;
  color: #191c22;
}

.result-question-card__score {
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
}

@media (max-width: 1200px) {
  .detail-workspace {
    grid-template-columns: 1fr;
  }

  .detail-card--nav {
    position: static;
  }
}

@media (max-width: 900px) {
  .detail-hero,
  .detail-card,
  .info-card {
    padding: 14px;
    border-radius: 16px;
  }

  .detail-hero__body,
  .detail-hero__topline,
  .detail-hero__actions,
  .detail-card__header,
  .feedback-stack,
  .detail-meta-grid {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  .detail-card__tip {
    max-width: none;
    text-align: left;
  }
}

@media (max-width: 640px) {
  .result-question-card__header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>

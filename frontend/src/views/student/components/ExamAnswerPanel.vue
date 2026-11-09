<template>
  <div class="exam-detail-page">
    <div
      v-if="isLegacyNoEndAt && (state === 'A' || state === 'B')"
      class="intro-notice intro-notice--info legacy-notice"
    >
      <span class="material-symbols-outlined">info</span>
      <span>本场未设置统一截止时间，按个人限时收卷；如教师需要统一截止时间，需新建考试。</span>
    </div>

    <section v-if="state === 'A' && detail" class="intro-layout">
      <article class="intro-card intro-card--hero">
        <div class="intro-card__topline">
          <div>
            <div class="intro-card__eyebrow">考试概览</div>
            <h1>{{ detail.title }}</h1>
            <p>{{ detail.description || '确认时间与规则后即可开始。' }}</p>
          </div>
          <div class="intro-actions">
            <button class="primary-action" type="button" :loading="false" :disabled="starting || notStarted" @click="doStart">
              <span class="material-symbols-outlined">play_circle</span>
              {{ starting ? '正在开始考试' : '开始考试' }}
            </button>
            <button class="secondary-action" type="button" @click="router.push('/student/exams')">返回考试列表</button>
          </div>
        </div>

        <div v-if="notStarted" class="intro-notice">
          <span class="material-symbols-outlined">schedule</span>
          <span>考试尚未开始，开始时间 {{ formatDateTime(detail.startAt) }}</span>
        </div>

        <div class="intro-metrics">
            <div class="intro-metric intro-metric--wide">
              <span>考试窗口</span>
              <strong>{{ formatWindow(detail.startAt, detail.endAt) }}</strong>
            </div>
          <div class="intro-metric">
            <span>时长</span>
            <strong>{{ detail.durationMinutes }} 分钟</strong>
          </div>
          <div class="intro-metric">
            <span>题目数</span>
            <strong>{{ notStarted ? '待开始' : `${questions.length} 题` }}</strong>
          </div>
        </div>
      </article>

      <aside class="intro-side">
        <article class="side-card side-card--mini">
          <div class="side-card__title">作答须知</div>
          <ul class="bullet-list">
            <li>开始后连续计时 {{ detail.durationMinutes }} 分钟；若考试窗口先结束，则按窗口结束时间收卷。</li>
            <li>交卷后不可修改答案。</li>
            <li>主观题结果可能稍后更新。</li>
          </ul>
        </article>
      </aside>
    </section>

    <section v-else-if="state === 'B'" class="taking-layout">
      <header class="taking-header">
        <div>
          <div class="taking-header__eyebrow">进行中</div>
          <h1>{{ detail?.title }}</h1>
            <p>{{ answeredCount }} / {{ questions.length }} 题已作答。</p>
        </div>
        <div class="timer-shell" :class="{ 'timer-shell--urgent': isTimerUrgent }">
          <span class="material-symbols-outlined">timer</span>
          <div class="timer-shell__body">
            <strong>{{ formatTime(remainingSeconds) }}</strong>
            <small>{{ deadlineText }}</small>
            <small>{{ limitText }}</small>
            <small>到点自动交卷</small>
          </div>
        </div>
      </header>

      <div v-if="deadlineSubmitFailed" class="submit-failure-bar">
        <span class="material-symbols-outlined">error</span>
        <p>考试已结束，自动交卷失败，请立即重试或联系教师</p>
        <button class="primary-action primary-action--danger" type="button" :disabled="submitting" @click="doSubmit({ manual: true })">
          重新交卷
        </button>
      </div>

      <div class="taking-grid">
        <main class="question-column">
          <div class="exam-banner">
            <div>
              <strong>当前作答状态：进行中</strong>
              <p>优先处理未作答题。</p>
            </div>
            <div class="exam-banner__warn">未作答 {{ unansweredCount }} 题</div>
          </div>

          <article v-if="currentQuestion" class="question-card">
            <div class="question-card__header">
              <div>
                <span class="question-card__badge">第 {{ currentIndex + 1 }} 题 / 共 {{ questions.length }} 题</span>
                <h2>{{ currentQuestion.stem }}</h2>
              </div>
              <span class="question-card__score">{{ currentQuestion.questionScore ?? currentQuestion.score ?? 0 }} 分</span>
            </div>

            <div class="question-card__body">
              <section v-if="currentQuestionType === 'FILL_BLANK'" class="exam-question-prompt">
                <div class="exam-question-prompt__label">题目内容</div>
                <FillBlankInlineEditor
                  :model-value="currentQuestionDraft"
                  :prompt="currentQuestion.stem"
                  :disabled="submitting || deadlineReached"
                  @update:model-value="updateCurrentQuestionDraft"
                />
              </section>

              <QuestionAnswerShell
                v-if="currentQuestionType !== 'FILL_BLANK'"
                title="作答区"
                :hint="answerHint(currentQuestion)"
                :chip="questionTypeLabel(currentQuestion)"
              >
                <template #callout>
                  <div v-if="currentQuestionType === 'CODE'" class="exam-code-callout">
                    <div class="exam-code-callout__title">代码题作答</div>
                    <p>复用实验题的代码作答交互，交卷时同步提交兼容文本与结构化答案。</p>
                  </div>
                </template>

                <ChoiceAnswerEditor
                  v-if="currentQuestionType === 'SINGLE_CHOICE'"
                  :model-value="currentQuestionDraft"
                  :options="currentQuestionOptions"
                  mode="single"
                  :disabled="submitting || deadlineReached"
                  @update:model-value="updateCurrentQuestionDraft"
                />

                <ChoiceAnswerEditor
                  v-else-if="currentQuestionType === 'MULTIPLE_CHOICE'"
                  :model-value="currentQuestionDraft"
                  :options="currentQuestionOptions"
                  mode="multiple"
                  :disabled="submitting || deadlineReached"
                  @update:model-value="updateCurrentQuestionDraft"
                />

                <ChoiceAnswerEditor
                  v-else-if="currentQuestionType === 'TRUE_FALSE'"
                  :model-value="currentQuestionDraft"
                  :options="currentQuestionOptions"
                  mode="judge"
                  :disabled="submitting || deadlineReached"
                  @update:model-value="updateCurrentQuestionDraft"
                />

                <CodeLabAnswerEditor
                  v-else-if="currentQuestionType === 'CODE'"
                  :model-value="currentQuestionDraft"
                  language="CODE"
                  :rows="14"
                  :maxlength="20000"
                  :disabled="submitting || deadlineReached"
                  placeholder="// 请输入代码答案"
                  @update:model-value="updateCurrentQuestionDraft"
                />

                <TextAnswerEditor
                  v-else-if="currentQuestionType === 'SHORT_ANSWER'"
                  :model-value="currentQuestionDraft"
                  :rows="8"
                  :maxlength="5000"
                  :disabled="submitting || deadlineReached"
                  placeholder="请输入简答内容"
                  @update:model-value="updateCurrentQuestionDraft"
                />

                <TextAnswerEditor
                  v-else
                  :model-value="currentQuestionDraft"
                  :rows="12"
                  :maxlength="5000"
                  :disabled="submitting || deadlineReached"
                  placeholder="请输入答案"
                  @update:model-value="updateCurrentQuestionDraft"
                />
              </QuestionAnswerShell>
            </div>

            <div class="question-card__footer">
              <button class="secondary-action" type="button" :disabled="currentIndex === 0" @click="currentIndex -= 1">上一题</button>
              <div class="question-card__footer-actions">
                <button class="secondary-action" type="button" :disabled="currentIndex >= questions.length - 1" @click="currentIndex += 1">下一题</button>
                <button class="primary-action primary-action--danger" type="button" :disabled="submitting || deadlineReached" @click="confirmSubmit">
                  交卷
                </button>
              </div>
            </div>
          </article>
        </main>

        <aside class="map-column">
          <section class="map-card">
            <div class="map-card__header">
              <h3>题号导航</h3>
              <span>{{ progressPercent }}%</span>
            </div>
            <div class="progress-track">
              <div class="progress-track__fill" :style="{ width: `${progressPercent}%` }"></div>
            </div>
            <div class="question-map-grid">
              <button
                v-for="(question, index) in questions"
                :key="question.questionId"
                type="button"
                :class="['question-map-item', questionMapTone(question.questionId, index)]"
                @click="currentIndex = index"
              >
                {{ index + 1 }}
              </button>
            </div>
            <div class="map-legend">
              <span><i class="dot dot--done"></i>已作答</span>
              <span><i class="dot dot--current"></i>当前题</span>
            </div>
          </section>

          <section class="submit-card">
            <h3>交卷控制</h3>
            <div class="submit-card__stats">
              <div><span>总题数</span><strong>{{ questions.length }}</strong></div>
              <div><span>未作答</span><strong>{{ unansweredCount }}</strong></div>
              <div><span>已作答</span><strong>{{ answeredCount }}</strong></div>
            </div>
            <p>提交后立即锁定。</p>
            <button class="primary-action" type="button" :disabled="submitting || deadlineReached" @click="confirmSubmit">提交本次考试</button>
          </section>
        </aside>
      </div>
    </section>

    <section v-else-if="state === 'C' && detail" class="result-layout">
      <header class="result-header">
        <div>
          <div class="result-header__eyebrow">结果</div>
          <h1>{{ detail.title }}</h1>
          <p>{{ resultStatusDescription }}</p>
        </div>
        <button class="secondary-action" type="button" @click="router.push('/student/exams')">返回考试列表</button>
      </header>

      <section class="result-summary-grid">
        <article class="result-summary-card result-summary-card--status">
          <div class="result-summary-card__label">状态</div>
          <div class="result-summary-card__value">{{ statusLabel(detail.submissionStatus) }}</div>
          <div v-if="resultMode === 'result' && questions.length" class="result-summary-card__desc">答对 {{ correctCount }} / 共 {{ questions.length }} 题</div>
          </article>
        <template v-if="resultMode === 'result'">
          <article class="result-summary-card">
            <div class="result-summary-card__label">自动评分</div>
            <div class="result-summary-card__value">{{ canShowResult ? displayScore(detail.autoScore) : '-' }}</div>
            </article>
          <article class="result-summary-card">
            <div class="result-summary-card__label">教师评分</div>
            <div class="result-summary-card__value">{{ canShowResult ? displayScore(detail.manualScore) : '-' }}</div>
            </article>
          <article class="result-summary-card result-summary-card--accent">
            <div class="result-summary-card__label">总分</div>
            <div class="result-summary-card__value">{{ totalScoreText }}</div>
            </article>
        </template>
      </section>

      <el-alert
        v-if="resultMode === 'grading'"
        title="主观题阅卷中，成绩暂不公开"
        type="info"
        :closable="false"
        class="result-alert"
      />
      <el-alert
        v-else-if="resultMode === 'pending'"
        title="成绩待发布"
        type="info"
        :closable="false"
        class="result-alert"
      />

      <div v-if="resultMode === 'result'" class="result-grid">
        <main class="result-main">
          <article
            v-for="(question, index) in questions"
            :key="question.questionId"
            :id="`result-question-${question.questionId}`"
            :class="['result-question-card', toneOf(question) !== 'none' ? `result-question-card--${toneOf(question)}` : '']"
          >
            <div class="result-question-card__header">
              <div>
                <span class="result-question-card__badge">第{{ index + 1 }}题</span>
                <span v-if="toneOf(question) !== 'none'" :class="['result-status-badge', `result-status-badge--${toneOf(question)}`]">
                  <span class="material-symbols-outlined">{{ toneBadge[toneOf(question)].icon }}</span>
                  {{ toneBadge[toneOf(question)].text }}
                </span>
                <h2>{{ question.stem }}</h2>
              </div>
              <span :class="['result-question-card__score', toneOf(question) !== 'none' ? `result-question-card__score--${toneOf(question)}` : '']">
                {{ questionScoreText(question.questionId, question) }}
              </span>
            </div>

            <ExamOptionReview
              :options-json="question.optionsJson"
              :question-type="qType(question)"
              :student-answer="question.studentAnswer ?? question.answer"
              :standard-answer="canShowResult ? question.answerJson : null"
            />

            <div :class="['result-answer-block', toneOf(question) === 'correct' || toneOf(question) === 'wrong' ? `result-answer-block--${toneOf(question)}` : '']">
              <div class="result-answer-block__label">
                <span v-if="toneOf(question) !== 'none'" class="material-symbols-outlined result-answer-block__icon">{{ toneBadge[toneOf(question)].icon }}</span>
                你的答案
              </div>
              <div class="result-answer-block__value">{{ formatDisplayAnswer(question.studentAnswer ?? question.answer) }}</div>
            </div>

            <div class="result-answer-grid">
              <div class="result-answer-block result-answer-block--light">
                <div class="result-answer-block__label">参考答案</div>
              <div class="result-answer-block__value">{{ canShowResult ? referenceAnswerText(question.questionId, question) : '暂未公开参考答案' }}</div>
              </div>
              <div class="result-answer-block result-answer-block--light">
                <div class="result-answer-block__label">评分信息</div>
                <div class="result-answer-block__value">{{ canShowResult ? resultMetaText(question.questionId) : '成绩暂未开放' }}</div>
              </div>
              <div class="result-answer-block result-answer-block--light result-answer-block--full">
                <div class="result-answer-block__label">题目解析</div>
                <div class="result-answer-block__value">
                  {{ canShowResult ? analysisTextOf(question.questionId, question) : '成绩暂未开放' }}
                </div>
              </div>
            </div>

            <div v-if="canShowResult && resultAnswerMap[question.questionId]?.teacherComment" class="teacher-comment-box">
              <div class="teacher-comment-box__label">教师评语</div>
              <div>{{ resultAnswerMap[question.questionId].teacherComment }}</div>
            </div>
          </article>
        </main>

        <aside class="result-side">
          <section v-if="questions.length" class="map-card">
            <div class="map-card__header">
              <h3>对错速览</h3>
            </div>
            <div class="question-map-grid">
              <button
                v-for="(question, index) in questions"
                :key="question.questionId"
                type="button"
                :class="['question-map-item', resultMapItemClass(question)]"
                @click="scrollToResultQuestion(question.questionId)"
              >
                {{ index + 1 }}
              </button>
            </div>
            <div class="map-legend">
              <span><i class="dot dot--correct"></i>答对</span>
              <span><i class="dot dot--wrong"></i>答错</span>
              <span><i class="dot dot--manual"></i>教师评分题</span>
            </div>
          </section>
          <article class="side-card side-card--mini">
            <div class="side-card__title">结果说明</div>
            <ul class="bullet-list">
              <li>自动题立即展示结果。</li>
              <li>主观题批改后更新。</li>
              <li>若仍为“已提交”，请稍后再查看。</li>
            </ul>
          </article>
        </aside>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { inject } from 'vue';
import { ExamDetailKey } from '@/composables/useExamDetail';
import ExamOptionReview from '@/components/exam/ExamOptionReview.vue';
const { QuestionAnswerShell, ChoiceAnswerEditor, FillBlankInlineEditor, TextAnswerEditor, CodeLabAnswerEditor, router, state, detail, questions, currentIndex, remainingSeconds, starting, submitting, resultAnswerMap, timer, deadlineReached, deadlineSubmitFailed, isTimerUrgent, deadlineText, limitText, isLegacyNoEndAt, currentQuestion, notStarted, currentQuestionDraft, currentQuestionType, currentQuestionOptions, answeredCount, unansweredCount, progressPercent, canShowResult, resultMode, totalScoreText, resultStatusDescription, updateCurrentQuestionDraft, questionTypeLabel, answerHint, formatTime, statusLabel, formatDisplayAnswer, formatWindow, formatDateTime, questionMapTone, displayScore, questionScoreText, referenceAnswerText, resultMetaText, analysisTextOf, resultTone, correctCount, scrollToResultQuestion, qType, doStart, doSubmit, confirmSubmit } = inject(ExamDetailKey)!;

/** 结果态三态展示映射：tone 由 composable 判定，此处只做图标/文案/导航类名的呈现转换 */
type ResultTone = 'correct' | 'wrong' | 'manual' | 'none';
const toneOf = (question: { questionId: number }): ResultTone => resultTone(question.questionId);
const toneBadge: Record<ResultTone, { icon: string; text: string }> = {
  correct: { icon: 'check_circle', text: '回答正确' },
  wrong: { icon: 'cancel', text: '回答错误' },
  manual: { icon: 'hourglass_top', text: '教师评分题' },
  none: { icon: '', text: '' },
};
function resultMapItemClass(question: { questionId: number }): string {
  const tone = toneOf(question);
  return tone === 'none' ? '' : `is-${tone}`;
}
</script>

<style scoped>
.exam-detail-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.intro-layout,
.taking-grid,
.result-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.56fr) minmax(220px, 0.58fr);
  gap: 10px;
}

.intro-card,
.side-card,
.taking-header,
.exam-banner,
.question-card,
.map-card,
.submit-card,
.result-summary-card,
.result-question-card {
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(193, 198, 214, 0.45);
  box-shadow: 0 10px 26px rgba(25, 28, 34, 0.05);
}

.intro-card,
.side-card,
.taking-header,
.exam-banner,
.question-card,
.map-card,
.submit-card,
.result-question-card {
  padding: 12px;
}

.side-card--mini {
  padding: 10px 12px;
}

.intro-card--hero {
  background: linear-gradient(135deg, #ffffff, #f0fdfa 50%, #f8fafc);
}

.intro-card__topline {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.intro-card__eyebrow,
.taking-header__eyebrow,
.result-header__eyebrow {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #0f766e;
  font-weight: 700;
}

.intro-card h1,
.taking-header h1,
.result-header h1 {
  margin: 8px 0 0;
  font-size: 22px;
  font-weight: 800;
  color: #191c22;
}

.intro-card p,
.taking-header p,
.result-header p,
.submit-card p,
.side-card p {
  margin: 4px 0 0;
  color: #414753;
  line-height: 1.35;
  font-size: 12px;
}

/* 考试未开始提示：与概览指标保持一致的轻量提示条 */
.intro-notice {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border-radius: 12px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  color: #9a3412;
  font-size: 12px;
  font-weight: 600;
}

.intro-notice--info {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #1d4ed8;
}

.legacy-notice {
  margin: 0 0 12px;
}

.intro-metrics,
.result-summary-grid {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
}

.intro-metric--wide {
  grid-column: span 2;
}

.intro-metric,
.result-summary-card {
  border-radius: 12px;
  background: #fff;
  padding: 7px 9px;
  border: 1px solid #e2e8f0;
}

.intro-metric span,
.result-summary-card__label {
  display: block;
  font-size: 12px;
  color: #727785;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.intro-metric strong,
.result-summary-card__value {
  display: block;
  margin-top: 3px;
  font-size: 15px;
  color: #191c22;
}

.result-summary-card__desc {
  margin-top: 4px;
  color: #727785;
  font-size: 11px;
  line-height: 1.3;
}

.result-summary-card--status {
  background: linear-gradient(135deg, #0d9488, #14b8a6);
  color: #fff;
}

.result-summary-card--status .result-summary-card__label,
.result-summary-card--status .result-summary-card__value,
.result-summary-card--status .result-summary-card__desc {
  color: inherit;
}

.result-summary-card--accent {
  background: linear-gradient(135deg, #eff6ff, #f8fafc);
}

.intro-actions,
.question-card__footer,
.question-card__footer-actions,
.result-header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.primary-action,
.secondary-action {
  border: none;
  border-radius: 10px;
  padding: 8px 11px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.primary-action {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.primary-action--danger {
  background: linear-gradient(135deg, #dc2626, #ef4444);
}

.secondary-action {
  background: #f8fafc;
  color: #334155;
}

.intro-side,
.map-column,
.result-side {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.side-card--accent {
  background: linear-gradient(135deg, #eff6ff, #ecfeff);
}

.side-card__title,
.map-card h3,
.submit-card h3 {
  font-size: 13px;
  font-weight: 800;
  color: #191c22;
}

.bullet-list {
  margin: 8px 0 0;
  padding-left: 16px;
  color: #414753;
  line-height: 1.35;
  font-size: 11px;
}

.taking-layout {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.taking-header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.timer-shell {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 12px;
  background: #ecfeff;
  border: 1px solid #ccfbf1;
}

.timer-shell__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.timer-shell__body small {
  font-size: 11px;
  line-height: 1.3;
}

.timer-shell .material-symbols-outlined {
  color: #0f766e;
}

.timer-shell strong {
  display: block;
  font-size: 17px;
  color: #0f766e;
  font-variant-numeric: tabular-nums;
}

.timer-shell small {
  color: #0f766e;
  font-weight: 700;
}

.timer-shell--urgent {
  background: #fef2f2;
  border-color: #fecaca;
}

.timer-shell--urgent .material-symbols-outlined,
.timer-shell--urgent strong,
.timer-shell--urgent small {
  color: #b91c1c;
}

.exam-question-prompt {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
}

.exam-question-prompt__label {
  margin-bottom: 8px;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.exam-code-callout {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #eff6ff;
  color: #0f766e;
}

.exam-code-callout__title {
  font-size: 12px;
  font-weight: 800;
}

.exam-code-callout p {
  margin: 4px 0 0;
  color: #334155;
  font-size: 12px;
}

.question-column {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.exam-banner {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  background: linear-gradient(135deg, #eff6ff, #ecfeff);
}

.exam-banner strong {
  color: #0f172a;
}

.exam-banner__warn {
  border-radius: 999px;
  padding: 6px 10px;
  background: #fff7ed;
  color: #c2410c;
  font-size: 11px;
  font-weight: 700;
}

/* 截止后自动交卷失败的常驻提示条 */
.submit-failure-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.submit-failure-bar p {
  flex: 1;
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.question-card__header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
}

.question-card__badge,
.result-question-card__badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 6px 10px;
  background: #ecfeff;
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
}

.question-card h2,
.result-question-card h2 {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.35;
  color: #191c22;
}

.question-card__score,
.result-question-card__score {
  font-size: 12px;
  font-weight: 700;
  color: #0f766e;
}

.question-card__body {
  margin-top: 8px;
}

.option-stack {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.option-card {
  margin: 0;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 9px 10px;
  background: #fff;
  width: 100%;
}

.option-card__key {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 10px;
  background: #f1f5f9;
  color: #334155;
  margin-right: 10px;
  font-weight: 700;
}

.text-answer-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.text-answer-box :deep(.el-textarea__inner) {
  border-radius: 14px;
  padding: 12px 14px;
}

.text-answer-box label {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
}

.map-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.progress-track {
  margin-top: 10px;
  width: 100%;
  height: 8px;
  border-radius: 999px;
  background: #e2e8f0;
  overflow: hidden;
}

.progress-track__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #0f766e, #14b8a6);
}

.question-map-grid {
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 5px;
}

.question-map-item {
  aspect-ratio: 1;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #64748b;
  font-weight: 700;
  font-size: 11px;
  cursor: pointer;
}

.question-map-item.is-done {
  background: #0f766e;
  border-color: #0f766e;
  color: #fff;
}

.question-map-item.is-current {
  background: #ecfeff;
  border: 2px solid #14b8a6;
  color: #0f766e;
}

.question-map-item.is-empty {
  background: #fff;
}

.map-legend {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 11px;
  color: #475569;
}

.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  margin-right: 8px;
}

.dot--done {
  background: #0f766e;
}

.dot--current {
  background: #14b8a6;
}

.dot--empty {
  background: #cbd5e1;
}

.submit-card__stats {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.submit-card__stats div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 5px 0;
  border-bottom: 1px solid #e2e8f0;
}

.submit-card__stats div:last-child {
  border-bottom: none;
}

.result-alert {
  margin-top: 6px;
}

.result-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-question-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

/* 选项明细为子组件根节点，父级 scoped 样式可命中；组件自隐藏时不留空白 */
.exam-option-review {
  margin-top: 8px;
}

.result-answer-block,
.teacher-comment-box {
  border-radius: 12px;
  background: #f8fafc;
  padding: 8px 10px;
  margin-top: 6px;
}

.result-answer-block--light {
  background: #fff;
  border: 1px solid #e2e8f0;
}

.result-answer-block--full {
  grid-column: 1 / -1;
}

.result-answer-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  margin-top: 6px;
}

.result-answer-block__label,
.teacher-comment-box__label {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #727785;
  font-weight: 700;
}

.result-answer-block__value,
.teacher-comment-box div {
  margin-top: 3px;
  color: #191c22;
  line-height: 1.3;
  white-space: pre-wrap;
}

/* 结果页对错三态视觉：正确绿 / 错误红 / 主观题中性灰（色值见技术方案 D11） */
.result-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
  border-radius: 999px;
  padding: 4px 9px;
  font-size: 11px;
  font-weight: 700;
  vertical-align: middle;
}

.result-status-badge .material-symbols-outlined {
  font-size: 14px;
}

.result-status-badge--correct {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #16a34a;
}

.result-status-badge--wrong {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
}

.result-status-badge--manual {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #64748b;
}

.result-question-card--correct {
  border-left: 3px solid #16a34a;
}

.result-question-card--wrong {
  border-left: 3px solid #dc2626;
}

.result-question-card--manual {
  border-left: 3px solid #64748b;
}

.result-answer-block__icon {
  font-size: 14px;
  vertical-align: -3px;
  margin-right: 2px;
}

.result-answer-block--correct {
  background: #f0fdf4;
}

.result-answer-block--correct .result-answer-block__label,
.result-answer-block--correct .result-answer-block__value {
  color: #16a34a;
}

.result-answer-block--wrong {
  background: #fef2f2;
}

.result-answer-block--wrong .result-answer-block__label,
.result-answer-block--wrong .result-answer-block__value {
  color: #dc2626;
}

.result-question-card__score--correct {
  color: #16a34a;
}

.result-question-card__score--wrong {
  color: #dc2626;
}

.result-question-card__score--manual {
  color: #64748b;
}

.question-map-item.is-correct {
  background: #16a34a;
  border-color: #16a34a;
  color: #fff;
}

.question-map-item.is-wrong {
  background: #dc2626;
  border-color: #dc2626;
  color: #fff;
}

.question-map-item.is-manual {
  background: #f1f5f9;
  border-color: #e2e8f0;
  color: #64748b;
}

.dot--correct {
  background: #16a34a;
}

.dot--wrong {
  background: #dc2626;
}

.dot--manual {
  background: #64748b;
}

@media (max-width: 1100px) {
  .intro-layout,
  .taking-grid,
  .result-grid,
  .intro-metrics,
  .result-summary-grid,
  .result-answer-grid {
    grid-template-columns: 1fr;
  }

  .intro-metric--wide {
    grid-column: span 1;
  }
}

@media (max-width: 768px) {
  .intro-card__topline,
  .page-hero,
  .taking-header,
  .exam-banner,
  .question-card__header,
  .question-card__footer,
  .question-card__footer-actions,
  .result-header,
  .result-question-card__header,
  .intro-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .question-map-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
</style>

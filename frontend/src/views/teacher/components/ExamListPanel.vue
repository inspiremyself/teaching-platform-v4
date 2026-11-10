<template>
  <div class="page exams-console workbench-page">
    <section class="workbench-header exams-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="exams-header__eyebrow">教师控制台 / 考试闭环 / 考试管理</div>
          <h1>考试管理工作台</h1>
          <p>围绕发布、进行、收卷和批改入口维护考试闭环。</p>
        </div>
        <div class="workbench-header__actions console-actions">
          <button class="console-button console-button--secondary" type="button" @click="fetchExams" :disabled="loading">
            <span class="material-symbols-outlined">refresh</span>
            刷新列表
          </button>
          <button class="console-button console-button--primary" type="button" @click="openCreateDialog">
            <span class="material-symbols-outlined">add_circle</span>
            创建考试
          </button>
        </div>
      </div>

      <div class="workbench-meta exams-meta">
        <span class="workbench-meta__item"><strong>{{ rows.length }}</strong> 考试总数</span>
        <span class="workbench-meta__item"><strong>{{ filteredRows.length }}</strong> 筛选结果</span>
        <span class="workbench-meta__item"><strong>{{ draftCount }}</strong> 草稿</span>
        <span class="workbench-meta__item"><strong>{{ publishedCount }}</strong> 进行中</span>
        <span class="workbench-meta__item workbench-meta__item--accent"><strong>{{ totalSubmissionCount }}</strong> 累计提交</span>
        <span class="workbench-meta__item">{{ highlightedExamTitle }}</span>
      </div>
    </section>

    <div class="toolbar-card">
        <div class="toolbar-card__main">
          <div class="toolbar-search">
            <span class="material-symbols-outlined">search</span>
            <el-input v-model="searchQuery" placeholder="搜索考试标题、班级或描述" clearable />
          </div>
          <div class="toolbar-filters">
            <div class="toolbar-filter">
              <label>状态</label>
              <el-select v-model="statusFilter" clearable placeholder="全部状态">
                <el-option label="草稿" :value="ACTIVITY_STATUS.DRAFT" />
                <el-option label="已发布" :value="ACTIVITY_STATUS.PUBLISHED" />
                <el-option label="已关闭" :value="ACTIVITY_STATUS.CLOSED" />
              </el-select>
            </div>
            <div class="toolbar-filter">
              <label>班级</label>
              <el-select v-model="classFilter" clearable placeholder="全部班级">
                <el-option v-for="c in classOptions" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </div>
          </div>
        </div>
        <div class="toolbar-card__meta">
          <span>当前筛选结果 {{ filteredRows.length }} / {{ rows.length }}</span>
          <span class="toolbar-card__dot"></span>
          <span>{{ statusFilter ? `已锁定状态 ${statusLabel(statusFilter)}` : '未锁定状态，可查看全部考试阶段' }}</span>
          <span class="toolbar-card__dot"></span>
          <span>{{ classFilter ? `已锁定班级 ${classOptions.find(c => c.id === classFilter)?.name || ''}` : '全部班级' }}</span>
        </div>
    </div>

    <div class="table-card">
        <div class="table-card__header">
          <div>
            <h2>考试清单</h2>
            <p>把阶段、时间窗、收卷量和结果入口压进同一层级，避免考试管理页显得过散。</p>
          </div>
          <div class="table-card__chips">
            <span class="table-chip">真实列表接口</span>
            <span class="table-chip">发布状态保留</span>
            <span class="table-chip">批改入口保留</span>
          </div>
        </div>

        <el-table
          :data="filteredRows"
          v-loading="loading"
          empty-text="暂无考试"
          class="exams-table"
          :header-cell-style="headerCellStyle"
        >
          <el-table-column label="考试信息" min-width="340">
            <template #default="{ row }">
              <div class="exam-cell">
                <div :class="['exam-cell__icon', `is-${row.status.toLowerCase()}`]">
                  <span class="material-symbols-outlined">quiz</span>
                </div>
                <div class="exam-cell__body">
                  <div class="exam-cell__title-row">
                    <span class="exam-cell__title">{{ row.title }}</span>
                    <span :class="['status-pill', `status-pill--${row.status.toLowerCase()}`]">
                      <span class="status-pill__dot"></span>
                      {{ statusLabel(row.status) }}
                    </span>
                  </div>
                  <div class="exam-cell__meta">
                    <span>
                      <span class="material-symbols-outlined">school</span>
                      {{ row.className || '未绑定班级' }}
                    </span>
                    <span>
                      <span class="material-symbols-outlined">schedule</span>
                      {{ formatWindow(row.startAt, row.endAt) }}
                    </span>
                  </div>
                  <p class="exam-cell__desc">{{ examDescription(row) }}</p>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="考试节奏" min-width="220">
            <template #default="{ row }">
              <div class="timeline-cell">
                <div class="timeline-item">
                  <span class="timeline-item__label">开始</span>
                  <strong>{{ formatDateTime(row.startAt) }}</strong>
                </div>
                <div class="timeline-item">
                  <span class="timeline-item__label">结束</span>
                  <strong>{{ formatDateTime(row.endAt) }}</strong>
                </div>
                <div class="timeline-item">
                  <span class="timeline-item__label">时长</span>
                  <strong>{{ row.durationMinutes }} 分钟</strong>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="收卷概览" min-width="180">
            <template #default="{ row }">
              <div class="submission-cell">
                <div class="submission-cell__count">{{ row.submissionCount ?? 0 }}</div>
                <div class="submission-cell__label">已提交答卷</div>
                <div class="submission-cell__hint">{{ submissionHint(row) }}</div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="260" fixed="right" align="right">
            <template #default="{ row }">
              <div class="action-cell">
                <button v-if="row.status === ACTIVITY_STATUS.DRAFT" class="action-button action-button--ghost" type="button" @click="openEditDialog(row)">
                  编辑
                </button>
                <button v-if="row.status === ACTIVITY_STATUS.DRAFT" class="action-button action-button--success" type="button" @click="publishExam(row)">
                  发布
                </button>
                <button v-if="row.status === ACTIVITY_STATUS.PUBLISHED" class="action-button action-button--warning" type="button" @click="closeExam(row)">
                  关闭
                </button>
                <button class="action-button action-button--primary" type="button" @click="goResults(row.id)">
                  查看结果
                </button>
              </div>
            </template>
          </el-table-column>
        </el-table>
    </div>

    <el-dialog v-model="dialogVisible" width="1040px" destroy-on-close class="exam-dialog">
      <div class="exam-dialog__shell">
        <header class="exam-dialog__header">
          <div>
            <p class="exam-dialog__eyebrow">考试管理工作台 / {{ editingId ? '考试维护' : '考试创建' }}</p>
            <h2>{{ editingId ? '编辑考试配置' : '创建考试' }}</h2>
            <p class="exam-dialog__desc">在同一工作面板中完成考试基础信息、时间窗与题目池编排，保留现有真实考试接口和组卷行为。</p>
          </div>
          <div class="exam-dialog__badges">
            <span class="exam-dialog__badge">{{ form.classId ? classOptions.find(c => c.id === form.classId)?.name || '已选班级' : '待选班级' }}</span>
            <span class="exam-dialog__badge exam-dialog__badge--muted">{{ configuredQuestions.length }} 题</span>
            <span class="exam-dialog__badge exam-dialog__badge--muted">计划满分 {{ planTotalScore }} 分</span>
          </div>
        </header>

        <section class="exam-dialog__summary-grid">
          <article class="exam-dialog__summary-card">
            <span class="exam-dialog__label">考试窗口</span>
            <strong>{{ formatWindow(form.startAt, form.endAt) }}</strong>
            <p>开始时间、结束时间与考试时长共同决定学生侧作答区间。</p>
          </article>
          <article class="exam-dialog__summary-card">
            <span class="exam-dialog__label">组卷状态</span>
            <strong>{{ configuredQuestions.length ? `已选 ${configuredQuestions.length} 题 / 计划满分 ${planTotalScore} 分` : '尚未选择试题' }}</strong>
            <p>复用共享题目配置组件完成选题、排序与分值微调，保存时仍按考试既有题目引用结构提交。</p>
          </article>
        </section>

        <div class="dialog-layout">
        <section class="dialog-main">
          <div class="dialog-block">
            <div class="dialog-block__title">基础信息</div>
            <el-form class="exam-dialog__form" label-position="top">
              <el-form-item label="考试标题" required>
                <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="例如：软件体系结构期末考试" />
              </el-form-item>
              <el-form-item label="描述">
                <el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="补充考试说明、注意事项或题型范围" />
              </el-form-item>
              <el-form-item label="班级" required>
                <el-select v-model="form.classId" style="width: 100%" placeholder="请选择班级">
                  <el-option v-for="c in classOptions" :key="c.id" :label="c.name" :value="c.id" />
                </el-select>
              </el-form-item>
              <div class="dialog-grid">
                <el-form-item label="开始时间">
                  <el-date-picker
                    v-model="form.startAt"
                    type="datetime"
                    value-format="YYYY-MM-DDTHH:mm:ssZ"
                    placeholder="请选择开始时间"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="结束时间">
                  <el-date-picker
                    v-model="form.endAt"
                    type="datetime"
                    value-format="YYYY-MM-DDTHH:mm:ssZ"
                    placeholder="请选择结束时间"
                    style="width: 100%"
                  />
                </el-form-item>
              </div>
              <el-form-item label="时长(分钟)">
                <el-input-number v-model="form.durationMinutes" :min="1" :max="600" />
                <p v-if="durationExceedsWindow" class="exam-dialog__hint exam-dialog__hint--danger">
                  {{ windowTruncationHint }}
                </p>
              </el-form-item>
              <el-form-item label="题目乱序">
                <el-switch v-model="form.shuffleQuestions" />
                <p class="exam-dialog__hint">开启后，题目顺序按学生生成；同一学生刷新后顺序保持不变。</p>
              </el-form-item>
              <el-form-item label="成绩可见性">
                <el-select v-model="form.scoreVisibilityMode" style="width: 100%">
                  <el-option label="交卷后立即可见" :value="SCORE_VISIBILITY_MODE.IMMEDIATE" />
                  <el-option label="教师确认后可见" :value="SCORE_VISIBILITY_MODE.AFTER_TEACHER_CONFIRM" />
                  <el-option
                    v-if="isLegacyScoreMode"
                    :label="legacyScoreModeLabel"
                    :value="form.scoreVisibilityMode"
                    disabled
                  />
                </el-select>
                <p v-if="form.scoreVisibilityMode === SCORE_VISIBILITY_MODE.AFTER_TEACHER_CONFIRM" class="exam-dialog__hint">
                  学生交卷后不会立即看到成绩。
                </p>
                <p v-if="isLegacyScoreMode" class="exam-dialog__hint exam-dialog__hint--danger">
                  当前为历史配置「{{ legacyScoreModeLabel }}」，必须改选「交卷后立即可见」或「教师确认后可见」后才能保存。
                </p>
              </el-form-item>
            </el-form>
          </div>

          <div class="dialog-block">
            <div class="dialog-block__header">
              <div>
                <div class="dialog-block__title">题目配置</div>
                <p class="dialog-block__desc">通过共享选题弹窗从题库带入题目，再在当前考试壳中调整顺序与分值。</p>
              </div>
              <div class="dialog-block__actions">
                <el-button plain @click="openQuestionPicker">从题库挑题</el-button>
              </div>
            </div>

            <div v-if="legacyInlineQuestions.length" class="legacy-inline-block">
              <el-alert
                v-for="item in legacyInlineQuestions"
                :key="item.localId"
                type="warning"
                :closable="false"
                show-icon
                :title="`该题不支持，请删除后从题库重新选择：${item.stem}`"
              >
                <el-button link type="danger" @click="removeLegacyInlineQuestion(item.localId)">删除该题</el-button>
              </el-alert>
            </div>

            <QuestionConfigList
              :items="configuredQuestions"
              @edit="editConfiguredQuestion"
              @remove="removeConfiguredQuestion"
              @reorder="handleConfiguredQuestionsReorder"
              @score-change="handleConfiguredQuestionScoreChange"
            />
          </div>
        </section>

        <aside class="dialog-side">
          <section class="selection-panel">
            <div class="selection-panel__header">
              <div>
                <div class="selection-panel__title">已选题目</div>
                <p>考试题目全部来自题库；这里复用共享题目配置清单管理真实组卷顺序与分值。</p>
              </div>
              <span class="selection-panel__badge">{{ configuredQuestions.length }} 题</span>
            </div>
            <div v-if="configuredQuestions.length" class="selection-list">
              <article v-for="row in configuredQuestions" :key="row.localId" class="selection-card">
                <div class="selection-card__stem">{{ row.stem }}</div>
                <div class="selection-card__controls">
                  <div>
                    <label>题型</label>
                    <strong>{{ row.questionType }}</strong>
                  </div>
                  <div>
                    <label>当前分值</label>
                    <strong>{{ row.score }} 分</strong>
                  </div>
                </div>
              </article>
            </div>
            <el-empty v-else description="请先在左侧题目池中选择试题" />
          </section>
        </aside>
      </div>
        <footer class="exam-dialog__footer">
          <div class="exam-dialog__footer-note">保留创建 / 编辑考试、题目选择、排序与分值保存逻辑，仅强化教师台工作面板样式与信息层级。</div>
          <div class="exam-dialog__footer-actions">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button
              type="primary"
              :loading="submitting"
              :disabled="legacyInlineQuestions.length > 0 || isLegacyScoreMode"
              @click="submitForm"
            >保存考试</el-button>
          </div>
        </footer>
      </div>
    </el-dialog>

    <QuestionEditorDialog
      v-model:visible="questionDialogVisible"
      :initial-question="currentEditorQuestion"
      :loading="questionSubmitting"
      :editing="Boolean(currentConfiguredQuestion)"
      description="在考试壳中直接复用共享题目编辑器维护题库题资产；保存后会同步到当前考试配置清单。"
      submit-text="保存题库题"
      :show-asset-fields="true"
      :show-config-fields="false"
      @saved="submitConfiguredQuestion"
    />

    <QuestionPickerDialog
      v-model:visible="pickerVisible"
      validate-judge-format
      @confirm="applyPickedQuestions"
    />
  </div>
</template>

<script setup lang="ts">
import { inject } from 'vue';
import { ExamListKey } from '@/composables/useExamList';
const { ACTIVITY_STATUS, SCORE_VISIBILITY_MODE, QuestionConfigList, QuestionEditorDialog, QuestionPickerDialog, loading, submitting, questionSubmitting, dialogVisible, pickerVisible, questionDialogVisible, editingId, rows, classOptions, configuredQuestions, planTotalScore, legacyInlineQuestions, searchQuery, statusFilter, classFilter, form, filteredRows, draftCount, publishedCount, totalSubmissionCount, highlightedExamTitle, headerCellStyle, currentConfiguredQuestion, currentEditorQuestion, isLegacyScoreMode, legacyScoreModeLabel, durationExceedsWindow, windowTruncationHint, fetchExams, openCreateDialog, openEditDialog, openQuestionPicker, applyPickedQuestions, handleConfiguredQuestionsReorder, handleConfiguredQuestionScoreChange, removeConfiguredQuestion, removeLegacyInlineQuestion, editConfiguredQuestion, submitConfiguredQuestion, submitForm, publishExam, closeExam, goResults, statusLabel, formatDateTime, formatWindow, examDescription, submissionHint } = inject(ExamListKey)!;
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.exams-console {
  color: #0f172a;
}

.console-hero {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.console-hero__main {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.console-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #64748b;
}

.console-breadcrumb .is-current {
  color: #0f766e;
  font-weight: 700;
}

.console-title-row {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-end;
}

.console-title-row h1 {
  margin: 0;
  font-size: 34px;
  font-weight: 800;
}

.console-title-row p {
  margin: 10px 0 0;
  color: #64748b;
  max-width: 720px;
}

.console-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.console-button {
  border: none;
  border-radius: 18px;
  padding: 12px 18px;
  font-size: 14px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.console-button--secondary {
  background: #eef2ff;
  color: #334155;
}

.console-button--primary {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  box-shadow: 0 16px 36px rgba(15, 118, 110, 0.24);
}

.context-strip {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  border: 1px solid #dbeafe;
  background: linear-gradient(135deg, #eff6ff, #ecfeff 55%, #f8fafc);
  border-radius: 28px;
  padding: 22px 24px;
}

.context-strip__lead {
  display: flex;
  gap: 16px;
  align-items: center;
}

.context-strip__badge {
  width: 56px;
  height: 56px;
  border-radius: 20px;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 16px 30px rgba(15, 118, 110, 0.24);
}

.context-strip__label {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #0f766e;
  font-weight: 700;
}

.context-strip__value {
  margin-top: 4px;
  font-size: 24px;
  font-weight: 800;
}

.context-strip__meta {
  margin-top: 6px;
  color: #475569;
}

.context-strip__stats {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: stretch;
}

.mini-stat {
  min-width: 120px;
  border-radius: 20px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.mini-stat__label {
  font-size: 12px;
  color: #64748b;
}

.mini-stat__value {
  display: block;
  margin-top: 8px;
  font-size: 24px;
}

.console-grid {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.console-summaries {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.summary-card {
  border-radius: 26px;
  padding: 24px;
  color: #fff;
  box-shadow: 0 18px 38px rgba(15, 23, 42, 0.09);
}

.summary-card--teal {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
}

.summary-card--slate {
  background: linear-gradient(135deg, #0f172a, #334155);
}

.summary-card--line {
  background: linear-gradient(135deg, #1d4ed8, #38bdf8);
}

.summary-card__eyebrow {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  opacity: 0.82;
}

.summary-card__value {
  margin-top: 10px;
  font-size: 34px;
  font-weight: 800;
}

.summary-card__desc {
  margin-top: 10px;
  font-size: 13px;
  opacity: 0.78;
}

.toolbar-card,
.table-card,
.selection-panel {
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 18px 38px rgba(15, 23, 42, 0.06);
}

.toolbar-card {
  padding: 20px 22px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar-card__main {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.toolbar-search {
  flex: 1;
  min-width: 260px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-radius: 20px;
  background: #f8fafc;
  padding: 0 16px;
  border: 1px solid #e2e8f0;
}

.toolbar-search > .material-symbols-outlined {
  color: #64748b;
}

.toolbar-search :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
  padding-left: 0;
}

.toolbar-filters {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
}

.toolbar-filter {
  min-width: 170px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.toolbar-filter label {
  font-size: 12px;
  color: #64748b;
  font-weight: 700;
}

.toolbar-card__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 13px;
  color: #64748b;
}

.toolbar-card__dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: #14b8a6;
}

.table-card {
  padding: 18px;
}

.table-card__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.table-card__header h2 {
  margin: 0;
  font-size: 22px;
}

.table-card__header p {
  margin: 8px 0 0;
  color: #64748b;
}

.table-card__chips {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.table-chip {
  padding: 8px 12px;
  border-radius: 999px;
  background: #f0fdfa;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.exam-cell {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.exam-cell__icon {
  width: 50px;
  height: 50px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.exam-cell__icon.is-draft {
  background: #f1f5f9;
  color: #475569;
}

.exam-cell__icon.is-published {
  background: #ecfdf5;
  color: #0f766e;
}

.exam-cell__icon.is-closed {
  background: #eff6ff;
  color: #1d4ed8;
}

.exam-cell__body {
  min-width: 0;
}

.exam-cell__title-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.exam-cell__title {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill__dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: currentColor;
}

.status-pill--draft {
  background: #f1f5f9;
  color: #475569;
}

.status-pill--published {
  background: #ecfdf5;
  color: #0f766e;
}

.status-pill--closed {
  background: #eff6ff;
  color: #1d4ed8;
}

.exam-cell__meta,
.timeline-cell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.exam-cell__meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #64748b;
  font-size: 13px;
}

.exam-cell__meta .material-symbols-outlined {
  font-size: 16px;
}

.exam-cell__desc {
  margin: 10px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.timeline-item {
  padding: 10px 12px;
  border-radius: 16px;
  background: #f8fafc;
}

.timeline-item__label {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.timeline-item strong {
  color: #0f172a;
}

.submission-cell {
  padding: 14px;
  border-radius: 18px;
  background: linear-gradient(180deg, #f8fafc, #f1f5f9);
  border: 1px solid #e2e8f0;
}

.submission-cell__count {
  font-size: 30px;
  font-weight: 800;
  color: #0f172a;
}

.submission-cell__label {
  margin-top: 4px;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #0f766e;
  font-weight: 700;
}

.submission-cell__hint {
  margin-top: 10px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.action-cell {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.action-button {
  border: none;
  border-radius: 14px;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.action-button--ghost {
  background: #f8fafc;
  color: #334155;
}

.action-button--success {
  background: #ecfdf5;
  color: #0f766e;
}

.action-button--warning {
  background: #fff7ed;
  color: #c2410c;
}

.action-button--primary {
  background: #0f766e;
  color: #fff;
}

.exam-dialog :deep(.el-dialog) {
  border-radius: 30px;
  overflow: hidden;
}

.exam-dialog :deep(.el-dialog__header) {
  display: none;
}

.exam-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.exam-dialog__shell {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
}

.exam-dialog__header,
.exam-dialog__summary-card,
.exam-dialog__footer {
  border: 1px solid #e2e8f0;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
}

.exam-dialog__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px;
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.08), rgba(239, 246, 255, 0.96));
}

.exam-dialog__eyebrow,
.exam-dialog__label {
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.exam-dialog__header h2 {
  margin: 8px 0 0;
  color: #0f172a;
  font-size: 28px;
  font-weight: 800;
}

.exam-dialog__desc {
  margin: 8px 0 0;
  max-width: 620px;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.exam-dialog__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 8px;
}

.exam-dialog__badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: #ccfbf1;
  color: #115e59;
  font-size: 11px;
  font-weight: 800;
}

.exam-dialog__badge--muted {
  background: #f1f5f9;
  color: #475569;
}

.exam-dialog__summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.exam-dialog__summary-card {
  padding: 18px;
}

.exam-dialog__summary-card strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.exam-dialog__summary-card p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.dialog-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 0.95fr);
  gap: 20px;
}

.dialog-main,
.dialog-side {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.dialog-block,
.selection-panel {
  padding: 18px;
}

.dialog-block__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 14px;
}

.dialog-block {
  border-radius: 24px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.dialog-block__title,
.selection-panel__title {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
  margin-bottom: 0;
}

.dialog-block__desc {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.dialog-block__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.exam-dialog__hint {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.exam-dialog__hint--danger {
  color: #b91c1c;
  font-weight: 600;
}

.legacy-inline-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.exam-dialog__form :deep(.el-form-item) {
  margin-bottom: 0;
}

.exam-dialog__form :deep(.el-form-item + .el-form-item) {
  margin-top: 14px;
}

.exam-dialog__form :deep(.el-form-item__label) {
  margin-bottom: 8px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.exam-dialog__form :deep(.el-input__wrapper),
.exam-dialog__form :deep(.el-select__wrapper),
.exam-dialog__form :deep(.el-textarea__inner),
.exam-dialog__form :deep(.el-input-number) {
  border-radius: 14px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  background: #f8fafc;
}

.selection-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.selection-panel__header p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.selection-panel__badge {
  padding: 6px 10px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.selection-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.selection-card {
  padding: 14px;
  border-radius: 18px;
  background: #f8fafc;
}

.selection-card__stem {
  font-size: 13px;
  line-height: 1.6;
  color: #0f172a;
}

.selection-card__controls {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.selection-card__controls label {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.selection-card__controls strong {
  color: #0f172a;
  font-size: 13px;
}

.exam-dialog__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
}

.exam-dialog__footer-note {
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.exam-dialog__footer-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 1100px) {
  .console-summaries,
  .dialog-layout {
    grid-template-columns: 1fr;
  }

  .toolbar-card__main,
  .table-card__header,
  .context-strip,
  .console-title-row {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 768px) {
  .console-summaries,
  .dialog-grid,
  .selection-card__controls,
  .exam-dialog__summary-grid {
    grid-template-columns: 1fr;
  }

  .context-strip__stats {
    width: 100%;
  }

  .mini-stat {
    flex: 1;
  }

  .exam-dialog__header,
  .exam-dialog__footer {
    flex-direction: column;
    align-items: stretch;
  }

  .exam-dialog__badges {
    justify-content: flex-start;
  }
}
</style>

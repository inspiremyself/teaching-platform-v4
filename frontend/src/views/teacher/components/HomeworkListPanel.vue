<template>
  <div class="page homeworks-page workbench-page">
    <section class="workbench-header homeworks-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="homeworks-header__eyebrow">教师后台 / 作业管理</div>
          <h1>作业管理台</h1>
          <p>围绕班级、时间窗、发布状态与提交进度维护作业闭环。</p>
        </div>
        <div class="workbench-header__actions">
          <el-button type="primary" @click="openCreateDialog">新建作业</el-button>
        </div>
      </div>

      <div class="workbench-meta homeworks-meta">
        <span class="workbench-meta__item"><strong>{{ filteredRows.length }}</strong> 作业总数</span>
        <span class="workbench-meta__item"><strong>{{ publishedCount }}</strong> 已发布</span>
        <span class="workbench-meta__item"><strong>{{ draftCount }}</strong> 草稿/待处理</span>
        <span class="workbench-meta__item workbench-meta__item--accent"><strong>{{ totalSubmissionCount }}</strong> 提交总量</span>
        <span class="workbench-meta__item">{{ activeStatusLabel }}</span>
        <span class="workbench-meta__item">{{ classFilterLabel }}</span>
      </div>
    </section>

    <section class="toolbar-card">
      <div class="toolbar-card__main">
        <div class="toolbar-search">
          <span class="material-symbols-outlined">search</span>
          <el-input v-model="searchQuery" placeholder="搜索作业标题、班级或说明" clearable />
        </div>
        <div class="toolbar-filters">
          <el-select v-model="statusFilter" placeholder="全部状态" clearable>
            <el-option label="全部状态" value="" />
            <el-option label="草稿" :value="ACTIVITY_STATUS.DRAFT" />
            <el-option label="已发布" :value="ACTIVITY_STATUS.PUBLISHED" />
            <el-option label="已关闭" :value="ACTIVITY_STATUS.CLOSED" />
          </el-select>
          <el-select v-model="classFilter" placeholder="全部班级" clearable>
            <el-option label="全部班级" value="0" />
            <el-option v-for="item in classOptions" :key="item.id" :label="item.name" :value="String(item.id)" />
          </el-select>
          <el-select v-model="dueFilter" placeholder="全部时效" clearable>
            <el-option label="全部时效" value="" />
            <el-option label="即将截止" value="upcoming" />
            <el-option label="已截止" value="overdue" />
            <el-option label="未设置截止" value="undated" />
          </el-select>
        </div>
      </div>
      <div class="toolbar-card__meta">
        <span>共 {{ rows.length }} 条作业</span>
        <span class="toolbar-divider"></span>
        <span>当前筛出 {{ filteredRows.length }} 条</span>
        <el-button link type="primary" @click="resetFilters">清空筛选</el-button>
      </div>
    </section>

    <section class="homeworks-console" v-loading="loading">
      <header class="homeworks-console__header">
        <div>
          <h2>作业清单</h2>
          <p>保留作业摘要、班级归属、提交数、时间窗和状态动作，避免退回成单薄表格页。</p>
        </div>
      </header>

      <div v-if="filteredRows.length" class="homework-list-shell">
        <article v-for="row in filteredRows" :key="row.id" class="homework-row">
          <div class="homework-row__primary">
            <div class="homework-row__title-line">
              <div>
                <h3>{{ row.title }}</h3>
                <p>{{ row.description || '暂无作业说明，建议补充作答目标、提交格式与评分重点。' }}</p>
              </div>
              <el-tag :type="statusTagType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
            </div>

            <div class="homework-row__meta-grid">
              <div>
                <span class="homework-row__label">所属班级</span>
                <strong>{{ row.className || '-' }}</strong>
              </div>
              <div>
                <span class="homework-row__label">开始时间</span>
                <strong>{{ formatDateTime(row.startAt) }}</strong>
              </div>
              <div>
                <span class="homework-row__label">截止时间</span>
                <strong>{{ formatDateTime(row.dueAt) }}</strong>
              </div>
              <div>
                <span class="homework-row__label">提交数</span>
                <strong>{{ row.submissionCount ?? 0 }}</strong>
              </div>
            </div>

            <div class="homework-row__supporting">
              <span class="homework-chip">
                <span class="material-symbols-outlined">timelapse</span>
                {{ dueHint(row.dueAt) }}
              </span>
              <span v-if="row.attachmentPath" class="homework-chip homework-chip--muted">
                <span class="material-symbols-outlined">attach_file</span>
                {{ row.attachmentPath }}
              </span>
            </div>
          </div>

          <div class="homework-row__actions">
            <el-button type="primary" plain @click="goSubmissions(row.id)">提交列表</el-button>
            <el-button @click="openEditDialog(row)">编辑</el-button>
            <el-button plain @click="releaseScores(row)">发布成绩</el-button>
            <el-button
              v-if="row.status === ACTIVITY_STATUS.DRAFT"
              type="success"
              plain
              @click="toggleStatus(row)"
            >
              发布作业
            </el-button>
            <el-button
              v-else-if="row.status === ACTIVITY_STATUS.PUBLISHED"
              type="warning"
              plain
              @click="toggleStatus(row)"
            >
              关闭作业
            </el-button>
          </div>
        </article>
      </div>

      <el-empty v-else description="暂无匹配作业" />
    </section>

    <el-dialog v-model="dialogVisible" width="860px" class="homework-dialog" destroy-on-close>
      <div class="homework-dialog__shell">
        <header class="homework-dialog__header">
          <div>
            <p class="homework-dialog__eyebrow">作业管理台 / {{ editingId ? '配置调整' : '发布准备' }}</p>
            <h2>{{ editingId ? '编辑作业配置' : '新建作业' }}</h2>
            <p class="homework-dialog__desc">围绕班级、说明、附件和时间窗组织作业发布信息；题目按清单增量同步，已发布作业仅支持调整排序。</p>
          </div>
          <div class="homework-dialog__badges">
            <span class="homework-dialog__badge">{{ statusLabel(form.status) }}</span>
            <span class="homework-dialog__badge homework-dialog__badge--muted">{{ form.classId ? classOptions.find(item => item.id === form.classId)?.name || '已选班级' : '待选班级' }}</span>
          </div>
        </header>

        <section class="homework-dialog__summary-grid">
          <article class="homework-dialog__summary-card">
            <span class="homework-dialog__label">时间窗状态</span>
            <strong>{{ form.dueAt ? dueHint(form.dueAt) : '未设置截止时间' }}</strong>
            <p>开始时间与截止时间会直接影响学生侧可见与提交窗口。</p>
          </article>
          <article class="homework-dialog__summary-card">
            <span class="homework-dialog__label">发布说明</span>
            <strong>{{ editingId ? '更新既有作业' : '创建新作业条目' }}</strong>
            <p>标题与班级仍是当前保存动作的必填条件，原有校验保持不变。</p>
          </article>
        </section>

        <el-form class="homework-dialog__form" label-position="top">
          <section class="homework-dialog__group">
            <div class="homework-dialog__group-title">基础信息</div>
            <div class="homework-dialog__grid homework-dialog__grid--2col">
              <el-form-item label="作业标题" required>
                <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="例如：实验一报告" />
              </el-form-item>
              <el-form-item label="所属班级" required>
                <el-select v-model="form.classId" placeholder="请选择班级" :disabled="editingSubmissionCount > 0">
                  <el-option v-for="item in classOptions" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="学科" :required="!editingId">
                <el-select v-model="form.subjectId" :placeholder="editingId ? '保持原值' : '请选择学科'">
                  <el-option v-if="editingId" label="保持原值" :value="0" />
                  <el-option v-for="item in subjectOptions" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="作业说明">
              <el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="补充作答目标、提交格式、评分重点等" />
            </el-form-item>
            <el-form-item label="附件路径">
              <el-input v-model="form.attachmentPath" placeholder="可选，本地挂载路径" />
            </el-form-item>
          </section>

          <section class="homework-dialog__group">
            <div class="homework-dialog__group-title">时间与状态配置</div>
            <div class="homework-dialog__grid homework-dialog__grid--3col">
              <el-form-item label="开始时间">
                <el-date-picker
                  v-model="form.startAt"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ssZ"
                  placeholder="请选择开始时间"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item label="截止时间">
                <el-date-picker
                  v-model="form.dueAt"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ssZ"
                  placeholder="请选择截止时间"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item label="发布状态">
                <el-select v-model="form.status" placeholder="请选择状态" :disabled="originalStatus === ACTIVITY_STATUS.CLOSED">
                  <el-option
                    label="草稿"
                    :value="ACTIVITY_STATUS.DRAFT"
                    :disabled="originalStatus === ACTIVITY_STATUS.PUBLISHED && editingSubmissionCount > 0"
                  />
                  <el-option label="已发布" :value="ACTIVITY_STATUS.PUBLISHED" />
                  <el-option label="已关闭" :value="ACTIVITY_STATUS.CLOSED" :disabled="originalStatus === ACTIVITY_STATUS.DRAFT" />
                </el-select>
              </el-form-item>
              <el-form-item label="成绩可见性">
                <el-select v-model="form.scoreVisibilityMode" placeholder="请选择可见性">
                  <el-option label="立即可见" :value="SCORE_VISIBILITY_MODE.IMMEDIATE" />
                  <el-option label="教师确认后可见" :value="SCORE_VISIBILITY_MODE.AFTER_TEACHER_CONFIRM" />
                  <el-option label="截止后可见" :value="SCORE_VISIBILITY_MODE.AFTER_DEADLINE" />
                  <el-option label="教师手动发布" :value="SCORE_VISIBILITY_MODE.MANUAL_RELEASE" />
                </el-select>
              </el-form-item>
            </div>
          </section>

          <section class="homework-dialog__group">
            <div class="homework-dialog__group-head">
              <div>
                <div class="homework-dialog__group-title">
                  题目配置
                  <span class="homework-dialog__badge homework-dialog__badge--muted">计划满分 {{ planTotalScore }} 分</span>
                </div>
                <p class="homework-dialog__group-desc">
                  统一复用题库选题、题目配置清单与共享编辑器。{{ editingId ? '保存时按清单增量同步：新增、修改与删除都会落到当前作业。' : '创建作业后会按当前清单写入题目。' }}
                </p>
                <p v-if="questionLockHint" class="homework-dialog__group-lock">{{ questionLockHint }}</p>
              </div>
              <div class="homework-dialog__group-actions">
                <el-switch v-model="saveToQuestionBank" active-text="同时保存到题库" :disabled="submitting || detailLoading" />
                <el-button plain :disabled="questionContentLocked || detailLoading" @click="openQuestionPicker">从题库挑题</el-button>
                <el-button type="primary" plain :disabled="questionContentLocked || detailLoading" @click="openInlineQuestionEditor">新增内联题</el-button>
              </div>
            </div>

            <QuestionConfigList
              :items="configuredQuestions"
              :content-locked="questionContentLocked"
              :reorder-locked="questionReorderLocked"
              :busy="detailLoading"
              copyable
              bank-snapshot-hint
              @edit="editConfiguredQuestion"
              @remove="removeConfiguredQuestion"
              @reorder="handleConfiguredQuestionsReorder"
              @score-change="handleConfiguredQuestionScoreChange"
              @copy-to-bank="copyConfiguredQuestionToBank"
            />
          </section>
        </el-form>

        <footer class="homework-dialog__footer">
          <div class="homework-dialog__footer-note">题目按清单增量同步；已发布作业仅支持调整排序，已关闭作业不可再修改题目。</div>
          <div class="homework-dialog__footer-actions">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="submitForm">{{ editingId ? '保存作业' : '创建作业' }}</el-button>
          </div>
        </footer>
      </div>
    </el-dialog>

    <QuestionEditorDialog
      v-model:visible="questionDialogVisible"
      :initial-question="currentEditorQuestion"
      :loading="questionSubmitting"
      :editing="Boolean(currentConfiguredQuestion)"
      :description="questionDialogDescription"
      :submit-text="questionDialogSubmitText"
      @saved="submitConfiguredQuestion"
    />

    <QuestionPickerDialog v-model:visible="pickerVisible" @confirm="applyPickedQuestions" />
  </div>
</template>

<script setup lang="ts">
import { inject } from 'vue';
import { HomeworksKey } from '@/composables/useHomeworks';
const { ACTIVITY_STATUS, SCORE_VISIBILITY_MODE, QuestionConfigList, QuestionEditorDialog, QuestionPickerDialog, loading, submitting, questionSubmitting, detailLoading, dialogVisible, pickerVisible, questionDialogVisible, editingId, originalStatus, rows, classOptions, subjectOptions, configuredQuestions, planTotalScore, saveToQuestionBank, searchQuery, statusFilter, classFilter, dueFilter, form, editingSubmissionCount, filteredRows, publishedCount, draftCount, totalSubmissionCount, activeStatusLabel, classFilterLabel, currentConfiguredQuestion, questionContentLocked, questionReorderLocked, questionLockHint, currentEditorQuestion, questionDialogDescription, questionDialogSubmitText, resetFilters, openCreateDialog, openEditDialog, openQuestionPicker, openInlineQuestionEditor, applyPickedQuestions, handleConfiguredQuestionsReorder, handleConfiguredQuestionScoreChange, removeConfiguredQuestion, editConfiguredQuestion, submitConfiguredQuestion, copyConfiguredQuestionToBank, submitForm, toggleStatus, releaseScores, goSubmissions, statusLabel, statusTagType, dueHint, formatDateTime } = inject(HomeworksKey)!;
</script>

<style scoped>
.homeworks-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.homeworks-hero {
  position: relative;
  overflow: hidden;
  border-radius: 32px;
  padding: 30px 32px;
  background:
    radial-gradient(circle at top right, rgba(96, 165, 250, 0.22), transparent 28%),
    linear-gradient(135deg, #0f172a, #1d4ed8 58%, #3b82f6);
  box-shadow: 0 20px 50px rgba(30, 64, 175, 0.22);
}

.homeworks-hero__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 22px;
  color: #eff6ff;
}

.homeworks-hero__eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(219, 234, 254, 0.82);
}

.homeworks-hero__heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
}

.homeworks-hero__heading h1 {
  margin: 0;
  font-size: 34px;
  font-weight: 800;
  color: #ffffff;
}

.homeworks-hero__heading p {
  max-width: 720px;
  margin: 10px 0 0;
  font-size: 15px;
  line-height: 1.7;
  color: rgba(219, 234, 254, 0.88);
}

.homeworks-context-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.context-item {
  display: flex;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 22px;
  background: rgba(15, 23, 42, 0.2);
  border: 1px solid rgba(191, 219, 254, 0.18);
}

.context-item--accent {
  background: rgba(255, 255, 255, 0.14);
}

.context-item .material-symbols-outlined {
  font-size: 22px;
}

.context-item strong {
  display: block;
  font-size: 14px;
  color: #ffffff;
}

.context-item p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(219, 234, 254, 0.8);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.summary-card {
  padding: 24px;
  border-radius: 28px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.summary-card--primary {
  display: flex;
  gap: 16px;
  color: #ffffff;
  background: linear-gradient(135deg, #1d4ed8, #3b82f6);
}

.summary-card--light,
.summary-card--accent {
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(191, 219, 254, 0.5);
}

.summary-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  flex-shrink: 0;
}

.summary-card__icon--green {
  background: rgba(220, 252, 231, 0.9);
  color: #15803d;
}

.summary-card__icon--amber {
  background: rgba(254, 243, 199, 0.9);
  color: #b45309;
}

.summary-card__label {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
}

.summary-card--primary .summary-card__label,
.summary-card--primary .summary-card__desc,
.summary-card--primary .summary-card__value {
  color: #ffffff;
}

.summary-card__value {
  margin-top: 8px;
  font-size: 30px;
  font-weight: 800;
  color: #0f172a;
}

.summary-card__value--small {
  font-size: 24px;
}

.summary-card__desc {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

.summary-card__desc--spaced {
  margin-top: 18px;
}

.summary-card__header-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.summary-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.toolbar-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 22px 24px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 18px 32px rgba(15, 23, 42, 0.06);
}

.toolbar-card__main {
  display: flex;
  gap: 16px;
  justify-content: space-between;
  align-items: center;
}

.toolbar-search {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  padding: 0 16px;
  min-height: 52px;
  border-radius: 18px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.toolbar-search .material-symbols-outlined {
  color: #64748b;
}

.toolbar-search :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
}

.toolbar-filters {
  display: flex;
  gap: 12px;
}

.toolbar-filters :deep(.el-select) {
  width: 168px;
}

.toolbar-card__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #64748b;
  font-size: 13px;
}

.toolbar-divider {
  width: 1px;
  height: 14px;
  background: #cbd5e1;
}

.homeworks-console {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.homeworks-console__header h2 {
  margin: 0;
  font-size: 22px;
  color: #0f172a;
}

.homeworks-console__header p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 14px;
}

.homework-list-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.homework-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 200px;
  gap: 16px;
  padding: 24px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 18px 34px rgba(15, 23, 42, 0.05);
}

.homework-row__primary {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.homework-row__title-line {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.homework-row__title-line h3 {
  margin: 0;
  font-size: 20px;
  color: #0f172a;
}

.homework-row__title-line p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.7;
}

.homework-row__meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.homework-row__meta-grid > div {
  padding: 14px 16px;
  border-radius: 20px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.homework-row__label {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
}

.homework-row__meta-grid strong {
  color: #0f172a;
  font-size: 14px;
}

.homework-row__supporting {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.homework-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.homework-chip--muted {
  background: #eff6ff;
}

.homework-row__actions {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
}

.homework-row__actions .el-button {
  width: 100%;
  margin-left: 0;
}

.homework-dialog :deep(.el-dialog) {
  border-radius: 28px;
  overflow: hidden;
}

.homework-dialog :deep(.el-dialog__header) {
  display: none;
}

.homework-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.homework-dialog__shell {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
}

.homework-dialog__header,
.homework-dialog__summary-card,
.homework-dialog__group,
.homework-dialog__footer {
  border: 1px solid #e2e8f0;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.96);
}

.homework-dialog__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px;
  background: linear-gradient(135deg, rgba(29, 78, 216, 0.08), rgba(239, 246, 255, 0.96));
}

.homework-dialog__eyebrow,
.homework-dialog__label {
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.homework-dialog__header h2 {
  margin: 8px 0 0;
  color: #0f172a;
  font-size: 26px;
  font-weight: 800;
}

.homework-dialog__desc {
  margin: 8px 0 0;
  max-width: 560px;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.homework-dialog__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 8px;
}

.homework-dialog__badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 800;
}

.homework-dialog__badge--muted {
  background: #f1f5f9;
  color: #475569;
}

.homework-dialog__summary-grid,
.homework-dialog__grid--2col,
.homework-dialog__grid--3col {
  display: grid;
  gap: 14px;
}

.homework-dialog__summary-grid,
.homework-dialog__grid--2col {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.homework-dialog__grid--3col {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.homework-dialog__summary-card,
.homework-dialog__group {
  padding: 18px;
}

.homework-dialog__summary-card strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.homework-dialog__summary-card p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.homework-dialog__form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.homework-dialog__group-title {
  margin-bottom: 14px;
  color: #0f172a;
  font-size: 16px;
  font-weight: 800;
}

.homework-dialog__group-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 14px;
}

.homework-dialog__group-head .homework-dialog__group-title {
  margin-bottom: 6px;
}

.homework-dialog__group-desc {
  margin: 0;
  max-width: 560px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.homework-dialog__group-lock {
  margin: 6px 0 0;
  color: #b45309;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.6;
}

.homework-dialog__group-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.homework-dialog__form :deep(.el-form-item) {
  margin-bottom: 0;
}

.homework-dialog__form :deep(.el-form-item__label) {
  margin-bottom: 8px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.homework-dialog__form :deep(.el-input__wrapper),
.homework-dialog__form :deep(.el-select__wrapper),
.homework-dialog__form :deep(.el-textarea__inner) {
  border-radius: 14px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  background: #f8fafc;
}

.homework-dialog__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
}

.homework-dialog__footer-note {
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.homework-dialog__footer-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 1200px) {
  .summary-grid,
  .homeworks-context-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .toolbar-card__main,
  .homework-row {
    grid-template-columns: 1fr;
    display: grid;
  }

  .toolbar-filters,
  .homework-row__actions {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    display: grid;
  }
}

@media (max-width: 900px) {
  .homeworks-hero,
  .summary-card,
  .toolbar-card,
  .homework-row {
    padding: 20px;
    border-radius: 24px;
  }

  .homeworks-hero__heading,
  .toolbar-card__main,
  .toolbar-filters,
  .homework-row__title-line,
  .homework-row__actions {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  .summary-grid,
  .homeworks-context-strip,
  .homework-row__meta-grid {
    grid-template-columns: 1fr;
  }

  .homework-dialog__header,
  .homework-dialog__footer {
    flex-direction: column;
    align-items: stretch;
  }

  .homework-dialog__badges {
    justify-content: flex-start;
  }

  .homework-dialog__summary-grid,
  .homework-dialog__grid--2col,
  .homework-dialog__grid--3col {
    grid-template-columns: 1fr;
  }
}
</style>

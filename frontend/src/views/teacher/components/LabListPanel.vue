<template>
  <WorkbenchShell
    title="实验台账"
    description="按班级维护实验、题项与发布状态。"
  >
    <template #actions>
      <ActionButton label="新建实验" tone="primary" @click="openCreateDialog" />
    </template>
    <template #meta>
      <span class="workbench-meta__item"><strong>{{ workbench.meta.value.total }}</strong> 任务总数</span>
      <span class="workbench-meta__item"><strong>{{ workbench.meta.value.filtered }}</strong> 筛选结果</span>
      <span class="workbench-meta__item"><strong>{{ workbench.meta.value.draft }}</strong> 草稿</span>
      <span class="workbench-meta__item"><strong>{{ workbench.meta.value.inProgress }}</strong> 进行中</span>
      <span class="workbench-meta__item workbench-meta__item--accent"><strong>{{ workbench.meta.value.submissions }}</strong> 累计提交</span>
      <span class="workbench-meta__item">{{ workbench.meta.value.hint }}</span>
    </template>
    <template #toolbar>
      <div class="workbench-toolbar__main">
        <div class="workbench-toolbar__search">
          <el-input v-model="workbench.filters.keyword.value" placeholder="搜索标题、要求、内容或班级" clearable />
        </div>
        <div class="workbench-toolbar__filters">
          <div class="workbench-filter">
            <label>状态</label>
            <el-select v-model="workbench.filters.status.value" clearable placeholder="全部状态">
              <el-option label="草稿" :value="ACTIVITY_STATUS.DRAFT" />
              <el-option label="已发布" :value="ACTIVITY_STATUS.PUBLISHED" />
              <el-option label="已关闭" :value="ACTIVITY_STATUS.CLOSED" />
            </el-select>
          </div>
          <div class="workbench-filter">
            <label>班级</label>
            <el-select v-model="workbench.filters.classId.value" clearable placeholder="全部班级">
              <el-option v-for="option in workbench.classOptions.value" :key="option.id" :label="option.label" :value="option.id" />
            </el-select>
          </div>
          <div class="workbench-filter">
            <label>时效</label>
            <el-select v-model="workbench.filters.deadlineBucket.value" placeholder="全部时效">
              <el-option label="全部时效" value="ALL" />
              <el-option label="即将截止" value="UPCOMING" />
              <el-option label="已截止" value="OVERDUE" />
              <el-option label="未设置截止" value="UNDATED" />
            </el-select>
          </div>
        </div>
      </div>
      <div class="workbench-toolbar__meta">
        <span>{{ workbench.meta.value.hint }}</span>
        <span>·</span>
        <el-button link type="primary" @click="resetFilters">清空筛选</el-button>
      </div>
    </template>

    <div class="workbench-table-card" v-loading="loading">
      <div class="workbench-table-card__header">
        <div>
          <h2>实验台账</h2>
          <p>核对题项数量、时间窗与发布动作。</p>
        </div>
      </div>
      <el-table
        :data="workbench.filteredRows.value"
        empty-text="暂无实验"
        :header-cell-style="headerCellStyle"
      >
        <el-table-column label="任务信息" min-width="340">
          <template #default="{ row }">
            <div class="workbench-cell__title-row">
              <span class="workbench-cell__title">{{ row.title }}</span>
              <StatusPill :tone="toneOf(row.status)" :label="row.statusLabel" />
            </div>
            <div class="workbench-cell__meta">
              <span>{{ row.className }}</span>
              <span>{{ experimentTypeLabel(row.raw.experimentType) }}</span>
              <span>{{ scoreVisibilityLabel(row.raw.scoreVisibilityMode) }}</span>
            </div>
            <p class="workbench-cell__desc">{{ row.description || '暂无实验要求' }}</p>
          </template>
        </el-table-column>

        <el-table-column label="任务节奏" min-width="260">
          <template #default="{ row }">
            <div class="workbench-timeline">
              <div class="workbench-timeline__item"><span>开始</span><strong>{{ formatDateTime(row.startAt) }}</strong></div>
              <div class="workbench-timeline__item"><span>截止</span><strong>{{ formatDateTime(row.endAt) }}</strong></div>
              <div class="workbench-timeline__item"><span>题项</span><strong>{{ row.itemCount ?? 0 }} 个</strong></div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="提交概览" width="200">
          <template #default="{ row }">
            <div class="workbench-submission">
              <div class="workbench-submission__count">—</div>
              <div class="workbench-submission__label">实验暂不统计</div>
              <div class="workbench-submission__hint">{{ updatedLabel(row.raw) }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="260" fixed="right" align="right">
          <template #default="{ row }">
            <div class="workbench-actions">
              <ActionButton
                v-for="item in workbench.actionsOf(row)"
                :key="item.key"
                :label="item.label"
                :tone="item.tone"
                @click="item.handler(row)"
              />
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
    </WorkbenchShell>

     <section class="labs-guidance-strip">
       <article class="side-card side-card--visual">
         <div class="side-card__header">
           <span class="material-symbols-outlined">account_tree</span>
           <strong>发布前核对</strong>
         </div>
           <p>先核对班级、步骤和状态，再进入后续动作。</p>
       </article>
     </section>

    <TaskEditorDialog v-model="createDialogVisible" :title="editingId ? '编辑实验' : '新建实验'">
      <el-form label-position="top">
        <el-form-item v-if="!editingId" label="创建方式">
          <el-radio-group v-model="createMode">
            <el-radio-button value="blank">空白创建</el-radio-button>
            <el-radio-button value="copy" :disabled="!rows.length">从已有实验复制</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <template v-if="createMode === 'copy' && !editingId">
          <el-form-item label="源实验" required>
            <el-select v-model="sourceLabId" class="app-control-full" placeholder="请选择源实验" @change="handleSourceLabChange">
              <el-option v-for="item in rows" :key="item.id" :label="sourceLabLabel(item)" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标班级" required>
            <el-select v-model="createForm.classId" class="app-control-full" placeholder="请选择目标班级">
              <el-option v-for="item in classOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="新实验名称" required>
            <el-input v-model="createForm.title" maxlength="30" show-word-limit />
          </el-form-item>
          <el-form-item label="发布状态">
            <el-select v-model="createForm.status" class="app-control-full">
              <el-option label="草稿" :value="ACTIVITY_STATUS.DRAFT" />
              <el-option label="已发布" :value="ACTIVITY_STATUS.PUBLISHED" :disabled="(sourceLab?.stepCount ?? 0) === 0" />
              <el-option label="已关闭" :value="ACTIVITY_STATUS.CLOSED" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="sourceLab" label="复制预览">
            <div class="lab-copy-preview">
              <span class="lab-meta-chip">{{ sourceLab.stepCount ?? 0 }} 个题项</span>
              <span class="lab-meta-chip" :class="sourceLab.materialId ? 'lab-meta-chip--success' : 'lab-meta-chip--muted'">
                {{ sourceLab.materialId ? '含实验指导' : '无实验指导' }}
              </span>
              <span class="lab-meta-chip" :class="sourceLab.summaryRequired ? 'lab-meta-chip--violet' : 'lab-meta-chip--muted'">
                {{ sourceLab.summaryRequired ? '要求实验小结' : '小结非必填' }}
              </span>
              <span class="lab-meta-chip">{{ scoreVisibilityLabel(sourceLab.scoreVisibilityMode) }}</span>
              <p v-if="(sourceLab.stepCount ?? 0) === 0" class="lab-copy-preview__warn">源实验没有题项，复制后需先配置题项</p>
            </div>
          </el-form-item>
        </template>

        <template v-else>
        <el-form-item label="实验名称" required>
          <el-input v-model="createForm.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="所属班级" required>
          <el-select v-model="createForm.classId" class="app-control-full" placeholder="请选择班级">
            <el-option v-for="item in classOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学科" :required="!editingId">
          <el-select v-model="createForm.subjectId" class="app-control-full" :placeholder="editingId ? '保持原值' : '请选择学科'">
            <el-option v-if="editingId" label="保持原值" :value="0" />
            <el-option v-for="item in subjectOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="实验类型" required>
          <el-select v-model="createForm.experimentType" class="app-control-full">
            <el-option v-for="item in experimentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="实验要求">
          <el-input v-model="createForm.description" type="textarea" :rows="4" maxlength="800" show-word-limit />
        </el-form-item>
        <el-form-item label="实验内容">
          <el-input v-model="createForm.experimentContent" type="textarea" :rows="5" maxlength="1500" show-word-limit />
        </el-form-item>
        <el-form-item label="实验指导">
          <div class="lab-guidance-form">
            <div class="lab-guidance-form__meta">
              <strong>{{ createForm.materialFileName || '未上传实验指导' }}</strong>
              <span>{{ createForm.materialId ? '已绑定资料记录' : '上传后将自动绑定到当前实验' }}</span>
            </div>
            <div class="lab-guidance-form__actions">
              <el-upload
                :show-file-list="false"
                :auto-upload="false"
                accept=".pdf,.doc,.docx,.ppt,.pptx,.zip,.rar,.7z,.txt,.md"
                :on-change="handleGuidanceSelected"
              >
                <el-button :loading="uploadingGuidance">{{ createForm.materialId ? '替换实验指导' : '上传实验指导' }}</el-button>
              </el-upload>
              <el-button v-if="createForm.materialId" link type="danger" @click="clearGuidance">清空</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="发布状态">
          <el-select v-model="createForm.status" class="app-control-full">
            <el-option label="草稿" :value="ACTIVITY_STATUS.DRAFT" />
            <el-option label="已发布" :value="ACTIVITY_STATUS.PUBLISHED" />
            <el-option label="已关闭" :value="ACTIVITY_STATUS.CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="实验小结">
          <el-switch
            v-model="createForm.summaryRequired"
            inline-prompt
            active-text="必填"
            inactive-text="选填"
          />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="createForm.startAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            clearable
            placeholder="留空表示不限开始时间"
            class="app-control-full"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="createForm.dueAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            clearable
            placeholder="留空表示不限截止时间"
            class="app-control-full"
          />
        </el-form-item>
        <el-form-item label="成绩可见">
          <el-select v-model="createForm.scoreVisibilityMode" class="app-control-full">
            <el-option label="即时出分" :value="SCORE_VISIBILITY_MODE.IMMEDIATE" />
            <el-option label="教师确认后可见" :value="SCORE_VISIBILITY_MODE.AFTER_TEACHER_CONFIRM" />
            <el-option label="截止后可见" :value="SCORE_VISIBILITY_MODE.AFTER_DEADLINE" />
            <el-option label="教师手动发布" :value="SCORE_VISIBILITY_MODE.MANUAL_RELEASE" />
          </el-select>
        </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">保存</el-button>
      </template>
    </TaskEditorDialog>
</template>

<script setup lang="ts">
import { inject } from 'vue';
import { LabsKey } from '@/composables/useLabs';
import WorkbenchShell from './workbench/WorkbenchShell.vue';
import ActionButton from './workbench/ActionButton.vue';
import StatusPill from './workbench/StatusPill.vue';
import TaskEditorDialog from './workbench/TaskEditorDialog.vue';
import { formatDateTime } from '@/utils/format';
const { ACTIVITY_STATUS, SCORE_VISIBILITY_MODE, loading, submitting, createDialogVisible, editingId, rows, classOptions, subjectOptions, uploadingGuidance, createMode, sourceLabId, experimentTypeOptions, createForm, sourceLab, sourceLabLabel, handleSourceLabChange, resetFilters, openCreateDialog, openEditDialog, experimentTypeLabel, handleGuidanceSelected, clearGuidance, submitCreate, goSteps, goResult, goBlankRegrade, toggleStatus, statusLabel, scoreVisibilityLabel, updatedLabel, workbench } = inject(LabsKey)!;
const toneOf = (status: string): 'draft' | 'published' | 'closed' | 'warning' =>
  status === 'DRAFT' ? 'draft' : status === 'PUBLISHED' ? 'published' : status === 'CLOSED' ? 'closed' : 'warning';
const headerCellStyle = () => ({ background: '#f8fafc', color: '#475569', fontWeight: 700 });
</script>

<style scoped>
.lab-row__subcopy {
  color: var(--color-text-placeholder);
}

.lab-meta-chip--success {
  background: var(--color-status-active-soft);
  color: #047857;
}

.lab-guidance-form {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: 1px dashed var(--color-border-soft);
  border-radius: 12px;
  background: var(--color-bg-app);
}

.lab-copy-preview {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 10px 12px;
  border: 1px dashed var(--color-border-soft);
  border-radius: 12px;
  background: var(--color-bg-app);
}

.lab-copy-preview__warn {
  flex-basis: 100%;
  margin: 2px 0 0;
  color: #d97706;
  font-size: var(--font-size-sm);
}

.lab-guidance-form__meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.lab-guidance-form__meta strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-base);
}

.lab-guidance-form__meta span {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.lab-guidance-form__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

@media (max-width: 1024px) {
  .lab-guidance-form {
    flex-direction: column;
    align-items: flex-start;
  }
}
.labs-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.labs-header,
.labs-console,
.side-card,
.summary-card {
  border: 1px solid var(--color-border-soft);
  border-radius: 16px;
  background: var(--color-surface);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
}

.labs-header {
  padding: 12px 14px 10px;
  background: linear-gradient(180deg, rgba(236, 254, 255, 0.72), rgba(255, 255, 255, 0.98));
}

.labs-header__top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
}

.labs-header__eyebrow {
  margin-bottom: 2px;
  color: var(--color-brand-teacher);
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: var(--letter-spacing-caps);
  text-transform: uppercase;
}

.labs-header h1 {
  margin: 0;
  color: var(--color-text-primary);
  font-family: 'DM Sans', sans-serif;}

.labs-header p {
  margin: 2px 0 0;
  max-width: 520px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-heading);
}

.labs-header__actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.labs-header__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.labs-header__meta-item,
.summary-chip,
.labs-console__hint {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--color-bg-app);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.labs-header__meta-item strong {
  margin-right: 4px;
  color: var(--color-text-primary);
}

.labs-header__meta-item--accent {
  background: var(--color-brand-teacher-soft);
  color: var(--color-brand-teacher);
}

.labs-console__header h2 {
  font-family: 'DM Sans', sans-serif;
}

.labs-console__header p,
.side-card p {
  margin: 2px 0 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-heading);
}

.labs-summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.summary-card {
  padding: 10px 12px;
}

.summary-card__icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.18);
}

.summary-card--primary {
  background: linear-gradient(135deg, var(--color-brand-teacher), var(--color-brand-teacher-accent));
  color: var(--color-text-inverse);
  border: none;
}

.summary-card--primary .summary-card__label,
.summary-card--primary .summary-card__desc {
  color: rgba(255, 255, 255, 0.82);
}

.summary-card--light .summary-card__icon,
.summary-card--accent .summary-chip {
  background: var(--color-brand-teacher-soft);
  color: var(--color-brand-teacher);
}

.summary-card__icon--amber {
  background: var(--color-warning-soft) !important;
  color: #d97706 !important;
}

.summary-card__icon--teal {
  background: var(--color-brand-teacher-soft) !important;
  color: var(--color-brand-teacher) !important;
}

.summary-card__icon--violet {
  background: var(--color-brand-admin-soft) !important;
  color: var(--color-brand-admin) !important;
}

.summary-card__label {
  margin-top: 6px;
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: var(--letter-spacing-caps);
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.summary-card__value {
  margin-top: 2px;
  font-size: var(--font-size-xl);
  font-weight: 700;
  color: inherit;
}

.summary-card__value--small {
  font-size: var(--font-size-lg);
}

.summary-card__desc {
  margin-top: 2px;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-heading);
}

.summary-card__desc--spaced {
  margin-top: 6px;
}

.summary-card__header-line {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
}

.toolbar-card__main {
  display: flex;
  gap: 10px;
  justify-content: space-between;
  align-items: center;
}

.toolbar-search {
  position: relative;
  flex: 1;
}

.toolbar-search .material-symbols-outlined {
  position: absolute;
  top: 50%;
  left: 12px;
  transform: translateY(-50%);
  z-index: 1;
  color: var(--color-text-placeholder);
}

.toolbar-search :deep(.el-input__wrapper) {
  padding-left: 32px;
  min-height: 34px;
}

.toolbar-filters {
  display: flex;
  gap: 8px;
}

.toolbar-filters .el-select {
  width: 150px;
}

.toolbar-card__meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 6px;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.toolbar-divider {
  width: 1px;
  height: 14px;
  background: var(--color-border-soft);
}

.labs-console {
  padding: 10px 12px 12px;
}

.labs-console__header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: flex-end;
  margin-bottom: 8px;
}

.labs-console__header h2 {
  margin: 0;
  color: var(--color-text-primary);}

.labs-console__hint {
  text-align: right;
}

.labs-table-shell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.lab-row {
  display: grid;
  grid-template-columns: minmax(0, 2.4fr) 0.9fr 1.15fr 248px;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid var(--color-border-soft);
  border-radius: 12px;
  background: linear-gradient(180deg, var(--color-surface), #fbfdff);
}

.lab-row__identity {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.lab-row__title-line {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.lab-row__meta-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 4px;
}

.lab-meta-chip {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 7px;
  border-radius: 999px;
  background: var(--color-info-soft);
  color: var(--color-info);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.lab-meta-chip--violet {
  background: var(--color-brand-admin-soft);
  color: var(--color-brand-admin);
}

.lab-meta-chip--muted {
  background: var(--color-bg-app);
  color: var(--color-text-muted);
}

.lab-row h3 {
  margin: 0;
  color: var(--color-text-primary);}

.lab-row p {
  margin: 3px 0 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-base);
  line-height: var(--line-height-heading);
}

.lab-class-chip {
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.lab-row__type,
.lab-row__status {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.meta-label {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-sm);
  font-weight: 700;
  letter-spacing: var(--letter-spacing-caps);
  text-transform: uppercase;
}

.lab-row__type strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.lab-row__type small,
.lab-row__status small {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-heading);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
}

.status-dot--success {
  background: var(--color-success);
}

.status-dot--warning {
  background: var(--color-warning);
}

.status-dot--muted {
  background: var(--color-text-placeholder);
}

.lab-row__actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  padding-right: 8px;
}

.lab-row__actions-grid {
  display: grid;
  width: 228px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 16px;
}

.lab-row__actions-grid :deep(.el-button) {
  width: 100%;
  height: 36px;
  min-width: 0;
  margin-left: 0;
}

.lab-row__toggle-action {
  min-width: 0 !important;
}

.labs-guidance-strip {
  display: grid;
  grid-template-columns: 1fr;
}

.side-card {
  padding: 8px 10px;
}

.side-card__header {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-primary);
}

.side-card__header .material-symbols-outlined {
  color: var(--color-brand-teacher);
}

@media (max-width: 1280px) {
  .labs-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .lab-row {
    grid-template-columns: minmax(0, 1.8fr) 0.9fr 1fr 228px;
  }

  .lab-row__status,
  .lab-row__actions {
    grid-column: span 1;
  }
}

@media (max-width: 1024px) {
  .labs-summary-grid,
  .lab-row {
    grid-template-columns: 1fr;
  }

  .labs-header__top,
  .toolbar-card__main,
  .labs-console__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .toolbar-filters {
    width: 100%;
    flex-wrap: wrap;
  }

  .toolbar-filters .el-select {
    width: 100%;
  }

  .lab-row__actions,
  .lab-row__actions-grid {
    align-items: flex-start;
    justify-content: flex-start;
  }

  .lab-row__actions-grid {
    width: 228px;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

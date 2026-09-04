<template>
  <div class="detail-page" v-loading="loading">
    <div class="page-header">
      <div class="page-header__main">
        <div class="detail-eyebrow">教师后台 / 实验报告详情</div>
        <h2>实验报告评分台</h2>
        <p>按题项连续查看学生作答、系统参考和教师确认分，主工作区直接面向评分操作。</p>
      </div>
      <div class="page-header__actions">
        <div v-if="detail" class="page-header__meta">
          <span class="page-header__meta-item"><strong>{{ detail.studentName || '-' }}</strong>{{ detail.studentNo || detail.studentUsername || '未标注学号' }}</span>
          <span class="page-header__meta-item">{{ statusLabel(detail.submitStatus) }}</span>
          <span class="page-header__meta-item page-header__meta-item--accent">当前总分 {{ detail.totalScore ?? '-' }}</span>
        </div>
        <div class="page-header__button-row">
          <el-button plain :disabled="!previousClassReport" @click="goReport(previousClassReport?.id)">上一个班级</el-button>
          <el-button plain :disabled="!previousStudentReport" @click="goReport(previousStudentReport?.id)">上一个学生</el-button>
          <el-button plain :disabled="!nextStudentReport" @click="goReport(nextStudentReport?.id)">下一个学生</el-button>
          <el-button plain :disabled="!nextClassReport" @click="goReport(nextClassReport?.id)">下一个班级</el-button>
          <el-button plain @click="goBatchGrade">批量快捷批改</el-button>
          <el-button @click="router.back()">返回批改队列</el-button>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && !detail" description="未获取到报告详情" />

    <template v-else-if="detail">
      <section class="summary-grid detail-summary-grid">
        <article class="summary-tile summary-tile--primary">
          <span class="summary-tile__label">实验</span>
          <strong>{{ detail.labTitle || '-' }}</strong>
          <small>{{ detail.className || '未标注班级' }}</small>
        </article>
        <article class="summary-tile">
          <span class="summary-tile__label">学生</span>
          <strong>{{ detail.studentName || '-' }}</strong>
          <small>{{ detail.studentNo || detail.studentUsername || '未标注学号' }}</small>
        </article>
        <article class="summary-tile">
          <span class="summary-tile__label">报告状态</span>
          <strong>{{ statusLabel(detail.submitStatus) }}</strong>
          <small>{{ detail.submittedAt ? `提交于 ${formatDateTime(detail.submittedAt)}` : '暂无提交时间' }}</small>
        </article>
        <article class="summary-tile">
          <span class="summary-tile__label">当前评分结果</span>
          <strong>{{ detail.totalScore ?? '-' }}</strong>
          <small>{{ detail.gradedAt ? `批改于 ${formatDateTime(detail.gradedAt)}` : '教师尚未完成题项评分确认' }}</small>
        </article>
      </section>

      <section class="detail-layout">
        <aside class="detail-sidebar">
          <article class="sidebar-card">
            <div class="sidebar-card__header">
              <span class="material-symbols-outlined">checklist</span>
              <strong>题项导航</strong>
            </div>
            <div class="sidebar-card__meta-row">
              <span>已加载题项</span>
              <strong>{{ answerItems.length }}</strong>
            </div>
            <div class="sidebar-card__meta-row">
              <span>页内评分</span>
              <strong>{{ computedTotalScore }}</strong>
            </div>
            <div class="sidebar-step-list">
              <button
                v-for="item in answerItems"
                :key="itemKey(item)"
                type="button"
                class="sidebar-step"
                @click="scrollToAnswer(itemKey(item))"
              >
                <div>
                  <span class="sidebar-step__eyebrow">题项 {{ item.stepNo || '-' }}</span>
                  <strong>{{ item.stepTitle || item.title || '未命名题项' }}</strong>
                </div>
                <span class="sidebar-step__score">{{ gradeFormMap[gradeKey(item)]?.score ?? resolvedInitialScore(item) }}/{{ maxScoreOf(item) }}</span>
              </button>
            </div>
          </article>
          <article class="sidebar-card">
            <div class="sidebar-card__header">
              <span class="material-symbols-outlined">subject</span>
              <strong>实验小结</strong>
            </div>
            <div class="sidebar-card__meta-row sidebar-card__meta-row--top">
              <el-tag :type="detail.summaryRequired ? 'warning' : 'info'" effect="light">
                {{ detail.summaryRequired ? '要求实验小结' : '实验小结非必填' }}
              </el-tag>
              <span class="sidebar-card__hint">{{ summaryStatusLabel }}</span>
            </div>
            <p class="detail-summary-text-card__body">{{ summaryPreview }}</p>
          </article>
        </aside>

        <div class="detail-main">
          <article
            v-for="item in answerItems"
            :id="`answer-${itemKey(item)}`"
            :key="itemKey(item)"
            class="answer-card"
            data-scroll-anchor="true"
          >
            <header class="answer-card__header">
              <div>
                <div class="answer-card__eyebrow">题项 {{ item.stepNo || '-' }} / {{ questionTypeLabel(item.questionType) }}</div>
                <h3>{{ item.stepTitle || item.title || '未命名题项' }}</h3>
              </div>
              <div class="answer-card__header-meta">
                <el-tag :type="scoreSourceTagType(item.scoreSource)" effect="light">
                  {{ scoreSourceLabel(item.scoreSource) }}
                </el-tag>
                <span class="answer-card__max-score">满分 {{ maxScoreOf(item) }}</span>
              </div>
            </header>

            <div class="answer-card__body">
              <section class="answer-pane">
                <div class="pane-title">学生答案</div>
                <div class="answer-text">{{ item.answerText || item.studentAnswer || '暂无作答内容' }}</div>
                <LabAnswerImageGallery
                  v-if="reportImages(item).length"
                  :images="reportImages(item)"
                />
              </section>

              <section class="insight-grid">
                <article class="insight-card">
                  <div class="pane-title">评分参考</div>
                  <div class="insight-line"><span>系统参考分</span><strong>{{ displaySuggestedScore(item) }}</strong></div>
                  <div class="insight-line"><span>相似度</span><strong>{{ formatPercent(item.plagiarismRate ?? item.similarityRate) }}</strong></div>
                  <div class="insight-detail">{{ item.autoJudgeDetail || '当前暂无系统评分说明。' }}</div>
                </article>

                <article class="insight-card insight-card--grading">
                  <div class="pane-title">教师逐项评分</div>
                  <el-form label-position="top" class="grading-form">
                    <el-form-item label="本题项得分" class="grading-form__item grading-form__item--score">
                      <el-input-number
                        v-model="gradeFormMap[gradeKey(item)].score"
                        :min="0"
                        :max="maxScoreOf(item)"
                        style="width: 100%"
                      />
                    </el-form-item>
                    <el-form-item label="本题项评语" class="grading-form__item">
                      <el-input
                        v-model="gradeFormMap[gradeKey(item)].teacherComment"
                        type="textarea"
                        :rows="3"
                        maxlength="500"
                        show-word-limit
                      />
                    </el-form-item>
                  </el-form>
                  <div class="footer-actions footer-actions--inline">
                    <el-button plain :disabled="!gradeFormMap[gradeKey(item)]?.answerId" @click="confirmSingleStep(item, false)">确认本题项</el-button>
                    <el-button plain :disabled="!gradeFormMap[gradeKey(item)]?.answerId || item.suggestedScore == null" @click="confirmSingleStep(item, true)">采纳建议分</el-button>
                  </div>
                </article>
              </section>
            </div>
          </article>

          <article id="report-final-card" class="final-card" data-scroll-anchor="true">
            <div class="final-card__header">
              <div>
                <h3>实验报告评语</h3>
                <p>确认所有题项评分后填写实验报告评语，提交后会刷新当前页面结果与批改状态。</p>
              </div>
              <div class="final-card__score">当前页内题项评分总和 {{ computedTotalScore }}</div>
            </div>
            <el-form label-position="top">
              <el-form-item label="实验报告评语">
                <el-input v-model="reportComment" type="textarea" :rows="4" maxlength="1000" show-word-limit />
              </el-form-item>
            </el-form>
            <div class="footer-actions">
              <el-button @click="router.back()">取消</el-button>
              <el-button type="primary" :loading="submitting" @click="submitGrade">提交评分</el-button>
            </div>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { confirmTeacherLabStepScore, getTeacherLabReportDetail, gradeTeacherLabReport, listTeacherLabReports } from '@/api/labs';
import LabAnswerImageGallery from '@/components/lab/LabAnswerImageGallery.vue';
import type { LabQuestionType, LabReportAnswerItem, LabReportDetail, LabReportItem, LabReportStatus } from '@/types/lab';
import { extractReportImages } from '@/utils/labReportImages';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const submitting = ref(false);
const detail = ref<LabReportDetail | null>(null);
const reports = ref<LabReportItem[]>([]);
const reportComment = ref('');
const gradeFormMap = reactive<Record<string, { answerId: number; score: number; teacherComment: string }>>({});
const pendingScrollContext = ref<{ anchorId: string | null; offset: number; scrollTop: number } | null>(null);

const reportId = computed(() => Number(route.params.id));

const answerItems = computed(() => detail.value?.answers || detail.value?.items || []);

const reportImages = (item: LabReportAnswerItem) => extractReportImages(item);

const computedTotalScore = computed(() => answerItems.value.reduce((sum, item) => sum + (gradeFormMap[gradeKey(item)]?.score ?? 0), 0));
const summaryStatusLabel = computed(() => detail.value?.summaryText?.trim() ? '已填写实验小结' : '未填写实验小结');
const summaryPreview = computed(() => detail.value?.summaryText?.trim() || '学生未填写实验小结。');
const reportQueue = computed(() => sortReports(reports.value));
const currentReportIndex = computed(() => reportQueue.value.findIndex((row) => row.id === reportId.value));
const previousStudentReport = computed(() => currentReportIndex.value > 0 ? reportQueue.value[currentReportIndex.value - 1] : null);
const nextStudentReport = computed(() => currentReportIndex.value >= 0 ? reportQueue.value[currentReportIndex.value + 1] || null : null);
const classGroups = computed(() => {
  const groups = new Map<string, LabReportItem[]>();
  reportQueue.value.forEach((row) => {
    const key = String(row.classId ?? row.className ?? 'unknown');
    groups.set(key, [...(groups.get(key) || []), row]);
  });
  return [...groups.entries()];
});
const currentClassIndex = computed(() => classGroups.value.findIndex(([, rows]) => rows.some((row) => row.id === reportId.value)));
const previousClassReport = computed(() => currentClassIndex.value > 0 ? classGroups.value[currentClassIndex.value - 1][1][0] : null);
const nextClassReport = computed(() => currentClassIndex.value >= 0 ? classGroups.value[currentClassIndex.value + 1]?.[1][0] || null : null);

const sortReports = (items: LabReportItem[]) => [...items].sort((left, right) => {
  const leftClass = left.className || String(left.classId ?? '');
  const rightClass = right.className || String(right.classId ?? '');
  const classResult = leftClass.localeCompare(rightClass, 'zh-CN');
  if (classResult !== 0) return classResult;
  const leftStudent = left.studentNo || left.studentUsername || left.studentName || String(left.id);
  const rightStudent = right.studentNo || right.studentUsername || right.studentName || String(right.id);
  return leftStudent.localeCompare(rightStudent, 'zh-CN', { numeric: true });
});

const resolveItemId = (item: LabReportAnswerItem) => item.answerId ?? item.id ?? item.stepId;
const isValidItemId = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value);
const itemKey = (item: LabReportAnswerItem) => String(resolveItemId(item) ?? 0);
const gradeKey = (item: LabReportAnswerItem) => itemKey(item);
const maxScoreOf = (item: LabReportAnswerItem) => item.maxScore ?? item.stepScore ?? 100;
const resolvedInitialScore = (item: LabReportAnswerItem) => item.score ?? item.autoScore ?? item.suggestedScore ?? 0;

const statusLabel = (status?: LabReportStatus) => {
  if (status === 'SAVED') return '已保存';
  if (status === 'SUBMITTED') return '已提交';
  if (status === 'GRADED') return '已批改';
  return status || '未知';
};

const questionTypeLabel = (type?: LabQuestionType) => {
  if (type === 'TEXT') return '文本题';
  if (type === 'SHORT_ANSWER') return '简答题';
  if (type === 'SINGLE_CHOICE') return '单选题';
  if (type === 'MULTIPLE_CHOICE') return '多选题';
  if (type === 'TRUE_FALSE') return '判断题';
  if (type === 'FILL_BLANK') return '填空题';
  if (type === 'CODE') return '代码题';
  return type || '未标注题型';
};

const scoreSourceLabel = (source?: string | null) => {
  if (source === 'AUTO') return '自动评分';
  if (source === 'SUGGESTED') return '建议评分';
  if (source === 'TEACHER') return '教师评分';
  return '未标注';
};

const scoreSourceTagType = (source?: string | null) => {
  if (source === 'AUTO') return 'success';
  if (source === 'SUGGESTED') return 'warning';
  if (source === 'TEACHER') return 'info';
  return 'default';
};

const displaySuggestedScore = (item: LabReportAnswerItem) => {
  if (item.autoScore !== null && item.autoScore !== undefined) {
    return `自动 ${item.autoScore}`;
  }
  if (item.suggestedScore !== null && item.suggestedScore !== undefined) {
    return `建议 ${item.suggestedScore}`;
  }
  return '暂无';
};

const ensureGradeEntry = (item: LabReportAnswerItem) => {
  const key = gradeKey(item);
  if (!gradeFormMap[key]) {
    const resolvedId = resolveItemId(item);
    gradeFormMap[key] = {
      answerId: isValidItemId(resolvedId) ? resolvedId : 0,
      score: Number(resolvedInitialScore(item)),
      teacherComment: item.teacherComment || '',
    };
  }
};

const hydrateGradeForm = (items: LabReportAnswerItem[]) => {
  Object.keys(gradeFormMap).forEach((key) => delete gradeFormMap[key]);
  items.forEach(ensureGradeEntry);
};

const captureScrollContext = (preferredAnchorId?: string) => {
  if (typeof window === 'undefined') {
    return null;
  }

  const anchors = [...document.querySelectorAll<HTMLElement>('[data-scroll-anchor="true"]')]
    .filter((element) => element.id);

  if (!anchors.length) {
    return {
      anchorId: null,
      offset: 0,
      scrollTop: window.scrollY,
    };
  }

  let anchor = preferredAnchorId ? document.getElementById(preferredAnchorId) as HTMLElement | null : null;
  if (!anchor) {
    const viewportMarker = Math.max(window.innerHeight * 0.28, 120);
    anchor = anchors.find((element) => {
      const rect = element.getBoundingClientRect();
      return rect.top <= viewportMarker && rect.bottom >= viewportMarker;
    }) ?? anchors.find((element) => element.getBoundingClientRect().top > 0) ?? anchors[anchors.length - 1] ?? null;
  }

  if (!anchor?.id) {
    return {
      anchorId: null,
      offset: 0,
      scrollTop: window.scrollY,
    };
  }

  const anchorTop = anchor.getBoundingClientRect().top + window.scrollY;
  return {
    anchorId: anchor.id,
    offset: window.scrollY - anchorTop,
    scrollTop: window.scrollY,
  };
};

const restoreScrollContext = async () => {
  const context = pendingScrollContext.value;
  if (!context || typeof window === 'undefined') {
    return;
  }

  pendingScrollContext.value = null;
  await nextTick();
  await nextTick();

  if (context.anchorId) {
    const anchor = document.getElementById(context.anchorId);
    if (anchor) {
      const anchorTop = anchor.getBoundingClientRect().top + window.scrollY;
      window.scrollTo({ top: Math.max(anchorTop + context.offset, 0), behavior: 'auto' });
      return;
    }
  }

  window.scrollTo({ top: Math.max(context.scrollTop, 0), behavior: 'auto' });
};

const fetchData = async () => {
  loading.value = true;
  try {
    const [data, reportRows] = await Promise.all([
      getTeacherLabReportDetail(reportId.value),
      listTeacherLabReports(),
    ]);
    detail.value = data;
    reports.value = reportRows;
    reportComment.value = data.teacherComment || data.reportComment || '';
    hydrateGradeForm(data.answers || data.items || []);
  } finally {
    loading.value = false;
  }

  await restoreScrollContext();
  await scrollToQueryAnswer();
};

const formatPercent = (value?: number | null) => {
  if (value === null || value === undefined) {
    return '暂无查重';
  }
  return `${Math.round(value * 100)}%`;
};

const formatDateTime = (value?: string | null) => value ? value.replace('T', ' ').slice(0, 16) : '-';

const scrollToAnswer = (key: string) => {
  const element = document.getElementById(`answer-${key}`);
  element?.scrollIntoView({ behavior: 'smooth', block: 'start' });
};

const scrollToQueryAnswer = async () => {
  const answerId = route.query.answerId;
  if (typeof answerId !== 'string') {
    return;
  }
  await nextTick();
  scrollToAnswer(answerId);
};

const goBatchGrade = () => {
  router.push(`/teacher/lab-reports/${reportId.value}/batch-grade`);
};

const goReport = (targetReportId?: number) => {
  if (!targetReportId) return;
  router.push(`/teacher/lab-reports/${targetReportId}`);
};

const submitGrade = async () => {
  const items = answerItems.value
    .map(item => gradeFormMap[gradeKey(item)])
    .filter(entry => entry && isValidItemId(entry.answerId))
    .map(entry => ({
      answerId: entry.answerId,
      score: Number(entry.score || 0),
      teacherComment: entry.teacherComment.trim(),
    }));

  if (!items.length) {
    ElMessage.warning('当前报告没有可提交的题项评分记录');
    return;
  }

  submitting.value = true;
  pendingScrollContext.value = captureScrollContext('report-final-card');
  try {
    await gradeTeacherLabReport(reportId.value, {
      items,
      teacherComment: reportComment.value.trim(),
    });
    ElMessage.success('实验报告评分成功');
    await fetchData();
  } catch (error) {
    pendingScrollContext.value = null;
    throw error;
  } finally {
    submitting.value = false;
  }
};

const confirmSingleStep = async (item: LabReportAnswerItem, acceptSuggested: boolean) => {
  const entry = gradeFormMap[gradeKey(item)];
  if (!entry?.answerId) {
    ElMessage.warning('当前题项没有可确认的答案记录');
    return;
  }
  pendingScrollContext.value = captureScrollContext(`answer-${itemKey(item)}`);
  try {
    await confirmTeacherLabStepScore(reportId.value, {
      answerId: entry.answerId,
      score: acceptSuggested ? undefined : Number(entry.score || 0),
      teacherComment: entry.teacherComment.trim(),
      acceptSuggested,
    });
    ElMessage.success(acceptSuggested ? '已采纳题项建议分' : '题项评分已确认');
    await fetchData();
  } catch (error) {
    pendingScrollContext.value = null;
    throw error;
  }
};

watch(() => route.params.id, () => fetchData());
fetchData();
</script>

<style scoped>
.detail-page {
  --detail-primary: #0f766e;
  --detail-primary-soft: #ecfeff;
  --detail-border: #dbe5ef;
  --detail-text: #0f172a;
  --detail-muted: #64748b;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.page-header__actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.page-header__button-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.page-header__meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.page-header__meta-item {
  display: inline-flex;
  align-items: center;
   min-height: 28px;
   padding: 0 10px;
   border-radius: 999px;
   background: #f8fafc;
   color: #475569;
   font-size: 12px;
   font-weight: 700;
}

.page-header__meta-item strong {
  margin-right: 4px;
  color: var(--detail-text);
}

.page-header__meta-item--accent {
  background: var(--detail-primary-soft);
  color: var(--detail-primary);
}

.detail-eyebrow,
.summary-tile__label,
.answer-card__eyebrow,
.sidebar-step__eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.detail-eyebrow {
  margin-bottom: 4px;
  color: var(--detail-primary);
}

.page-header h2,
.answer-card h3,
.final-card h3,
.sidebar-card__header strong {
  margin: 0;
  font-family: 'DM Sans', sans-serif;
}

.page-header p,
.summary-tile small,
.sidebar-card p,
.insight-detail,
.final-card p {
  color: var(--detail-muted);
  line-height: 1.5;
}

.page-header p {
  margin: 6px 0 0;
  max-width: 680px;
}

.detail-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.detail-summary-text-card__body {
  margin: 8px 0 0;
  color: var(--detail-muted);
  line-height: 1.6;
  white-space: pre-wrap;
  font-size: 13px;
}

.summary-tile,
.sidebar-card,
.answer-card,
.final-card {
  border: 1px solid var(--detail-border);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.summary-tile {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 104px;
}

.summary-tile--primary {
  border: none;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
}

.summary-tile--primary .summary-tile__label,
.summary-tile--primary strong,
.summary-tile--primary small {
  color: #fff;
}

.summary-tile strong,
.answer-card__max-score,
.final-card__score {
  color: var(--detail-text);
  font-size: 18px;
}

.detail-layout {
  display: grid;
  grid-template-columns: 276px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.detail-sidebar {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sidebar-card {
  padding: 14px;
}

.sidebar-card__header {
  display: flex;
  gap: 8px;
  align-items: center;
  color: var(--detail-text);
}

.sidebar-card__meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
  color: var(--detail-muted);
  font-size: 12px;
}

.sidebar-card__meta-row--top {
  align-items: flex-start;
}

.sidebar-card__meta-row strong {
  color: var(--detail-text);
  font-size: 13px;
}

.sidebar-card__hint {
  font-size: 12px;
}

.sidebar-card__header .material-symbols-outlined {
  color: var(--detail-primary);
}

.sidebar-step-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

.sidebar-step {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--detail-border);
  border-radius: 12px;
  background: #f8fafc;
  text-align: left;
  cursor: pointer;
}

.sidebar-step strong {
  display: block;
  margin-top: 2px;
  color: var(--detail-text);
  font-size: 13px;
  line-height: 1.4;
}

.sidebar-step__eyebrow {
  color: var(--detail-primary);
}

.sidebar-step__score {
  color: #0f766e;
  font-weight: 700;
}

.detail-main {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.answer-card {
  padding: 16px;
}

.answer-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--detail-border);
}

.answer-card__eyebrow {
  margin-bottom: 4px;
  color: var(--detail-primary);
}

.answer-card__header-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-end;
}

.answer-card__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

.answer-pane {
  padding: 14px;
  border-radius: 14px;
  background: #f8fafc;
}

.pane-title {
  margin-bottom: 8px;
  color: var(--detail-text);
  font-weight: 700;
  font-size: 13px;
}

.answer-text,
.insight-detail {
  white-space: pre-wrap;
  line-height: 1.6;
  font-size: 14px;
}

.pane-file-row {
  margin-top: 8px;
  color: var(--detail-muted);
  font-size: 13px;
}

.pane-file-row a {
  color: var(--detail-primary);
  text-decoration: none;
}

.insight-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 12px;
}

.insight-card {
  padding: 14px;
  border-radius: 14px;
  border: 1px solid var(--detail-border);
  background: #fff;
}

.insight-card--grading {
  background: linear-gradient(180deg, #ffffff, #fbfdff);
}

.insight-line {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: var(--detail-muted);
  font-size: 13px;
}

.insight-line + .insight-line {
  margin-top: 8px;
}

.insight-line strong {
  color: var(--detail-text);
}

.insight-detail {
  margin-top: 10px;
}

.grading-form {
  display: grid;
  grid-template-columns: minmax(160px, 220px) minmax(0, 1fr);
  gap: 12px;
}

.grading-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.grading-form__item {
  min-width: 0;
}

.grading-form__item--score {
  max-width: 220px;
}

.final-card {
  padding: 16px;
}

.final-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.footer-actions--inline {
  margin-top: 12px;
}

@media (max-width: 1220px) {
  .detail-summary-grid,
  .detail-layout,
  .insight-grid {
    grid-template-columns: 1fr;
  }

  .detail-sidebar {
    position: static;
  }
}

@media (max-width: 900px) {
  .page-header,
  .answer-card__header,
  .final-card__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .page-header__actions,
  .page-header__meta {
    width: 100%;
    align-items: flex-start;
    justify-content: flex-start;
  }

  .page-header__button-row {
    justify-content: flex-start;
  }

  .grading-form {
    grid-template-columns: 1fr;
  }
}
</style>

<template>
  <div class="student-dashboard workbench-page">
    <section class="workbench-header student-dashboard__header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <h1>欢迎回来{{ displayNameSuffix }}。</h1>
          <p>优先看待完成任务、最近反馈和常用入口。</p>
        </div>
        <div class="workbench-header__actions">
          <el-button type="primary" @click="loadDashboard" :loading="loading">刷新面板</el-button>
        </div>
      </div>

      <div class="workbench-meta" v-if="localizedSummaryCards.length">
        <span v-for="card in localizedSummaryCards" :key="card.key" class="workbench-meta__item">
          <strong>{{ card.value }}</strong> {{ card.title }}
        </span>
      </div>
    </section>

    <el-skeleton v-if="loading && !loaded" :rows="8" animated />

    <template v-else>
      <div v-if="loadError" class="state-card">
        <AppEmptyState tone="error" title="学生工作台加载失败" :description="loadError">
          <template #actions>
            <el-button type="primary" @click="loadDashboard">重新加载</el-button>
          </template>
        </AppEmptyState>
      </div>

      <div v-else class="student-grid">
        <section class="student-main-column">
          <el-card class="student-card" shadow="never">
            <template #header>
              <div class="card-head-row">
                <div>
                  <span class="section-title">待办任务</span>
                  <p>{{ upcomingTasks.length }} 个待完成任务，按最近截止时间排序。</p>
                </div>
              </div>
            </template>
            <div v-if="upcomingTasks.length" class="task-stack">
              <button v-for="task in upcomingTasks" :key="`${task.type}-${task.title}`" type="button" class="student-task-card" @click="router.push(task.route)">
                <div class="student-task-card__icon">
                  <span class="material-symbols-outlined">task_alt</span>
                </div>
                <div>
                  <h3>{{ task.title }}</h3>
                  <p>{{ localizedTaskType(task.type) }} · {{ formatTimeLabel(task.dueAt) }}</p>
                </div>
                <span class="material-symbols-outlined student-task-card__arrow">arrow_forward_ios</span>
              </button>
            </div>
            <el-empty v-else description="当前没有待办任务" />
          </el-card>

          <el-card class="student-card" shadow="never">
            <template #header>
              <div class="card-head-row">
                  <span class="section-title">最近学习动态</span>
              </div>
            </template>
            <div v-if="recentFeedback.length" class="feedback-list">
              <div v-for="item in recentFeedback" :key="`${item.businessType}-${item.businessName}`" class="feedback-item">
                <div class="feedback-item__dot"></div>
                <div>
                  <strong>{{ item.businessName }}</strong>
                  <p>{{ localizedTaskType(item.businessType) }} · 得分 {{ item.score }} · {{ formatTimeLabel(item.gradedAt) }}</p>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂时没有新的评分反馈" />
          </el-card>
        </section>

        <aside class="student-side-column">
          <el-card class="student-card" shadow="never">
            <template #header>
              <div class="card-head-row">
                  <span class="section-title">快捷入口</span>
              </div>
            </template>
            <div class="quick-link-stack">
              <button v-for="link in quickLinks" :key="link.route" type="button" class="student-link" @click="router.push(link.route)">
                <div>
                  <strong>{{ localizedQuickLinkLabel(link.label) }}</strong>
                  <p>当前关联 {{ link.badge }}</p>
                </div>
                <span class="material-symbols-outlined">north_east</span>
              </button>
            </div>
          </el-card>

          <el-card class="student-card" shadow="never">
            <template #header>
              <div class="card-head-row">
                  <span class="section-title">最近资料</span>
              </div>
            </template>
            <el-table :data="recentMaterials" empty-text="暂无可下载资料" class="student-table">
              <el-table-column prop="title" label="资料名称" min-width="160" />
               <el-table-column label="类别" min-width="110">
                 <template #default="{ row }">
                   {{ localizedMaterialCategory(row.category) }}
                 </template>
               </el-table-column>
            </el-table>
          </el-card>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { getStudentDashboard } from '@/api/student';
import AppEmptyState from '@/components/ui/AppEmptyState.vue';

type SummaryCard = {
  key: string;
  title: string;
  value: number | string;
  description: string;
};

type UpcomingTask = {
  type: string;
  title: string;
  dueAt: string | null;
  route: string;
};

type FeedbackItem = {
  businessType: string;
  businessName: string;
  score: number;
  gradedAt: string | null;
};

type MaterialPreview = {
  title: string;
  category: string;
  fileName: string;
};

type QuickLink = {
  label: string;
  route: string;
  badge: number;
};

const authStore = useAuthStore();
const { user } = storeToRefs(authStore);
const router = useRouter();
const loading = ref(false);
const loaded = ref(false);
const loadError = ref('');
const summaryCards = ref<SummaryCard[]>([]);
const upcomingTasks = ref<UpcomingTask[]>([]);
const recentFeedback = ref<FeedbackItem[]>([]);
const recentMaterials = ref<MaterialPreview[]>([]);
const quickLinks = ref<QuickLink[]>([]);
const displayNameSuffix = computed(() => (user.value?.displayName ? `，${user.value.displayName}` : ''));

const translateStudentText = (text: string) => {
  const normalized = text.trim().toLowerCase();
  if (!normalized) return text;
  if (/[\u4e00-\u9fff]/.test(text)) return text;
  if (/lab/.test(normalized)) return '实验';
  if (/homework|assignment/.test(normalized)) return '作业';
  if (/exam|test|quiz/.test(normalized)) return '考试';
  if (/material|resource/.test(normalized)) return '资料';
  if (/task|todo/.test(normalized)) return '待办';
  if (/score|grade/.test(normalized)) return '成绩';
  if (/pending/.test(normalized)) return '待处理';
  if (/completed|finished/.test(normalized)) return '已完成';
  if (/upcoming/.test(normalized)) return '即将开始';
  return text;
};

const localizedSummaryCards = computed(() => summaryCards.value.map(card => ({
  ...card,
  title: translateStudentText(card.title),
  description: translateStudentText(card.description),
 })));

const localizedTaskType = (value: string) => translateStudentText(value);
const localizedQuickLinkLabel = (value: string) => translateStudentText(value);
const localizedMaterialCategory = (value: string) => translateStudentText(value);

const loadDashboard = async () => {
  loading.value = true;
  loadError.value = '';
  try {
    const data = await getStudentDashboard();
    summaryCards.value = data.summaryCards ?? [];
    upcomingTasks.value = data.upcomingTasks ?? [];
    recentFeedback.value = data.recentFeedback ?? [];
    recentMaterials.value = data.recentMaterials ?? [];
    quickLinks.value = data.quickLinks ?? [];
    loaded.value = true;
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '学生工作台加载失败';
  } finally {
    loading.value = false;
  }
};

const formatTimeLabel = (value: string | null) => {
  if (!value) {
    return '时间待定';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
};

onMounted(loadDashboard);
</script>

<style scoped>
.student-dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.student-dashboard h1 {
  margin: 0;
  font-size: 36px;
  font-weight: 800;
  color: #191c22;
}

.student-dashboard p {
  margin: 6px 0 0;
  color: #414753;
  font-size: 13px;
}

.student-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.92fr);
  gap: 16px;
}

.student-main-column,
.student-side-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.student-card {
  border: 1px solid rgba(193, 198, 214, 0.45);
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(25, 28, 34, 0.05);
}

.card-head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-head-row p {
  margin: 4px 0 0;
  color: #727785;
  font-size: 12px;
}

.section-title {
  font-size: 18px;
  font-weight: 800;
  color: #191c22;
}

.task-stack,
.feedback-list,
.quick-link-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.student-task-card,
.student-link {
  border: none;
  text-align: left;
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
  padding: 14px;
  border-radius: 16px;
  background: #f9f9ff;
  cursor: pointer;
}

.student-task-card__icon {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #a0f399;
  color: #217128;
}

.student-task-card h3,
.student-link strong {
  margin: 0;
  font-size: 16px;
  color: #191c22;
}

.student-task-card p,
.student-link p,
.feedback-item p {
  margin: 0;
  color: #727785;
  line-height: 1.7;
  font-size: 13px;
}

.student-task-card__arrow {
  margin-left: auto;
  color: #727785;
}

.feedback-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.feedback-item__dot {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  margin-top: 6px;
  background: #005bbf;
}

.feedback-item strong {
  color: #191c22;
}

.student-table :deep(th.el-table__cell) {
  background: #f9f9ff;
}

@media (max-width: 1024px) {
  .student-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .student-dashboard h1 { font-size: 28px; }
}
</style>

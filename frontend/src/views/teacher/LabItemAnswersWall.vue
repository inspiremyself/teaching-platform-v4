<template>
  <div class="answers-wall-page">
    <section class="answers-wall-header">
      <button type="button" class="answers-wall-back" @click="router.back()">
        <span class="material-symbols-outlined">arrow_back</span>
        <span>返回总览</span>
      </button>
      <div class="answers-wall-header__top">
        <div>
          <div class="answers-wall-eyebrow">教师后台 / 子题答案墙</div>
          <h1>{{ wall?.title || '子题答案墙' }}</h1>
          <p>
            {{ wall?.labTitle || '' }}
            <template v-if="wall"> · 第{{ wall.stepNo }}题</template>
          </p>
        </div>
        <el-button plain :loading="loading" @click="fetchWall">刷新</el-button>
      </div>
      <p v-if="wall?.content" class="answers-wall-prompt">{{ wall.content }}</p>
    </section>

    <section v-loading="loading" class="answers-wall-list">
      <template v-if="wall?.answers.length">
        <article v-for="entry in wall.answers" :key="entry.studentId" class="answers-wall-card">
          <header class="answers-wall-card__header">
            <div>
              <strong>{{ entry.studentName }}</strong>
              <span>{{ entry.studentNo }}</span>
            </div>
            <div class="answers-wall-card__meta">
              <el-tag v-if="entry.submitStatus" :type="statusTagType(entry.submitStatus)" effect="light">
                {{ statusLabel(entry.submitStatus) }}
              </el-tag>
              <span v-else class="answers-wall-muted">未提交</span>
              <span v-if="entry.score != null">得分 {{ entry.score }}</span>
            </div>
          </header>

          <div v-if="entry.answerText" class="answers-wall-text">{{ entry.answerText }}</div>
          <p v-else-if="!entry.images?.length" class="answers-wall-muted">暂无作答</p>

          <LabAnswerImageGallery :images="entry.images" />

          <p v-if="entry.teacherComment" class="answers-wall-comment">教师评语：{{ entry.teacherComment }}</p>
        </article>
      </template>
      <el-empty v-else-if="!loading" description="暂无学生答案" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getTeacherLabItemAnswers } from '@/api/labs';
import LabAnswerImageGallery from '@/components/lab/LabAnswerImageGallery.vue';
import type { LabItemAnswerWall, LabReportStatus } from '@/types/lab';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const wall = ref<LabItemAnswerWall | null>(null);

const labId = () => Number(route.params.labId);
const itemId = () => Number(route.params.itemId);

const fetchWall = async () => {
  loading.value = true;
  try {
    wall.value = await getTeacherLabItemAnswers(labId(), itemId());
  } finally {
    loading.value = false;
  }
};

const statusLabel = (status?: LabReportStatus | null) => {
  if (status === 'SUBMITTED') return '已提交';
  if (status === 'GRADED') return '已批改';
  if (status === 'SAVED') return '已保存';
  return status || '未知';
};

const statusTagType = (status?: LabReportStatus | null) => {
  if (status === 'SUBMITTED') return 'warning';
  if (status === 'GRADED') return 'success';
  if (status === 'SAVED') return 'info';
  return 'info';
};

onMounted(() => {
  void fetchWall();
});
</script>

<style scoped>
.answers-wall-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.answers-wall-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: #475569;
  cursor: pointer;
  padding: 0;
}

.answers-wall-header__top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.answers-wall-eyebrow {
  color: #64748b;
  font-size: 13px;
  margin-bottom: 8px;
}

.answers-wall-header h1 {
  margin: 0 0 8px;
  font-size: 28px;
}

.answers-wall-header p {
  margin: 0;
  color: #64748b;
}

.answers-wall-prompt {
  margin: 0;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 12px;
  color: #334155;
  line-height: 1.6;
}

.answers-wall-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.answers-wall-card {
  padding: 18px 20px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
}

.answers-wall-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.answers-wall-card__header strong {
  display: block;
  margin-bottom: 4px;
}

.answers-wall-card__header span {
  color: #64748b;
  font-size: 13px;
}

.answers-wall-card__meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-end;
  color: #64748b;
  font-size: 13px;
}

.answers-wall-text {
  white-space: pre-wrap;
  line-height: 1.7;
  color: #1e293b;
}

.answers-wall-comment {
  margin: 12px 0 0;
  color: #475569;
  font-size: 13px;
}

.answers-wall-muted {
  color: #94a3b8;
}
</style>

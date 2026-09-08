<template>
  <div class="page admin-dashboard workbench-page">
    <section class="workbench-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="admin-eyebrow">学校管理台 / 总览</div>
          <h1>校管工作台</h1>
          <p>{{ schoolName }} — 本校组织数据摘要</p>
        </div>
        <div class="workbench-header__actions">
          <el-button type="primary" :loading="loading" @click="loadSummary">刷新数据</el-button>
        </div>
      </div>

      <div class="workbench-meta">
        <span class="workbench-meta__item"><strong>{{ teacherCount }}</strong> 教师</span>
        <span class="workbench-meta__item"><strong>{{ classCount }}</strong> 班级</span>
        <span class="workbench-meta__item"><strong>{{ studentCount }}</strong> 学生</span>
        <span class="workbench-meta__item"><strong>{{ activeTeacherCount }}</strong> 在职教师</span>
        <span class="workbench-meta__item"><strong>{{ activeClassCount }}</strong> 启用班级</span>
      </div>
    </section>

    <div class="summary-grid" v-loading="loading">
      <article class="summary-card">
        <span class="material-symbols-outlined summary-card__icon">badge</span>
        <div>
          <div class="summary-card__label">教师总数</div>
          <strong class="summary-card__value">{{ teacherCount }}</strong>
          <p>{{ activeTeacherCount }} 名在职 · {{ disabledTeacherCount }} 名停用</p>
        </div>
        <router-link to="/admin/teachers" class="summary-card__link">管理教师</router-link>
      </article>

      <article class="summary-card">
        <span class="material-symbols-outlined summary-card__icon">groups</span>
        <div>
          <div class="summary-card__label">班级总数</div>
          <strong class="summary-card__value">{{ classCount }}</strong>
          <p>{{ activeClassCount }} 个启用 · {{ archivedClassCount }} 个归档</p>
        </div>
        <router-link to="/admin/classes" class="summary-card__link">管理班级</router-link>
      </article>

      <article class="summary-card">
        <span class="material-symbols-outlined summary-card__icon">person</span>
        <div>
          <div class="summary-card__label">学生总数</div>
          <strong class="summary-card__value">{{ studentCount }}</strong>
          <p>{{ activeStudentCount }} 名启用 · {{ disabledStudentCount }} 名停用</p>
        </div>
        <router-link to="/admin/students" class="summary-card__link">管理学生</router-link>
      </article>

      <article class="summary-card">
        <span class="material-symbols-outlined summary-card__icon">domain</span>
        <div>
          <div class="summary-card__label">学校信息</div>
          <strong class="summary-card__value">{{ schoolName }}</strong>
          <p>编码 {{ schoolCode || '—' }}</p>
        </div>
        <router-link to="/admin/school" class="summary-card__link">编辑学校</router-link>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { getSchool, listAdminClasses, listAdminStudents, listTeachers } from '@/api/admin';
import { useAuthStore } from '@/stores/auth';

const authStore = useAuthStore();
const { user } = storeToRefs(authStore);

const loading = ref(false);
const teacherCount = ref(0);
const activeTeacherCount = ref(0);
const disabledTeacherCount = ref(0);
const classCount = ref(0);
const activeClassCount = ref(0);
const archivedClassCount = ref(0);
const studentCount = ref(0);
const activeStudentCount = ref(0);
const disabledStudentCount = ref(0);
const schoolCode = ref('');

const schoolName = computed(() => user.value?.schoolName || '本校');

const loadSummary = async () => {
  loading.value = true;
  try {
    const [teachersRes, classesRes, studentsRes, schoolRes] = await Promise.all([
      listTeachers(),
      listAdminClasses(),
      listAdminStudents(),
      getSchool(),
    ]);

    teacherCount.value = teachersRes.length;
    activeTeacherCount.value = teachersRes.filter(item => item.status === 'ACTIVE').length;
    disabledTeacherCount.value = teachersRes.filter(item => item.status === 'DISABLED').length;

    classCount.value = classesRes.length;
    activeClassCount.value = classesRes.filter(item => item.status === 'ACTIVE').length;
    archivedClassCount.value = classesRes.filter(item => item.status === 'ARCHIVED').length;

    const students = studentsRes;
    studentCount.value = students.length;
    activeStudentCount.value = students.filter(item => item.status === 'ACTIVE').length;
    disabledStudentCount.value = students.filter(item => item.status === 'DISABLED').length;

    schoolCode.value = schoolRes.code;
  } finally {
    loading.value = false;
  }
};

onMounted(loadSummary);
</script>

<style scoped>
.admin-eyebrow {
  margin-bottom: 2px;
  color: var(--primary-color);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 14px;
  align-items: start;
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.summary-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--primary-light);
  color: var(--primary-color);
  font-size: 22px;
}

.summary-card__label {
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.summary-card__value {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 28px;
  font-weight: 800;
}

.summary-card p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
}

.summary-card__link {
  align-self: center;
  color: var(--primary-color);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>

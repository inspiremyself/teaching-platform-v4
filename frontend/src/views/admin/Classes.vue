<template>
  <div class="page admin-classes workbench-page">
    <section class="workbench-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="admin-eyebrow">学校管理台 / 班级管理</div>
          <h1>班级管理</h1>
          <p>维护班级信息，并为每班指派班主任与学科教师。</p>
        </div>
        <div class="workbench-header__actions">
          <el-button type="primary" @click="openCreateDialog">
            <span class="material-symbols-outlined" style="font-size: 16px; margin-right: 4px;">add</span>
            新建班级
          </el-button>
        </div>
      </div>

      <div class="workbench-meta">
        <span class="workbench-meta__item"><strong>{{ rows.length }}</strong> 班级总数</span>
        <span class="workbench-meta__item"><strong>{{ activeCount }}</strong> 启用中</span>
        <span class="workbench-meta__item"><strong>{{ totalStudents }}</strong> 学生总数</span>
      </div>
    </section>

    <section class="toolbar-card">
      <div class="toolbar-row">
        <el-input v-model="searchQuery" placeholder="搜索班级名称或代码" clearable style="max-width: 280px" />
        <el-select v-model="statusFilter" clearable placeholder="全部状态" style="width: 140px">
          <el-option label="启用中" value="ACTIVE" />
          <el-option label="已归档" value="ARCHIVED" />
        </el-select>
      </div>
    </section>

    <section class="table-card">
      <el-table :data="filteredRows" v-loading="loading" empty-text="暂无班级数据">
        <el-table-column label="班级" min-width="220">
          <template #default="{ row }">
            <div class="identity-cell">
              <strong>{{ row.name }}</strong>
              <span>代码 {{ row.code }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="教师" min-width="280">
          <template #default="{ row }">
            <div class="teacher-tags">
              <span v-for="(t, idx) in row.teachers" :key="idx" class="teacher-tag">
                {{ t.teacherName }}
                <em>{{ t.role === 'HEAD' ? '班主任' : t.subjectName || '学科' }}</em>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="studentCount" label="学生" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用中' : '已归档' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="right" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="warning" @click="toggleStatus(row)">
              {{ row.status === 'ACTIVE' ? '归档' : '恢复' }}
            </el-button>
            <el-button link type="danger" @click="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑班级' : '新建班级'" width="720px" destroy-on-close>
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="班级名称">
            <el-input v-model="form.name" maxlength="128" placeholder="例如：软件工程 2026 级 1 班" />
          </el-form-item>
          <el-form-item v-if="!editingId" label="班级代码">
            <el-input v-model="form.code" maxlength="64" placeholder="例如：SE2026-1" />
          </el-form-item>
          <el-form-item v-if="editingId" label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="启用中" value="ACTIVE" />
              <el-option label="已归档" value="ARCHIVED" />
            </el-select>
          </el-form-item>
        </div>

        <div class="teachers-section">
          <div class="teachers-section__header">
            <strong>教师指派</strong>
            <el-button size="small" @click="addTeacherRow">添加教师</el-button>
          </div>
          <el-table :data="form.teachers" size="small" empty-text="请至少添加一名教师">
            <el-table-column label="教师" min-width="180">
              <template #default="{ row }">
                <el-select v-model="row.teacherUserId" placeholder="选择教师" filterable style="width: 100%">
                  <el-option
                    v-for="t in activeTeachers"
                    :key="t.id"
                    :label="`${t.displayName} (${t.username})`"
                    :value="t.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="角色" width="140">
              <template #default="{ row }">
                <el-select v-model="row.role" style="width: 100%" @change="onRoleChange(row)">
                  <el-option label="班主任" value="HEAD" />
                  <el-option label="学科教师" value="SUBJECT" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="学科名称" min-width="160">
              <template #default="{ row }">
                <el-input
                  v-model="row.subjectName"
                  :disabled="row.role === 'HEAD'"
                  placeholder="role=SUBJECT 时必填"
                />
              </template>
            </el-table-column>
            <el-table-column label="" width="60" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" :disabled="form.teachers.length <= 1" @click="removeTeacherRow($index)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  createAdminClass,
  deleteAdminClass,
  listAdminClasses,
  listTeachers,
  updateAdminClass,
} from '@/api/admin';
import type { AdminClass, AdminTeacher, ClassTeacherInput, ClassTeacherRole } from '@/types/admin';
import type { ClassStatus } from '@/types/class';

interface TeacherFormRow {
  teacherUserId: number | undefined;
  role: ClassTeacherRole;
  subjectName: string;
}

const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const rows = ref<AdminClass[]>([]);
const teachers = ref<AdminTeacher[]>([]);
const searchQuery = ref('');
const statusFilter = ref<ClassStatus | ''>('');

const form = reactive({
  name: '',
  code: '',
  status: 'ACTIVE' as ClassStatus,
  teachers: [] as TeacherFormRow[],
});

const activeTeachers = computed(() => teachers.value.filter(item => item.status === 'ACTIVE'));
const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase();
  return rows.value.filter(row => {
    const matchesKeyword =
      !keyword ||
      row.name.toLowerCase().includes(keyword) ||
      row.code.toLowerCase().includes(keyword);
    const matchesStatus = !statusFilter.value || row.status === statusFilter.value;
    return matchesKeyword && matchesStatus;
  });
});
const activeCount = computed(() => rows.value.filter(item => item.status === 'ACTIVE').length);
const totalStudents = computed(() => rows.value.reduce((sum, row) => sum + (row.studentCount || 0), 0));

const createEmptyTeacherRow = (): TeacherFormRow => ({
  teacherUserId: undefined,
  role: 'SUBJECT',
  subjectName: '',
});

const resetForm = () => {
  form.name = '';
  form.code = '';
  form.status = 'ACTIVE';
  form.teachers = [createEmptyTeacherRow()];
  editingId.value = null;
};

const onRoleChange = (row: TeacherFormRow) => {
  if (row.role === 'HEAD') {
    row.subjectName = '';
  }
};

const addTeacherRow = () => {
  form.teachers.push(createEmptyTeacherRow());
};

const removeTeacherRow = (index: number) => {
  if (form.teachers.length <= 1) return;
  form.teachers.splice(index, 1);
};

const toTeacherInputs = (): ClassTeacherInput[] | null => {
  const headCount = form.teachers.filter(item => item.role === 'HEAD').length;
  if (headCount > 1) {
    ElMessage.warning('每个班级最多只能有一名班主任');
    return null;
  }

  const inputs: ClassTeacherInput[] = [];
  const seen = new Set<number>();

  for (const row of form.teachers) {
    if (!row.teacherUserId) {
      ElMessage.warning('请为每行选择教师');
      return null;
    }
    if (seen.has(row.teacherUserId)) {
      ElMessage.warning('同一教师不能重复加入同一班级');
      return null;
    }
    seen.add(row.teacherUserId);

    if (row.role === 'SUBJECT' && !row.subjectName.trim()) {
      ElMessage.warning('学科教师必须填写学科名称');
      return null;
    }

    inputs.push({
      teacherUserId: row.teacherUserId,
      role: row.role,
      subjectName: row.role === 'SUBJECT' ? row.subjectName.trim() : null,
    });
  }

  if (!inputs.length) {
    ElMessage.warning('班级至少需要绑定一名教师');
    return null;
  }

  return inputs;
};

const fetchData = async () => {
  loading.value = true;
  try {
    const [classesRes, teachersRes] = await Promise.all([listAdminClasses(), listTeachers()]);
    rows.value = classesRes;
    teachers.value = teachersRes;
  } finally {
    loading.value = false;
  }
};

const openCreateDialog = () => {
  resetForm();
  dialogVisible.value = true;
};

const openEditDialog = (row: AdminClass) => {
  editingId.value = row.id;
  form.name = row.name;
  form.code = row.code;
  form.status = row.status;
  form.teachers = row.teachers.length
    ? row.teachers.map(item => ({
        teacherUserId: item.teacherUserId,
        role: item.role,
        subjectName: item.subjectName || '',
      }))
    : [createEmptyTeacherRow()];
  dialogVisible.value = true;
};

const submit = async () => {
  if (!form.name.trim()) {
    ElMessage.warning('请输入班级名称');
    return;
  }
  if (!editingId.value && !form.code.trim()) {
    ElMessage.warning('请输入班级代码');
    return;
  }

  const teacherInputs = toTeacherInputs();
  if (!teacherInputs) return;

  submitting.value = true;
  try {
    if (editingId.value) {
      await updateAdminClass(editingId.value, {
        name: form.name.trim(),
        status: form.status,
        teachers: teacherInputs,
      });
      ElMessage.success('班级更新成功');
    } else {
      await createAdminClass({
        name: form.name.trim(),
        code: form.code.trim(),
        teachers: teacherInputs,
      });
      ElMessage.success('班级创建成功');
    }
    dialogVisible.value = false;
    resetForm();
    await fetchData();
  } finally {
    submitting.value = false;
  }
};

const toggleStatus = async (row: AdminClass) => {
  const nextStatus: ClassStatus = row.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE';
  const teachersPayload: ClassTeacherInput[] = row.teachers.map(item => ({
    teacherUserId: item.teacherUserId,
    role: item.role,
    subjectName: item.subjectName,
  }));
  await updateAdminClass(row.id, { name: row.name, status: nextStatus, teachers: teachersPayload });
  ElMessage.success('班级状态更新成功');
  await fetchData();
};

const remove = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认删除该班级吗？班内有学生时将无法删除。', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
    await deleteAdminClass(id);
    ElMessage.success('班级删除成功');
    await fetchData();
  } catch {
    // cancelled or API error
  }
};

onMounted(fetchData);
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

.toolbar-card,
.table-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
}

.toolbar-card {
  padding: 12px 14px;
}

.toolbar-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.table-card {
  padding: 0 0 8px;
  overflow: hidden;
}

.identity-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.identity-cell strong {
  color: #0f172a;
  font-size: 13px;
}

.identity-cell span {
  color: #64748b;
  font-size: 11px;
}

.teacher-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.teacher-tag {
  display: inline-flex;
  flex-direction: column;
  gap: 1px;
  padding: 4px 8px;
  border-radius: 8px;
  background: var(--primary-light);
  color: var(--primary-color);
  font-size: 11px;
  font-weight: 700;
}

.teacher-tag em {
  font-style: normal;
  font-size: 10px;
  font-weight: 600;
  opacity: 0.8;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.teachers-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}

.teachers-section__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

@media (max-width: 768px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>

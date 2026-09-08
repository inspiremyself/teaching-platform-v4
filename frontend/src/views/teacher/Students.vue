<template>
  <div class="page students-console workbench-page">
    <section class="workbench-header students-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="students-header__eyebrow">教师控制台 / 班级名册 / 学生管理</div>
          <h1>学生工作台</h1>
          <p>按班级维护学生名册，集中处理导入与状态切换。</p>
        </div>
        <div class="workbench-header__actions">
          <button class="console-button console-button--secondary" type="button" @click="openImportDialog" :disabled="!selectedClassId">
            <span class="material-symbols-outlined">upload_file</span>
            导入学生
          </button>
          <button class="console-button console-button--primary" type="button" @click="openCreateDialog">
            <span class="material-symbols-outlined">person_add</span>
            新增学生
          </button>
        </div>
      </div>

      <div class="workbench-meta students-meta">
        <span class="workbench-meta__item workbench-meta__item--accent"><strong>{{ selectedClassSummary.title }}</strong></span>
        <span class="workbench-meta__item"><strong>{{ filteredRows.length }}</strong> 可见学生</span>
        <span class="workbench-meta__item"><strong>{{ activeCount }}</strong> 启用中</span>
        <span class="workbench-meta__item"><strong>{{ disabledCount }}</strong> 停用中</span>
        <span class="workbench-meta__item">{{ classCoverageText }}</span>
      </div>
    </section>

    <section class="console-grid console-grid--refactored">

      <div class="toolbar-card">
        <div class="toolbar-card__main">
          <div class="toolbar-search">
            <span class="material-symbols-outlined">search</span>
            <el-input v-model="searchQuery" placeholder="按账号、姓名或班级搜索" clearable />
          </div>
          <div class="toolbar-filters">
            <div class="toolbar-filter">
              <label>班级</label>
              <el-select v-model="selectedClassId" clearable placeholder="全部班级" @change="fetchStudents">
                <el-option v-for="item in classes" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
              </el-select>
            </div>
            <div class="toolbar-filter toolbar-filter--compact">
              <label>状态</label>
              <el-select v-model="statusFilter" clearable placeholder="全部状态">
                <el-option label="启用" value="ACTIVE" />
                <el-option label="停用" value="DISABLED" />
              </el-select>
            </div>
          </div>
        </div>
        <div class="toolbar-card__meta">
          <span>当前筛选结果 {{ filteredRows.length }} / {{ rows.length }}</span>
          <span class="toolbar-card__dot"></span>
          <span>{{ selectedClassId ? '已锁定导入班级' : '未锁定班级' }}</span>
          <span class="toolbar-card__dot"></span>
          <span>{{ statusFilter ? `状态：${statusFilter === 'ACTIVE' ? '启用' : '停用'}` : '全部状态' }}</span>
        </div>
      </div>

      <div class="table-card">
        <div class="table-card__header">
          <div>
            <h2>学生名册</h2>
            <p>{{ selectedClassSummary.subtitle }}</p>
          </div>
        </div>

        <el-table
          :data="filteredRows"
          v-loading="loading"
          empty-text="暂无学生数据"
          class="students-table"
          :header-cell-style="headerCellStyle"
        >
          <el-table-column label="学生信息" min-width="320">
            <template #default="{ row }">
              <div class="student-cell">
                <div :class="['student-avatar', row.status === 'ACTIVE' ? 'is-active' : 'is-disabled']">
                  {{ getAvatarText(row.displayName, row.username) }}
                </div>
                <div class="student-cell__body">
                  <div class="student-cell__name-row">
                    <span class="student-cell__name">{{ row.displayName }}</span>
                    <span :class="['status-pill', row.status === 'ACTIVE' ? 'status-pill--active' : 'status-pill--disabled']">
                      <span class="status-pill__dot"></span>
                      {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
                    </span>
                  </div>
                  <div class="student-cell__account">账号 {{ row.username }}</div>
                  <div class="student-cell__meta">
                    <span>
                      <span class="material-symbols-outlined">badge</span>
                      学生 ID #{{ row.id }}
                    </span>
                    <span>
                      <span class="material-symbols-outlined">school</span>
                      {{ getClassLabel(row.classId) }}
                    </span>
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="班级上下文" min-width="210">
            <template #default="{ row }">
              <div class="class-cell">
                <div class="class-cell__title">{{ getClassLabel(row.classId) }}</div>
                <div class="class-cell__subtitle">{{ getClassCode(row.classId) }}</div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="控制信息" min-width="190">
            <template #default="{ row }">
              <div class="control-cell">
                <div class="control-cell__item">
                  <span class="control-cell__label">导入归属</span>
                  <strong class="control-cell__value">{{ row.classId === selectedClassId ? '当前班级' : '跨班可见' }}</strong>
                </div>
                <div class="control-cell__item">
                  <span class="control-cell__label">操作状态</span>
                  <strong class="control-cell__value">{{ row.status === 'ACTIVE' ? '可登录' : '已冻结' }}</strong>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="180" fixed="right" align="right">
            <template #default="{ row }">
              <div class="action-cell">
                <button class="action-icon" type="button" title="编辑学生" @click="openEditDialog(row)">
                  <span class="material-symbols-outlined">edit</span>
                </button>
                <button class="action-icon" type="button" :title="row.status === 'ACTIVE' ? '停用学生' : '启用学生'" @click="toggleStatus(row)">
                  <span class="material-symbols-outlined">{{ row.status === 'ACTIVE' ? 'block' : 'check_circle' }}</span>
                </button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" width="700px" class="teacher-dialog" destroy-on-close>
      <div class="teacher-dialog__shell">
        <header class="teacher-dialog__header">
          <div>
            <p class="teacher-dialog__eyebrow">学生工作台 / {{ editingId ? '名册调整' : '新增录入' }}</p>
            <h2>{{ editingId ? '编辑学生资料' : '新增学生账号' }}</h2>
            <p class="teacher-dialog__desc">
              {{ editingId ? '调整姓名与班级归属，保留现有账号和真实状态链路。' : '在当前教师控制台内补录学生账号、初始密码和班级归属。' }}
            </p>
          </div>
          <div class="teacher-dialog__badges">
            <span class="teacher-dialog__badge">{{ selectedClassSummary.title }}</span>
            <span class="teacher-dialog__badge teacher-dialog__badge--muted">{{ editingId ? '编辑模式' : '创建模式' }}</span>
          </div>
        </header>

        <section class="teacher-dialog__context-grid">
          <article class="teacher-dialog__context-card">
            <span class="teacher-dialog__context-label">当前班级上下文</span>
            <strong>{{ selectedClassSummary.title }}</strong>
            <p>{{ selectedClassSummary.subtitle }}</p>
          </article>
          <article class="teacher-dialog__context-card">
            <span class="teacher-dialog__context-label">保存说明</span>
            <strong>{{ editingId ? '更新名册条目' : '创建新账号' }}</strong>
            <p>{{ editingId ? '保存后立即回写到真实学生列表。' : '账号与密码必填，创建后可继续批量导入补量。' }}</p>
          </article>
        </section>

        <el-form class="teacher-dialog__form" label-position="top">
          <section class="teacher-dialog__group">
            <div class="teacher-dialog__group-title">账号与归属</div>
            <div class="teacher-dialog__grid teacher-dialog__grid--2col">
              <el-form-item label="账号" v-if="!editingId">
                <el-input v-model="form.username" placeholder="例如：20260011" />
              </el-form-item>
              <el-form-item label="学生姓名">
                <el-input v-model="form.displayName" placeholder="请输入学生姓名" />
              </el-form-item>
              <el-form-item label="初始密码" v-if="!editingId">
                <el-input v-model="form.password" show-password placeholder="默认可用 123456" />
              </el-form-item>
              <el-form-item label="所属班级">
                <el-select v-model="form.classId" placeholder="请选择班级">
                  <el-option v-for="item in classes" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
                </el-select>
              </el-form-item>
            </div>
          </section>
        </el-form>

        <footer class="teacher-dialog__footer">
          <div class="teacher-dialog__footer-note">保留现有 API 校验与保存逻辑，仅优化教师端工作面板呈现。</div>
          <div class="teacher-dialog__footer-actions">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="submit">{{ editingId ? '保存调整' : '创建学生' }}</el-button>
          </div>
        </footer>
      </div>
    </el-dialog>

    <el-dialog v-model="importDialogVisible" width="760px" class="teacher-dialog" destroy-on-close>
      <div class="teacher-dialog__shell">
        <header class="teacher-dialog__header">
          <div>
            <p class="teacher-dialog__eyebrow">学生工作台 / 批量导入</p>
            <h2>批量导入学生</h2>
            <p class="teacher-dialog__desc">按当前班级上下文导入学生名册，逐行解析账号、姓名和密码，并保留真实导入结果反馈。</p>
          </div>
          <div class="teacher-dialog__badges">
            <span class="teacher-dialog__badge">{{ selectedClassSummary.title }}</span>
            <span class="teacher-dialog__badge teacher-dialog__badge--muted">{{ selectedClassId ? '已锁定班级' : '未锁定' }}</span>
          </div>
        </header>

        <section class="teacher-dialog__context-grid teacher-dialog__context-grid--import">
          <article class="teacher-dialog__context-card">
            <span class="teacher-dialog__context-label">导入格式</span>
            <strong>Excel 或 文本批量导入</strong>
            <p>Excel 表头支持“学号 / 姓名 / 密码”，密码留空时默认使用学号。</p>
          </article>
          <article class="teacher-dialog__context-card">
            <span class="teacher-dialog__context-label">导入去向</span>
            <strong>{{ selectedClassSummary.title }}</strong>
            <p>当前导入结果会直接落入所选班级，并在下方返回失败明细。</p>
          </article>
        </section>

        <el-form class="teacher-dialog__form" label-position="top">
          <section class="teacher-dialog__group">
            <div class="teacher-dialog__group-title">Excel 文件导入</div>
            <div
              :class="['import-file-panel', { 'is-dragover': isImportDragOver }]"
              @dragenter.prevent="handleImportDragEnter"
              @dragover.prevent="handleImportDragOver"
              @dragleave.prevent="handleImportDragLeave"
              @drop.prevent="handleImportDrop"
            >
              <div class="import-file-panel__meta">
                <strong>{{ importFileName || '尚未选择 Excel 文件' }}</strong>
                <span>
                  {{ parsedImportRows.length ? `已解析 ${parsedImportRows.length} 条有效记录` : '支持 .xlsx，仅读取首个工作表' }}
                </span>
              </div>
              <div class="import-file-panel__actions">
                <el-button @click="downloadImportTemplate">下载模板</el-button>
                <el-upload
                  accept=".xlsx"
                  :auto-upload="false"
                  :show-file-list="false"
                  :on-change="handleImportFileChange"
                >
                  <el-button type="primary" plain>选择 Excel 文件</el-button>
                </el-upload>
                <el-button v-if="selectedImportFile" @click="clearImportFile">清空文件</el-button>
              </div>
            </div>
            <div class="import-file-panel__tips">
              <span>可直接将 Excel 文件拖拽到上方区域</span>
              <span>表头建议：学号、姓名、密码</span>
              <span>密码列可空，系统会自动回退为学号</span>
            </div>
          </section>

          <section class="teacher-dialog__group">
            <div class="teacher-dialog__group-title">文本补充导入</div>
            <el-form-item label="批量录入文本">
              <el-input
                v-model="importText"
                type="textarea"
                :rows="9"
                placeholder="20260011,张三,123456"
              />
            </el-form-item>
          </section>
        </el-form>

        <section v-if="importResult" class="teacher-dialog__result-card">
          <div class="teacher-dialog__result-header">
            <div>
              <span class="teacher-dialog__context-label">导入结果</span>
              <strong>成功 {{ importResult.successCount }} 条，失败 {{ importResult.failureCount }} 条</strong>
            </div>
            <span class="teacher-dialog__badge teacher-dialog__badge--muted">失败明细保留</span>
          </div>
          <el-table :data="importResult.failures" size="small" empty-text="无失败记录">
            <el-table-column prop="row" label="行号" width="100" />
            <el-table-column prop="reason" label="失败原因" />
          </el-table>
        </section>

        <footer class="teacher-dialog__footer">
          <div class="teacher-dialog__footer-note">导入接口、字段解析和结果展示逻辑保持不变。</div>
          <div class="teacher-dialog__footer-actions">
            <el-button @click="importDialogVisible = false">关闭</el-button>
            <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
          </div>
        </footer>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { UploadFile } from 'element-plus';
import { read, utils, writeFileXLSX } from 'xlsx';
import {
  changeAdminStudentStatus,
  createAdminStudent,
  importAdminStudents,
  listAdminClasses,
  listAdminStudents,
  updateAdminStudent,
} from '@/api/admin';
import type { AdminClass } from '@/types/admin';
import type { ImportStudentRow, ImportStudentsResult, Student, StudentStatus } from '@/types/student';

const headerCellStyle = {
  background: '#f8fafc',
  color: '#64748b',
  fontWeight: '700',
  textTransform: 'uppercase',
  fontSize: '11px',
  letterSpacing: '0.08em',
};

const loading = ref(false);
const submitting = ref(false);
const importing = ref(false);
const dialogVisible = ref(false);
const importDialogVisible = ref(false);
const editingId = ref<number | null>(null);
const selectedClassId = ref<number | undefined>();
const rows = ref<Student[]>([]);
const classes = ref<AdminClass[]>([]);
const importText = ref('');
const importResult = ref<ImportStudentsResult | null>(null);
const selectedImportFile = ref<File | null>(null);
const importFileName = ref('');
const parsedImportRows = ref<ImportStudentRow[]>([]);
const isImportDragOver = ref(false);
const searchQuery = ref('');
const statusFilter = ref<StudentStatus | ''>('');

const REQUIRED_IMPORT_HEADERS = {
  username: ['学号', '账号', '用户名'],
  displayName: ['姓名', '学生姓名'],
  password: ['密码', '初始密码'],
} as const;

const form = reactive({
  username: '',
  displayName: '',
  password: '123456',
  classId: undefined as number | undefined,
});

const classMap = computed(() => new Map(classes.value.map(item => [item.id, item])));

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase();
  return rows.value.filter(row => {
    const classInfo = row.classId ? classMap.value.get(row.classId) : undefined;
    const matchesKeyword =
      !keyword ||
      row.username.toLowerCase().includes(keyword) ||
      row.displayName.toLowerCase().includes(keyword) ||
      (classInfo?.name.toLowerCase().includes(keyword) ?? false) ||
      (classInfo?.code.toLowerCase().includes(keyword) ?? false);
    const matchesStatus = !statusFilter.value || row.status === statusFilter.value;
    return matchesKeyword && matchesStatus;
  });
});

const activeCount = computed(() => filteredRows.value.filter(item => item.status === 'ACTIVE').length);
const disabledCount = computed(() => filteredRows.value.filter(item => item.status === 'DISABLED').length);

const selectedClassSummary = computed(() => {
  if (!classes.value.length) {
    return {
      title: '暂无班级数据',
      subtitle: '请先创建班级后再管理学生名册',
    };
  }

  if (!selectedClassId.value) {
    return {
      title: '全部班级视图',
      subtitle: `当前共加载 ${classes.value.length} 个班级，可切换后执行定向导入`,
    };
  }

  const currentClass = classMap.value.get(selectedClassId.value);
  if (!currentClass) {
    return {
      title: '班级上下文缺失',
      subtitle: '当前选中班级未在列表中找到，请刷新后重试',
    };
  }

  return {
    title: currentClass.name,
    subtitle: `班级编码 ${currentClass.code} · 创建与导入默认落到该班级`,
  };
});

const classCoverageText = computed(() => {
  if (!classes.value.length) {
    return '0 班级';
  }
  return `${selectedClassId.value ? '单班' : '全部'} / ${classes.value.length} 班级`;
});

const resetForm = () => {
  form.username = '';
  form.displayName = '';
  form.password = '123456';
  form.classId = selectedClassId.value ?? classes.value[0]?.id;
  editingId.value = null;
};

const getClassLabel = (classId: number | null) => {
  if (!classId) {
    return '未分配班级';
  }
  return classMap.value.get(classId)?.name ?? '未知班级';
};

const getClassCode = (classId: number | null) => {
  if (!classId) {
    return '未绑定班级编码';
  }
  return classMap.value.get(classId)?.code ?? '班级编码缺失';
};

const getAvatarText = (displayName: string, username: string) => {
  const source = displayName.trim() || username.trim();
  return source.slice(0, 1).toUpperCase();
};

const fetchClasses = async () => {
  classes.value = await listAdminClasses();
  if (!selectedClassId.value && classes.value.length === 1) {
    selectedClassId.value = classes.value[0].id;
  }
};

const fetchStudents = async () => {
  loading.value = true;
  try {
    rows.value = await listAdminStudents(selectedClassId.value);
  } finally {
    loading.value = false;
  }
};

const clearImportFile = () => {
  selectedImportFile.value = null;
  importFileName.value = '';
  parsedImportRows.value = [];
  isImportDragOver.value = false;
};

const downloadImportTemplate = () => {
  const worksheet = utils.aoa_to_sheet([
    ['学号', '姓名', '密码'],
    ['20260011', '张三', ''],
    ['20260012', '李四', '123456'],
  ]);
  const workbook = utils.book_new();
  utils.book_append_sheet(workbook, worksheet, '学生导入模板');
  writeFileXLSX(workbook, '学生批量导入模板.xlsx');
};

const normalizeCellValue = (value: string | number | boolean | null | undefined) => String(value ?? '').trim();

const parseImportTextRows = (): ImportStudentRow[] =>
  importText.value
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map(line => {
      const [username = '', displayName = '', password = ''] = line.split(',').map(item => item.trim());
      return { username, displayName, password };
    })
    .filter(item => item.username && item.displayName && item.password);

const findHeaderIndex = (headers: string[], candidates: readonly string[]) =>
  headers.findIndex(header => candidates.some(candidate => candidate === header));

const parseExcelRows = async (file: File): Promise<ImportStudentRow[]> => {
  const buffer = await file.arrayBuffer();
  const workbook = read(buffer, { type: 'array' });
  const firstSheetName = workbook.SheetNames[0];
  if (!firstSheetName) {
    throw new Error('Excel 文件中未找到可用工作表');
  }

  const worksheet = workbook.Sheets[firstSheetName];
  const rawRows = utils.sheet_to_json<Array<string | number | boolean | null>>(worksheet, {
    header: 1,
    raw: false,
    defval: '',
  });

  if (!rawRows.length) {
    throw new Error('Excel 文件内容为空');
  }

  const headerRow = rawRows[0].map(item => normalizeCellValue(item));
  const usernameIndex = findHeaderIndex(headerRow, REQUIRED_IMPORT_HEADERS.username);
  const displayNameIndex = findHeaderIndex(headerRow, REQUIRED_IMPORT_HEADERS.displayName);
  const passwordIndex = findHeaderIndex(headerRow, REQUIRED_IMPORT_HEADERS.password);

  if (usernameIndex < 0 || displayNameIndex < 0) {
    throw new Error('Excel 表头必须包含“学号”和“姓名”列');
  }

  return rawRows
    .slice(1)
    .map(row => {
      const username = normalizeCellValue(row[usernameIndex]);
      const displayName = normalizeCellValue(row[displayNameIndex]);
      const rawPassword = passwordIndex >= 0 ? normalizeCellValue(row[passwordIndex]) : '';
      const password = rawPassword || username;
      return { username, displayName, password };
    })
    .filter(item => item.username || item.displayName || item.password)
    .filter(item => item.username && item.displayName);
};

const applyImportFile = async (file: File) => {
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.warning('请上传 .xlsx 格式的 Excel 文件');
    clearImportFile();
    return;
  }

  try {
    const rows = await parseExcelRows(file);
    if (!rows.length) {
      ElMessage.warning('Excel 中没有可导入的有效学生记录');
      clearImportFile();
      return;
    }
    selectedImportFile.value = file;
    importFileName.value = file.name;
    parsedImportRows.value = rows;
    importResult.value = null;
    ElMessage.success(`Excel 解析成功，共 ${rows.length} 条有效记录`);
  } catch (error) {
    clearImportFile();
    ElMessage.error(error instanceof Error ? error.message : 'Excel 解析失败，请检查文件内容');
  } finally {
    isImportDragOver.value = false;
  }
};

const handleImportFileChange = async (uploadFile: UploadFile) => {
  const rawFile = uploadFile.raw;
  if (!rawFile) {
    return;
  }
  await applyImportFile(rawFile);
};

const handleImportDragEnter = () => {
  isImportDragOver.value = true;
};

const handleImportDragOver = () => {
  isImportDragOver.value = true;
};

const handleImportDragLeave = (event: DragEvent) => {
  const currentTarget = event.currentTarget;
  const relatedTarget = event.relatedTarget;
  if (currentTarget instanceof HTMLElement && relatedTarget instanceof Node && currentTarget.contains(relatedTarget)) {
    return;
  }
  isImportDragOver.value = false;
};

const handleImportDrop = async (event: DragEvent) => {
  const file = event.dataTransfer?.files?.[0];
  if (!file) {
    isImportDragOver.value = false;
    return;
  }
  await applyImportFile(file);
};

const openCreateDialog = () => {
  resetForm();
  dialogVisible.value = true;
};

const openEditDialog = (row: Student) => {
  editingId.value = row.id;
  form.username = row.username;
  form.displayName = row.displayName;
  form.password = '123456';
  form.classId = row.classId ?? selectedClassId.value ?? classes.value[0]?.id;
  dialogVisible.value = true;
};

const submit = async () => {
  if (!form.displayName.trim() || !form.classId) {
    ElMessage.warning('请填写姓名并选择班级');
    return;
  }
  if (!editingId.value && (!form.username.trim() || !form.password.trim())) {
    ElMessage.warning('请填写账号和初始密码');
    return;
  }

  submitting.value = true;
  try {
    if (editingId.value) {
      await updateAdminStudent(editingId.value, { displayName: form.displayName.trim(), classId: form.classId });
      ElMessage.success('学生更新成功');
    } else {
      await createAdminStudent({
        username: form.username.trim(),
        displayName: form.displayName.trim(),
        password: form.password.trim(),
        classId: form.classId,
      });
      ElMessage.success('学生创建成功');
    }
    dialogVisible.value = false;
    resetForm();
    await fetchStudents();
  } finally {
    submitting.value = false;
  }
};

const toggleStatus = async (row: Student) => {
  const nextStatus: StudentStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  await changeAdminStudentStatus(row.id, nextStatus);
  ElMessage.success('学生状态更新成功');
  await fetchStudents();
};

const openImportDialog = () => {
  if (!selectedClassId.value) {
    ElMessage.warning('请先选择导入班级');
    return;
  }
  importText.value = '';
  importResult.value = null;
  clearImportFile();
  importDialogVisible.value = true;
};

const submitImport = async () => {
  if (!selectedClassId.value) {
    ElMessage.warning('请先选择导入班级');
    return;
  }
  const parsedRows = parsedImportRows.value.length ? parsedImportRows.value : parseImportTextRows();

  if (!parsedRows.length) {
    ElMessage.warning('请先选择有效的 Excel 文件，或输入至少一条有效文本记录');
    return;
  }

  importing.value = true;
  try {
    importResult.value = await importAdminStudents({ classId: selectedClassId.value, rows: parsedRows });
    ElMessage.success('导入已完成');
    await fetchStudents();
  } finally {
    importing.value = false;
  }
};

onMounted(async () => {
  await fetchClasses();
  resetForm();
  await fetchStudents();
});
</script>

<style scoped>
.students-console {
  gap: 8px;
}

.students-meta {
  row-gap: 6px;
}

.console-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.console-button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 12px;
  min-height: 32px;
  border-radius: 8px;
  border: 1px solid transparent;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.18s ease;
}

.console-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.console-button--primary {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  box-shadow: 0 10px 20px rgba(20, 184, 166, 0.16);
}

.console-button--secondary {
  background: #fff;
  border-color: #cbd5e1;
  color: #0f172a;
}

.console-button:not(:disabled):hover {
  transform: translateY(-1px);
}

.students-header__eyebrow {
  margin-bottom: 2px;
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.console-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.console-grid--refactored {
  gap: 8px;
}

.toolbar-card,
.table-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
}

.toolbar-card {
  padding: 10px 12px;
}

.toolbar-card__main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.toolbar-search {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 220px;
  flex: 1;
}

.toolbar-search > .material-symbols-outlined {
  position: absolute;
  left: 10px;
  z-index: 1;
  color: #94a3b8;
  font-size: 16px;
}

.toolbar-search :deep(.el-input) {
  width: 100%;
}

.toolbar-search :deep(.el-input__wrapper) {
  padding-left: 30px;
  min-height: 34px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
}

.toolbar-filters {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-filter {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 180px;
}

.toolbar-filter--compact {
  min-width: 120px;
}

.toolbar-filter label {
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.toolbar-filter :deep(.el-select) {
  width: 100%;
}

.toolbar-filter :deep(.el-select__wrapper) {
  min-height: 34px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
}

.toolbar-card__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  color: #64748b;
  font-size: 10px;
  flex-wrap: wrap;
}

.toolbar-card__dot {
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: #cbd5e1;
}

.table-card {
  overflow: hidden;
}

.table-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 10px 12px 0;
  flex-wrap: wrap;
  border-bottom: 1px solid #eef2f7;
  padding-bottom: 8px;
}

.table-card__header h2 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.table-card__header p {
  margin: 1px 0 0;
  color: #64748b;
  font-size: 10px;
}

.students-table {
  margin-top: 2px;
}

.students-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.students-table :deep(.el-table__cell) {
  padding-top: 8px;
  padding-bottom: 8px;
}

.students-table :deep(.el-table__row) {
  transition: background-color 0.18s ease;
}

.student-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.student-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 800;
  flex-shrink: 0;
}

.student-avatar.is-active {
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.16), rgba(13, 148, 136, 0.28));
  color: #0f766e;
}

.student-avatar.is-disabled {
  background: linear-gradient(135deg, rgba(148, 163, 184, 0.18), rgba(100, 116, 139, 0.3));
  color: #475569;
}

.student-cell__body {
  min-width: 0;
}

.student-cell__name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.student-cell__name {
  color: #0f172a;
  font-size: 12px;
  font-weight: 800;
}

.student-cell__account {
  margin-top: 1px;
  color: #475569;
  font-size: 10px;
  font-weight: 600;
}

.student-cell__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 3px;
  color: #64748b;
  font-size: 10px;
}

.student-cell__meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.student-cell__meta .material-symbols-outlined {
  font-size: 13px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.status-pill__dot {
  width: 4px;
  height: 4px;
  border-radius: 999px;
}

.status-pill--active {
  background: rgba(20, 184, 166, 0.14);
  color: #0f766e;
}

.status-pill--active .status-pill__dot {
  background: #14b8a6;
}

.status-pill--disabled {
  background: rgba(148, 163, 184, 0.18);
  color: #475569;
}

.status-pill--disabled .status-pill__dot {
  background: #64748b;
}

.class-cell__title,
.control-cell__value {
  color: #0f172a;
  font-size: 11px;
  font-weight: 700;
}

.class-cell__subtitle,
.control-cell__label {
  margin-top: 2px;
  color: #64748b;
  font-size: 10px;
}

.control-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.control-cell__item {
  padding: 5px 7px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.control-cell__label {
  display: block;
  margin-top: 0;
}

.control-cell__value {
  display: block;
  margin-top: 2px;
}

.action-cell {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.action-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: #64748b;
  cursor: pointer;
  transition: all 0.16s ease;
}

.action-icon:hover {
  border-color: #14b8a6;
  color: #0f766e;
  background: rgba(20, 184, 166, 0.08);
}

.teacher-dialog :deep(.el-dialog) {
  border-radius: 28px;
  overflow: hidden;
}

.teacher-dialog :deep(.el-dialog__header) {
  display: none;
}

.teacher-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.teacher-dialog__shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
}

.teacher-dialog__header,
.teacher-dialog__footer,
.teacher-dialog__context-card,
.teacher-dialog__group,
.teacher-dialog__result-card {
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
}

.teacher-dialog__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.1), rgba(248, 250, 252, 0.98));
}

.teacher-dialog__eyebrow,
.teacher-dialog__context-label {
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.teacher-dialog__header h2 {
  margin: 6px 0 0;
  color: #0f172a;
  font-size: 22px;
  font-weight: 800;
}

.teacher-dialog__desc {
  margin: 6px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
  max-width: 480px;
}

.teacher-dialog__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 6px;
}

.teacher-dialog__badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.teacher-dialog__badge--muted {
  background: #f1f5f9;
  color: #475569;
}

.teacher-dialog__context-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.teacher-dialog__context-grid--import {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
}

.teacher-dialog__context-card {
  padding: 12px 14px;
}

.teacher-dialog__context-card strong,
.teacher-dialog__result-header strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
}

.teacher-dialog__context-card p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 11px;
  line-height: 1.45;
}

.teacher-dialog__form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.teacher-dialog__group {
  padding: 14px;
}

.teacher-dialog__group-title {
  margin-bottom: 10px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
}

.import-file-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
  background: #f8fafc;
}

.import-file-panel.is-dragover {
  border-color: #14b8a6;
  background: rgba(20, 184, 166, 0.08);
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.12);
}

.import-file-panel__meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.import-file-panel__meta strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 800;
  word-break: break-all;
}

.import-file-panel__meta span,
.import-file-panel__tips {
  color: #64748b;
  font-size: 11px;
  line-height: 1.5;
}

.import-file-panel__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.import-file-panel__tips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-top: 10px;
}

.teacher-dialog__grid {
  display: grid;
  gap: 10px;
}

.teacher-dialog__grid--2col {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.teacher-dialog__form :deep(.el-form-item) {
  margin-bottom: 0;
}

.teacher-dialog__form :deep(.el-form-item__label) {
  margin-bottom: 6px;
  color: #475569;
  font-size: 11px;
  font-weight: 700;
}

.teacher-dialog__form :deep(.el-input__wrapper),
.teacher-dialog__form :deep(.el-select__wrapper),
.teacher-dialog__form :deep(.el-textarea__inner) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  background: #f8fafc;
}

.teacher-dialog__result-card {
  padding: 14px;
}

.teacher-dialog__result-header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 10px;
}

.teacher-dialog__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
}

.teacher-dialog__footer-note {
  color: #64748b;
  font-size: 11px;
  line-height: 1.45;
}

.teacher-dialog__footer-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 1200px) {
}

@media (max-width: 768px) {
  .toolbar-card__main,
  .table-card__header {
    align-items: stretch;
  }

  .toolbar-search,
  .toolbar-filter,
  .toolbar-filter--compact {
    min-width: 100%;
  }

  .teacher-dialog__header,
  .teacher-dialog__footer {
    flex-direction: column;
    align-items: stretch;
  }

  .teacher-dialog__badges {
    justify-content: flex-start;
  }

  .teacher-dialog__context-grid,
  .teacher-dialog__grid--2col {
    grid-template-columns: 1fr;
  }

  .import-file-panel {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>

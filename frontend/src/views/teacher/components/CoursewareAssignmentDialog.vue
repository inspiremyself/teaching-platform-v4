<template>
  <el-dialog
    :model-value="visible"
    title="按小节布置学习任务"
    width="760px"
    @update:model-value="onVisibleChange"
  >
    <el-form label-width="100px">
      <el-form-item label="布置班级">
        <el-select v-model="classIds" multiple placeholder="请选择班级" class="assignment-dialog__select">
          <el-option
            v-for="item in classes"
            :key="item.id"
            :label="`${item.name}（${item.code}）`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="目标方式">
        <div v-for="classId in classIds" :key="classId" class="assignment-dialog__target">
          <span class="assignment-dialog__class-name">{{ className(classId) }}</span>
          <el-radio-group v-model="targetModes[classId]">
            <el-radio value="CLASS">整班</el-radio>
            <el-radio value="STUDENT">点名学生</el-radio>
          </el-radio-group>
          <el-input
            v-if="targetModes[classId] === 'STUDENT'"
            v-model="studentInputs[classId]"
            placeholder="student_user_id，多个用逗号分隔"
            class="assignment-dialog__student-input"
          />
        </div>
        <span v-if="classIds.length === 0" class="assignment-dialog__hint">请先选择班级</span>
      </el-form-item>

      <el-form-item label="课件小节">
        <div class="assignment-dialog__sections">
          <p class="assignment-dialog__hint">不勾选任何小节表示整份布置，按课件原顺序展开。</p>
          <el-checkbox-group v-model="sectionIds">
            <el-checkbox v-for="section in sections" :key="section.id" :value="section.id">
              {{ section.heading }}
            </el-checkbox>
          </el-checkbox-group>
          <div v-if="sectionIds.length > 0" class="assignment-dialog__order">
            <p class="assignment-dialog__hint">阅读顺序（可用上移 / 下移调整）：</p>
            <div v-for="(sectionId, index) in sectionIds" :key="sectionId" class="assignment-dialog__order-row">
              <span>{{ index + 1 }}. {{ sectionHeading(sectionId) }}</span>
              <el-button link size="small" :disabled="index === 0" @click="move(index, -1)">上移</el-button>
              <el-button
                link
                size="small"
                :disabled="index === sectionIds.length - 1"
                @click="move(index, 1)"
              >
                下移
              </el-button>
            </div>
          </div>
        </div>
      </el-form-item>

      <el-form-item label="可见时间窗">
        <el-date-picker
          v-model="startAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ssZ"
          placeholder="开始时间（选填）"
        />
        <span class="assignment-dialog__dash">至</span>
        <el-date-picker
          v-model="endAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ssZ"
          placeholder="结束时间（选填）"
        />
        <p class="assignment-dialog__hint">两个时间须成对填写；都不填表示长期可见。</p>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('close')">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">布置</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { inject, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { listClasses } from '@/api/classes';
import { listTeacherCoursewareSections } from '@/api/teacherContent';
import { teacherContentKey } from '@/composables/useTeacherContent';
import type { TeachingClass } from '@/types/class';
import type { CoursewareSectionItem } from '@/types/platformContent';
import type { AssignmentTargetPayload, CreateAssignmentPayload } from '@/types/teacherContent';

const props = withDefaults(
  defineProps<{ coursewareId: number; visible: boolean }>(),
  { visible: false },
);
const emit = defineEmits<{ (e: 'close'): void; (e: 'created'): void }>();
const teacherContent = inject(teacherContentKey)!;

const classes = ref<TeachingClass[]>([]);
const sections = ref<CoursewareSectionItem[]>([]);
const classIds = ref<number[]>([]);
const targetModes = ref<Record<number, 'CLASS' | 'STUDENT'>>({});
const studentInputs = ref<Record<number, string>>({});
const sectionIds = ref<number[]>([]);
const startAt = ref('');
const endAt = ref('');
const submitting = ref(false);
const loadGeneration = ref(0);

const className = (classId: number) =>
  classes.value.find((item) => item.id === classId)?.name ?? `班级 ${classId}`;
const sectionHeading = (sectionId: number) =>
  sections.value.find((item) => item.id === sectionId)?.heading ?? `小节 ${sectionId}`;

const onVisibleChange = (value: boolean) => {
  if (!value) {
    emit('close');
  }
};

const move = (index: number, delta: number) => {
  const target = index + delta;
  const next = [...sectionIds.value];
  if (target < 0 || target >= next.length) {
    return;
  }
  [next[index], next[target]] = [next[target], next[index]];
  sectionIds.value = next;
};

const parseStudentIds = (classId: number): number[] | null => {
  if (targetModes.value[classId] !== 'STUDENT') {
    return [];
  }
  const raw = (studentInputs.value[classId] ?? '').trim();
  if (!raw) {
    ElMessage.warning(`${className(classId)} 请输入点名学生 ID`);
    return null;
  }
  const ids: number[] = [];
  for (const part of raw.split(',').map((value) => value.trim()).filter((value) => value !== '')) {
    const value = Number(part);
    if (!/^\d+$/.test(part) || !Number.isSafeInteger(value) || value <= 0) {
      ElMessage.warning('学生 ID 须为正整数，多个用逗号分隔');
      return null;
    }
    ids.push(value);
  }
  return ids;
};

const onSubmit = async () => {
  if (classIds.value.length === 0) {
    ElMessage.warning('请至少选择一个班级');
    return;
  }
  if ((startAt.value === '') !== (endAt.value === '')) {
    ElMessage.warning('时间窗须成对填写');
    return;
  }
  const targets: AssignmentTargetPayload[] = [];
  for (const classId of classIds.value) {
    const studentIds = parseStudentIds(classId);
    if (studentIds === null) {
      return;
    }
    targets.push({ classId, studentIds });
  }
  const payload: CreateAssignmentPayload = {
    coursewareId: props.coursewareId,
    sectionIds: [...sectionIds.value],
    targets,
    startAt: startAt.value || null,
    endAt: endAt.value || null,
  };
  submitting.value = true;
  try {
    await teacherContent.createAssignment(payload);
    emit('created');
    emit('close');
  } finally {
    submitting.value = false;
  }
};

watch(classIds, (ids) => {
  for (const classId of ids) {
    if (!targetModes.value[classId]) {
      targetModes.value[classId] = 'CLASS';
    }
  }
});

watch(
  () => props.visible,
  async (visible) => {
    const snapshot = ++loadGeneration.value;
    if (!visible) {
      return;
    }
    classIds.value = [];
    targetModes.value = {};
    studentInputs.value = {};
    sectionIds.value = [];
    startAt.value = '';
    endAt.value = '';
    const [classList, sectionList] = await Promise.all([
      listClasses(),
      listTeacherCoursewareSections(props.coursewareId),
    ]);
    if (snapshot !== loadGeneration.value) {
      return;
    }
    classes.value = classList;
    sections.value = sectionList;
  },
);
</script>

<style scoped>
.assignment-dialog__select {
  width: 100%;
}

.assignment-dialog__target {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.assignment-dialog__class-name {
  min-width: 120px;
}

.assignment-dialog__student-input {
  width: 260px;
}

.assignment-dialog__sections {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.assignment-dialog__hint {
  margin: 0;
  color: #909399;
  font-size: 12px;
}

.assignment-dialog__order {
  border-top: 1px solid #ebeef5;
  padding-top: 8px;
}

.assignment-dialog__order-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.assignment-dialog__dash {
  margin: 0 8px;
}
</style>

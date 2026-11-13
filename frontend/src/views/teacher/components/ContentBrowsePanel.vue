<template>
  <div class="page teacher-content">
    <section class="filter-toolbar">
      <el-select v-model="teacherContent.filterType.value" class="filter-toolbar__item">
        <el-option label="全部" value="ALL" />
        <el-option label="未挂载" value="UNMOUNTED" />
        <el-option label="按教材" value="TEXTBOOK" />
        <el-option label="按教材小节" value="TEXTBOOK_SECTION" />
        <el-option label="按课件小节" value="COURSEWARE_SECTION" />
      </el-select>
      <el-select
        v-if="teacherContent.filterType.value === 'TEXTBOOK'"
        v-model="teacherContent.textbookId.value"
        class="filter-toolbar__item"
      >
        <el-option label="请选择教材" :value="null" />
        <el-option
          v-for="textbook in teacherContent.textbooks.value"
          :key="textbook.id"
          :label="textbook.title"
          :value="textbook.id"
        />
      </el-select>
      <el-select
        v-if="teacherContent.filterType.value === 'TEXTBOOK_SECTION'"
        v-model="selectedTextbookId"
        class="filter-toolbar__item"
        @change="onTextbookChange"
      >
        <el-option label="请选择教材" :value="null" />
        <el-option
          v-for="textbook in teacherContent.textbooks.value"
          :key="textbook.id"
          :label="textbook.title"
          :value="textbook.id"
        />
      </el-select>
      <el-select
        v-if="teacherContent.filterType.value === 'TEXTBOOK_SECTION'"
        v-model="teacherContent.sectionId.value"
        class="filter-toolbar__item"
      >
        <el-option label="请选择教材小节" :value="null" />
        <el-option
          v-for="section in teacherContent.textbookSections.value"
          :key="section.id"
          :label="section.heading"
          :value="section.id"
        />
      </el-select>
      <el-select
        v-if="teacherContent.filterType.value === 'COURSEWARE_SECTION'"
        v-model="selectedCoursewareId"
        class="filter-toolbar__item"
        @change="onCoursewareChange"
      >
        <el-option label="请选择课件" :value="null" />
        <el-option
          v-for="item in teacherContent.coursewares.value"
          :key="item.id"
          :label="item.title"
          :value="item.id"
        />
      </el-select>
      <el-select
        v-if="teacherContent.filterType.value === 'COURSEWARE_SECTION'"
        v-model="teacherContent.coursewareSectionId.value"
        class="filter-toolbar__item"
      >
        <el-option label="请选择课件小节" :value="null" />
        <el-option
          v-for="section in teacherContent.sections.value"
          :key="section.id"
          :label="section.heading"
          :value="section.id"
        />
      </el-select>
      <el-select v-model="teacherContent.subjectId.value" class="filter-toolbar__item">
        <el-option label="全部学科" :value="null" />
        <el-option
          v-for="subject in teacherContent.subjects.value"
          :key="subject.id"
          :label="subject.name"
          :value="subject.id"
        />
      </el-select>
      <el-button type="primary" @click="onSearch">筛选</el-button>
      <el-button @click="onOpenAssignments">布置记录</el-button>
    </section>

    <section class="teacher-content__list" v-loading="teacherContent.loading.value">
      <el-empty v-if="teacherContent.coursewares.value.length === 0" description="暂无可浏览的课件" />
      <el-card
        v-for="item in teacherContent.coursewares.value"
        :key="item.id"
        class="teacher-content__card"
        shadow="never"
      >
        <div class="teacher-content__card-head">
          <span class="teacher-content__title">{{ item.title }}</span>
          <el-tag size="small" type="info">{{ item.sectionCount }} 小节</el-tag>
        </div>
        <p class="teacher-content__subtitle">{{ item.subtitle }}</p>
        <div class="teacher-content__actions">
          <el-button link type="primary" @click="onOpen(item.id)">查看小节</el-button>
          <el-button
            v-if="item.status === COURSEWARE_STATUS.PUBLISHED"
            link
            type="primary"
            @click="onAssign(item.id)"
          >
            布置
          </el-button>
        </div>
      </el-card>
    </section>

    <el-dialog v-model="previewVisible" title="课件小节" width="720px">
      <el-alert
        v-if="teacherContent.visibility.value && !teacherContent.visibility.value.visible"
        title="该课件当前不可见，请联系平台管理员授权"
        type="warning"
        :closable="false"
      />
      <div v-for="section in teacherContent.sections.value" :key="section.id" class="teacher-content__section">
        <h4 class="teacher-content__section-title">{{ section.heading }}</h4>
        <ContentPreview :content-json="section.contentJson" :read-asset="readAssetFor" />
      </div>
    </el-dialog>

    <el-drawer v-model="assignmentDrawerVisible" title="按班查看布置记录" size="620px">
      <el-select
        v-model="assignmentClassId"
        placeholder="请选择班级"
        class="teacher-content__class-select"
        @change="onAssignmentClassChange"
      >
        <el-option
          v-for="item in assignmentClasses"
          :key="item.id"
          :label="`${item.name}（${item.code}）`"
          :value="item.id"
        />
      </el-select>
      <el-table :data="teacherContent.assignments.value" empty-text="该班级暂无布置记录">
        <el-table-column prop="coursewareTitle" label="课件" min-width="140" />
        <el-table-column prop="targetType" label="目标类型" width="100" />
        <el-table-column prop="targetId" label="目标 ID" width="100" />
        <el-table-column prop="sectionId" label="小节 ID" width="100" />
        <el-table-column prop="displayOrder" label="顺序" width="80" />
        <el-table-column prop="startAt" label="开始时间" min-width="160" />
        <el-table-column prop="endAt" label="结束时间" min-width="160" />
      </el-table>
    </el-drawer>

    <CoursewareAssignmentDialog
      :courseware-id="assignmentCoursewareId"
      :visible="assignmentDialogVisible"
      @close="assignmentDialogVisible = false"
      @created="onAssignmentCreated"
    />
  </div>
</template>

<script setup lang="ts">
import { inject, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { listClasses } from '@/api/classes';
import { getTeacherCoursewareVisibility } from '@/api/teacherContent';
import ContentPreview from '@/components/content-preview/ContentPreview.vue';
import { teacherContentKey } from '@/composables/useTeacherContent';
import { COURSEWARE_STATUS } from '@/types/enums';
import type { TeachingClass } from '@/types/class';
import CoursewareAssignmentDialog from './CoursewareAssignmentDialog.vue';

const teacherContent = inject(teacherContentKey)!;
const previewVisible = ref(false);
const selectedTextbookId = ref<number | null>(null);
const selectedCoursewareId = ref<number | null>(null);
const assignmentDialogVisible = ref(false);
const assignmentCoursewareId = ref(0);
const assignmentDrawerVisible = ref(false);
const assignmentClasses = ref<TeachingClass[]>([]);
const assignmentClassId = ref<number | null>(null);

const onTextbookChange = async (textbookId: number | null) => {
  teacherContent.sectionId.value = null;
  await teacherContent.loadTextbookSections(textbookId);
};

const onCoursewareChange = async (coursewareId: number | null) => {
  teacherContent.coursewareSectionId.value = null;
  if (coursewareId) {
    await teacherContent.loadSections(coursewareId);
  }
};

const readAssetFor = async (path: string): Promise<string> => {
  const assets = teacherContent.sections.value.flatMap((item) => item.assets ?? []);
  return teacherContent.readAsset(path, assets);
};

const onSearch = async () => {
  await teacherContent.loadCoursewares();
};

const onOpen = async (coursewareId: number) => {
  await teacherContent.loadSections(coursewareId);
  previewVisible.value = true;
  if (teacherContent.visibility.value && !teacherContent.visibility.value.visible) {
    ElMessage.warning('该课件当前不可见，请联系平台管理员授权');
  }
};

const onAssign = async (coursewareId: number) => {
  const visibility = await getTeacherCoursewareVisibility(coursewareId);
  if (!visibility.visible) {
    ElMessage.warning('该课件当前不可见，请联系平台管理员授权');
    return;
  }
  assignmentCoursewareId.value = coursewareId;
  assignmentDialogVisible.value = true;
};

const onOpenAssignments = async () => {
  assignmentClasses.value = await listClasses();
  assignmentDrawerVisible.value = true;
};

const onAssignmentClassChange = async (classId: number | null) => {
  if (!classId) {
    return;
  }
  await teacherContent.loadAssignments(classId);
};

const onAssignmentCreated = async () => {
  ElMessage.success('布置成功');
  if (assignmentClassId.value) {
    await teacherContent.loadAssignments(assignmentClassId.value);
  }
};
</script>

<style scoped>
.filter-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.filter-toolbar__item {
  width: 200px;
}

.teacher-content__card {
  margin-bottom: 12px;
}

.teacher-content__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.teacher-content__title {
  font-weight: 600;
}

.teacher-content__subtitle {
  color: #6b7785;
}

.teacher-content__section {
  margin-bottom: 18px;
}

.teacher-content__section-title {
  margin: 0 0 8px;
}

.teacher-content__class-select {
  width: 260px;
  margin-bottom: 16px;
}
</style>

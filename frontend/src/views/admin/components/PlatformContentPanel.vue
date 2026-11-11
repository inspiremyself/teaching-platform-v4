<template>
  <div class="page admin-platform-content" v-loading="platformContent.loading.value">
    <section class="admin-platform-content__block">
      <div class="admin-platform-content__head">
        <h2 class="admin-platform-content__title">平台学科字典</h2>
        <div class="admin-platform-content__head-actions">
          <el-input v-model="subjectForm.code" class="admin-platform-content__input" placeholder="编码" />
          <el-input v-model="subjectForm.name" class="admin-platform-content__input" placeholder="名称" />
          <el-button type="primary" @click="onAddSubject">新增学科</el-button>
        </div>
      </div>
      <el-table :data="platformContent.subjects.value" border stripe>
        <el-table-column prop="code" label="编码" min-width="140" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="onToggleSubject(row)">
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="admin-platform-content__block">
      <div class="admin-platform-content__head">
        <h2 class="admin-platform-content__title">平台教材目录</h2>
        <div class="admin-platform-content__head-actions">
          <el-select v-model="textbookForm.globalSubjectId" class="admin-platform-content__input">
            <el-option label="选择平台学科" :value="null" />
            <el-option
              v-for="subject in platformContent.subjects.value"
              :key="subject.id"
              :label="subject.name"
              :value="subject.id"
            />
          </el-select>
          <el-input v-model="textbookForm.code" class="admin-platform-content__input" placeholder="编码" />
          <el-input v-model="textbookForm.title" class="admin-platform-content__input" placeholder="标题" />
          <el-button type="primary" @click="onAddTextbook">新增教材</el-button>
        </div>
      </div>
      <el-table :data="platformContent.textbooks.value" border stripe>
        <el-table-column prop="code" label="编码" min-width="140" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column label="小节数" width="100">
          <template #default="{ row }">{{ row.sectionCount }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="onPickTextbook(row.id)">小节</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="admin-platform-content__block">
      <div class="admin-platform-content__head">
        <h2 class="admin-platform-content__title">平台课件</h2>
        <el-button type="primary" @click="generateVisible = true">AI 生成</el-button>
      </div>
      <el-table :data="platformContent.coursewares.value" border stripe>
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
              {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="小节数" width="90">
          <template #default="{ row }">{{ row.sectionCount }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status !== 'DRAFT'" @click="onEdit(row.id)">
              编辑
            </el-button>
            <el-button link type="primary" @click="onPreview(row.id)">预览</el-button>
            <el-button
              link
              type="primary"
              :disabled="row.status !== 'DRAFT'"
              @click="onPublish(row.id)"
            >
              发布
            </el-button>
            <el-button
              link
              type="primary"
              :disabled="row.status !== 'PUBLISHED'"
              @click="onUnpublish(row.id)"
            >
              下架
            </el-button>
            <el-button
              link
              type="primary"
              :disabled="row.status !== 'PUBLISHED'"
              @click="onOpenGrant(row.id)"
            >
              授权
            </el-button>
            <el-button link type="danger" @click="onRemove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="sectionVisible" title="教材小节" width="640px">
      <div class="admin-platform-content__head-actions">
        <el-input v-model="sectionForm.heading" class="admin-platform-content__input" placeholder="小节标题" />
        <el-input-number v-model="sectionForm.sortOrder" :min="1" />
        <el-button type="primary" @click="onAddSection">新增小节</el-button>
      </div>
      <el-table :data="platformContent.textbookSections.value" border stripe>
        <el-table-column prop="heading" label="小节" min-width="180" />
        <el-table-column prop="sortOrder" label="次序" width="90" />
        <el-table-column label="叶子" width="90">
          <template #default="{ row }">{{ row.leaf ? '是' : '否' }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑课件" width="900px">
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="editForm.subtitle" />
        </el-form-item>
        <el-form-item label="平台学科">
          <el-select v-model="editForm.globalSubjectId">
            <el-option
              v-for="subject in platformContent.subjects.value"
              :key="subject.id"
              :label="subject.name"
              :value="subject.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div
        v-for="(section, index) in editForm.sections"
        :key="index"
        class="admin-platform-content__section"
      >
        <el-input v-model="section.heading" placeholder="小节标题" class="admin-platform-content__input" />
        <el-input v-model="section.contentJson" type="textarea" :rows="6" placeholder="结构化正文 JSON" />
      </div>
      <div class="admin-platform-content__head-actions">
        <el-select
          v-model="mountTextbookId"
          class="admin-platform-content__input"
          @change="onLoadMountCandidates"
        >
          <el-option label="选择教材以挂载小节" :value="null" />
          <el-option
            v-for="textbook in platformContent.textbooks.value"
            :key="textbook.id"
            :label="textbook.title"
            :value="textbook.id"
          />
        </el-select>
        <el-select v-model="mountSectionIds" multiple class="admin-platform-content__input">
          <el-option
            v-for="section in mountCandidates"
            :key="section.id"
            :label="section.heading"
            :value="section.id"
          />
        </el-select>
        <el-button @click="onApplyMounts">应用挂载</el-button>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="grantVisible" title="按学校授权" width="640px">
      <el-select v-model="grantSchoolIds" multiple class="admin-platform-content__input">
        <el-option
          v-for="school in platformContent.schools.value"
          :key="school.id"
          :label="school.name"
          :value="school.id"
        />
      </el-select>
      <el-button type="primary" @click="onGrant">授权</el-button>
      <el-table :data="platformContent.grants.value" border stripe class="admin-platform-content__grants">
        <el-table-column label="学校 id" width="120">
          <template #default="{ row }">{{ row.schoolId }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="danger" @click="onRevoke(row.schoolId)">取消授权</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="generateVisible" title="AI 生成课件" width="640px">
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="platformContent.generateForm.value.title" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="platformContent.generateForm.value.subtitle" />
        </el-form-item>
        <el-form-item label="平台学科">
          <el-select v-model="platformContent.generateForm.value.globalSubjectId">
            <el-option
              v-for="subject in platformContent.subjects.value"
              :key="subject.id"
              :label="subject.name"
              :value="subject.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="教材">
          <el-select v-model="generateTextbookId" @change="onLoadGenerateSections">
            <el-option label="选择教材" :value="null" />
            <el-option
              v-for="textbook in platformContent.textbooks.value"
              :key="textbook.id"
              :label="textbook.title"
              :value="textbook.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="教材小节">
          <el-select v-model="platformContent.generateForm.value.textbookSectionId">
            <el-option
              v-for="section in generateSections"
              :key="section.id"
              :label="section.heading"
              :value="section.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" @click="onGenerate">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="课件预览" width="900px">
      <div v-for="section in platformContent.current.value?.sections ?? []" :key="section.id">
        <h4 class="admin-platform-content__section-title">{{ section.heading }}</h4>
        <ContentPreview :content-json="section.contentJson" :read-asset="platformContent.readAsset" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { inject, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import ContentPreview from '@/components/content-preview/ContentPreview.vue';
import { platformContentKey } from '@/composables/usePlatformContent';
import type { MountPayload, PlatformSubjectItem, SectionPayload, TextbookSectionItem } from '@/types/platformContent';

const platformContent = inject(platformContentKey)!;

const subjectForm = reactive({ code: '', name: '' });
const textbookForm = reactive({ globalSubjectId: null as number | null, code: '', title: '' });
const sectionForm = reactive({ heading: '', sortOrder: 1 });
const editForm = reactive({
  title: '',
  subtitle: '',
  globalSubjectId: null as number | null,
  sections: [] as SectionPayload[],
});
const mountTextbookId = ref<number | null>(null);
const mountSectionIds = ref<number[]>([]);
const mountCandidates = ref<TextbookSectionItem[]>([]);
const editMounts = ref<MountPayload[]>([]);
const grantSchoolIds = ref<number[]>([]);
const generateTextbookId = ref<number | null>(null);
const generateSections = ref<TextbookSectionItem[]>([]);
const currentTextbookId = ref<number | null>(null);
const editCoursewareId = ref<number | null>(null);
const grantCoursewareId = ref<number | null>(null);

const sectionVisible = ref(false);
const editVisible = ref(false);
const grantVisible = ref(false);
const generateVisible = ref(false);
const previewVisible = ref(false);

const onAddSubject = async () => {
  await platformContent.addSubject(subjectForm.code, subjectForm.name);
  subjectForm.code = '';
  subjectForm.name = '';
  ElMessage.success('平台学科已新增');
};

const onToggleSubject = async (subject: PlatformSubjectItem) => {
  await platformContent.toggleSubjectStatus(subject);
};

const onAddTextbook = async () => {
  if (!textbookForm.globalSubjectId) {
    ElMessage.warning('请选择平台学科');
    return;
  }
  await platformContent.addTextbook(textbookForm.globalSubjectId, textbookForm.code, textbookForm.title);
  textbookForm.code = '';
  textbookForm.title = '';
  ElMessage.success('平台教材已新增');
};

const onPickTextbook = async (textbookId: number) => {
  currentTextbookId.value = textbookId;
  await platformContent.loadTextbookSections(textbookId);
  sectionVisible.value = true;
};

const onAddSection = async () => {
  if (!currentTextbookId.value) {
    return;
  }
  await platformContent.addTextbookSection(
    currentTextbookId.value,
    sectionForm.heading,
    sectionForm.sortOrder,
    null,
  );
  sectionForm.heading = '';
  sectionForm.sortOrder += 1;
};

const onEdit = async (coursewareId: number) => {
  await platformContent.loadCurrent(coursewareId);
  const detail = platformContent.current.value;
  if (!detail) {
    return;
  }
  editCoursewareId.value = coursewareId;
  editForm.title = detail.courseware.title;
  editForm.subtitle = detail.courseware.subtitle ?? '';
  editForm.globalSubjectId = detail.courseware.globalSubjectId;
  editForm.sections = platformContent.sectionsPayload(detail);
  editMounts.value = [...detail.mounts];
  editVisible.value = true;
};

const onLoadMountCandidates = async (textbookId: number | null) => {
  if (!textbookId) {
    mountCandidates.value = [];
    return;
  }
  await platformContent.loadTextbookSections(textbookId);
  mountCandidates.value = platformContent.textbookSections.value;
};

const onApplyMounts = () => {
  editMounts.value = mountSectionIds.value.map((sectionId) => ({
    mountType: 'TEXTBOOK_SECTION',
    mountTargetId: sectionId,
  }));
};

const onSave = async () => {
  if (!editCoursewareId.value || !editForm.globalSubjectId) {
    ElMessage.warning('请选择平台学科');
    return;
  }
  await platformContent.saveCourseware(editCoursewareId.value, {
    title: editForm.title,
    subtitle: editForm.subtitle,
    globalSubjectId: editForm.globalSubjectId,
    sections: editForm.sections,
    mounts: editMounts.value,
  });
  editVisible.value = false;
  ElMessage.success('课件已保存');
};

const onPreview = async (coursewareId: number) => {
  await platformContent.loadCurrent(coursewareId);
  previewVisible.value = true;
};

const onPublish = async (coursewareId: number) => {
  await platformContent.publish(coursewareId);
  ElMessage.success('课件已发布');
};

const onUnpublish = async (coursewareId: number) => {
  await platformContent.unpublish(coursewareId);
  ElMessage.success('课件已下架');
};

const onRemove = async (coursewareId: number) => {
  await ElMessageBox.confirm('已被布置的课件只能下架，确认删除？', '删除课件', {
    type: 'warning',
  });
  await platformContent.remove(coursewareId);
  ElMessage.success('课件已删除');
};

const onOpenGrant = async (coursewareId: number) => {
  grantCoursewareId.value = coursewareId;
  await platformContent.loadGrants(coursewareId);
  grantVisible.value = true;
};

const onGrant = async () => {
  if (!grantCoursewareId.value || grantSchoolIds.value.length === 0) {
    ElMessage.warning('请选择学校');
    return;
  }
  await platformContent.grant(grantCoursewareId.value, grantSchoolIds.value);
  grantSchoolIds.value = [];
  ElMessage.success('授权已保存');
};

const onRevoke = async (schoolId: number) => {
  if (!grantCoursewareId.value) {
    return;
  }
  await platformContent.revoke(grantCoursewareId.value, schoolId);
};

const onLoadGenerateSections = async (textbookId: number | null) => {
  if (!textbookId) {
    generateSections.value = [];
    return;
  }
  await platformContent.loadTextbookSections(textbookId);
  generateSections.value = platformContent.textbookSections.value;
};

const onGenerate = async () => {
  const detail = await platformContent.runGenerate();
  if (!detail) {
    return;
  }
  generateVisible.value = false;
  previewVisible.value = true;
  ElMessage.success('课件草稿已生成');
};
</script>

<style scoped>
.admin-platform-content__block {
  margin-bottom: 24px;
}

.admin-platform-content__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.admin-platform-content__head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.admin-platform-content__title {
  margin: 0;
  font-size: 16px;
}

.admin-platform-content__input {
  width: 200px;
}

.admin-platform-content__section {
  margin-bottom: 12px;
}

.admin-platform-content__section-title {
  margin: 12px 0 6px;
}

.admin-platform-content__grants {
  margin-top: 12px;
}
</style>

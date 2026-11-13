import { computed, ref } from 'vue';
import type { InjectionKey } from 'vue';
import {
  createCoursewareAssignment,
  getTeacherCoursewareSection,
  getTeacherCoursewareVisibility,
  listCoursewareAssignments,
  listTeacherCoursewareSections,
  listTeacherCoursewares,
  listTeacherSubjects,
  listTeacherTextbookSections,
  listTeacherTextbooks,
  readTeacherCoursewareAssetBlob,
} from '@/api/teacherContent';
import type { ContentFilterType } from '@/types/enums';
import type {
  ContentBrowseItem,
  ContentVisibilityItem,
  CreateAssignmentPayload,
  TeacherAssignmentItem,
} from '@/types/teacherContent';
import type {
  CoursewareAssetItem,
  CoursewareSectionItem,
  PlatformSubjectItem,
  TextbookItem,
  TextbookSectionItem,
} from '@/types/platformContent';

export const useTeacherContent = () => {
  const filterType = ref<ContentFilterType>('ALL');
  const textbookId = ref<number | null>(null);
  const sectionId = ref<number | null>(null);
  const coursewareSectionId = ref<number | null>(null);
  const subjectId = ref<number | null>(null);
  const coursewares = ref<ContentBrowseItem[]>([]);
  const sections = ref<CoursewareSectionItem[]>([]);
  const textbooks = ref<TextbookItem[]>([]);
  const textbookSections = ref<TextbookSectionItem[]>([]);
  const subjects = ref<PlatformSubjectItem[]>([]);
  const visibility = ref<ContentVisibilityItem | null>(null);
  const selectedCoursewareId = ref<number | null>(null);
  const loading = ref(false);
  const generation = ref(0);
  const assignments = ref<TeacherAssignmentItem[]>([]);

  const filterLabel = computed(() => {
    switch (filterType.value) {
      case 'UNMOUNTED':
        return '未挂载';
      case 'TEXTBOOK':
        return '按教材';
      case 'TEXTBOOK_SECTION':
        return '按教材小节';
      case 'COURSEWARE_SECTION':
        return '按课件小节';
      default:
        return '全部';
    }
  });

  const loadCoursewares = async () => {
    loading.value = true;
    const snapshot = ++generation.value;
    try {
      const params = {
        filterType: filterType.value,
        textbookId: textbookId.value,
        sectionId: sectionId.value,
        coursewareSectionId: coursewareSectionId.value,
        subjectId: subjectId.value,
      };
      const list = await listTeacherCoursewares(params);
      if (snapshot !== generation.value) {
        return;
      }
      coursewares.value = list;
    } finally {
      loading.value = false;
    }
  };

  const loadSections = async (coursewareId: number) => {
    const snapshot = ++generation.value;
    selectedCoursewareId.value = coursewareId;
    visibility.value = await getTeacherCoursewareVisibility(coursewareId);
    const list = await listTeacherCoursewareSections(coursewareId);
    if (snapshot !== generation.value) {
      return;
    }
    sections.value = list;
  };

  const loadFilters = async () => {
    const [bookList, subjectList] = await Promise.all([listTeacherTextbooks(), listTeacherSubjects()]);
    textbooks.value = bookList;
    subjects.value = subjectList;
  };

  const loadTextbookSections = async (textbookId: number | null) => {
    if (!textbookId) {
      textbookSections.value = [];
      return;
    }
    textbookSections.value = await listTeacherTextbookSections(textbookId);
  };

  const loadSection = async (coursewareId: number, sectionIdValue: number) => {
    const section = await getTeacherCoursewareSection(coursewareId, sectionIdValue);
    sections.value = [section];
  };

  const readAsset = async (path: string, assets: CoursewareAssetItem[] = []): Promise<string> => {
    const coursewareId = selectedCoursewareId.value;
    const asset = assets.find((item) => item.filePath === path);
    if (!coursewareId || !asset) {
      return '';
    }
    const blob = await readTeacherCoursewareAssetBlob(coursewareId, asset.id);
    return URL.createObjectURL(blob);
  };

  const loadAssignments = async (classId: number) => {
    assignments.value = await listCoursewareAssignments(classId);
  };

  const createAssignment = async (payload: CreateAssignmentPayload) =>
    createCoursewareAssignment(payload);

  return {
    filterType,
    filterLabel,
    textbookId,
    sectionId,
    coursewareSectionId,
    subjectId,
    coursewares,
    sections,
    textbooks,
    textbookSections,
    subjects,
    visibility,
    selectedCoursewareId,
    loading,
    assignments,
    loadCoursewares,
    loadSections,
    loadFilters,
    loadTextbookSections,
    loadSection,
    readAsset,
    loadAssignments,
    createAssignment,
  };
};

export type TeacherContentContext = ReturnType<typeof useTeacherContent>;

export const teacherContentKey: InjectionKey<TeacherContentContext> = Symbol('teacher-content');

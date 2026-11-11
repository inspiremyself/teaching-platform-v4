import { computed, ref } from 'vue';
import type { InjectionKey } from 'vue';
import {
  changePlatformSubjectStatus,
  createPlatformSubject,
  createPlatformTextbook,
  createPlatformTextbookSection,
  deletePlatformCourseware,
  generateCourseware,
  getPlatformCourseware,
  grantCoursewareSchools,
  listCoursewareGrants,
  listPlatformCoursewares,
  listPlatformContentSubjects,
  listPlatformTextbookSections,
  listPlatformTextbooks,
  publishCourseware,
  readCoursewareAssetBlob,
  revokeCoursewareSchool,
  unpublishCourseware,
  updatePlatformCourseware,
  uploadCoursewareAsset,
} from '@/api/platformContent';
import { listPlatformSchools } from '@/api/admin';
import type {
  CoursewareDetail,
  CoursewareItem,
  GenerateCoursewarePayload,
  PlatformSubjectItem,
  SectionPayload,
  ShareGrantItem,
  TextbookItem,
  TextbookSectionItem,
  UpdateCoursewarePayload,
} from '@/types/platformContent';
import type { PlatformSchoolItem } from '@/types/admin';

export interface GenerateForm {
  title: string;
  subtitle: string;
  globalSubjectId: number | null;
  textbookSectionId: number | null;
  coursewareId: number | null;
}

export const usePlatformContent = () => {
  const subjects = ref<PlatformSubjectItem[]>([]);
  const textbooks = ref<TextbookItem[]>([]);
  const textbookSections = ref<TextbookSectionItem[]>([]);
  const coursewares = ref<CoursewareItem[]>([]);
  const grants = ref<ShareGrantItem[]>([]);
  const schools = ref<PlatformSchoolItem[]>([]);
  const current = ref<CoursewareDetail | null>(null);
  const loading = ref(false);
  const generateForm = ref<GenerateForm>({
    title: '',
    subtitle: '',
    globalSubjectId: null,
    textbookSectionId: null,
    coursewareId: null,
  });
  const generation = ref(0);

  const publishedCoursewares = computed(() =>
    coursewares.value.filter((item) => item.status === 'PUBLISHED'),
  );

  const loadSubjects = async () => {
    subjects.value = await listPlatformContentSubjects();
  };

  const loadTextbooks = async (globalSubjectId?: number | null) => {
    textbooks.value = await listPlatformTextbooks(globalSubjectId ?? null);
  };

  const loadTextbookSections = async (textbookId: number) => {
    textbookSections.value = await listPlatformTextbookSections(textbookId);
  };

  const loadCoursewares = async () => {
    coursewares.value = await listPlatformCoursewares();
  };

  const loadSchools = async () => {
    schools.value = await listPlatformSchools();
  };

  const loadGrants = async (coursewareId: number) => {
    grants.value = await listCoursewareGrants(coursewareId);
  };

  const loadCurrent = async (coursewareId: number) => {
    const snapshot = ++generation.value;
    const detail = await getPlatformCourseware(coursewareId);
    if (snapshot !== generation.value) {
      return;
    }
    current.value = detail;
  };

  const loadAll = async () => {
    loading.value = true;
    try {
      await Promise.all([loadSubjects(), loadTextbooks(), loadCoursewares(), loadSchools()]);
    } finally {
      loading.value = false;
    }
  };

  const toggleSubjectStatus = async (subject: PlatformSubjectItem) => {
    const next = subject.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    await changePlatformSubjectStatus(subject.id, next);
    await loadSubjects();
  };

  const addSubject = async (code: string, name: string) => {
    await createPlatformSubject({ code, name });
    await loadSubjects();
  };

  const addTextbook = async (globalSubjectId: number, code: string, title: string) => {
    await createPlatformTextbook({ globalSubjectId, code, title });
    await loadTextbooks();
  };

  const addTextbookSection = async (
    textbookId: number,
    heading: string,
    sortOrder: number,
    parentId: number | null,
  ) => {
    await createPlatformTextbookSection(textbookId, { heading, sortOrder, parentId });
    await loadTextbookSections(textbookId);
  };

  const runGenerate = async () => {
    const form = generateForm.value;
    if (!form.globalSubjectId || !form.textbookSectionId) {
      throw new Error('请选择平台学科与教材小节');
    }
    const snapshot = ++generation.value;
    const payload: GenerateCoursewarePayload = {
      coursewareId: form.coursewareId,
      globalSubjectId: form.globalSubjectId,
      textbookSectionId: form.textbookSectionId,
      title: form.title,
      subtitle: form.subtitle,
    };
    const detail = await generateCourseware(payload);
    if (snapshot !== generation.value) {
      return null;
    }
    current.value = detail;
    await loadCoursewares();
    return detail;
  };

  const saveCourseware = async (coursewareId: number, payload: UpdateCoursewarePayload) => {
    current.value = await updatePlatformCourseware(coursewareId, payload);
    await loadCoursewares();
  };

  const publish = async (coursewareId: number) => {
    current.value = await publishCourseware(coursewareId);
    await loadCoursewares();
  };

  const unpublish = async (coursewareId: number) => {
    current.value = await unpublishCourseware(coursewareId);
    await loadCoursewares();
  };

  const remove = async (coursewareId: number) => {
    await deletePlatformCourseware(coursewareId);
    if (current.value?.courseware.id === coursewareId) {
      current.value = null;
    }
    await loadCoursewares();
  };

  const grant = async (coursewareId: number, schoolIds: number[]) => {
    grants.value = await grantCoursewareSchools(coursewareId, { schoolIds });
    await loadCoursewares();
  };

  const revoke = async (coursewareId: number, schoolId: number) => {
    await revokeCoursewareSchool(coursewareId, schoolId);
    await loadGrants(coursewareId);
  };

  const uploadAsset = async (coursewareId: number, file: File) => {
    await uploadCoursewareAsset(coursewareId, file);
    await loadCurrent(coursewareId);
  };

  const readAsset = async (path: string): Promise<string> => {
    const detail = current.value;
    if (!detail) {
      return '';
    }
    const asset = detail.assets.find((item) => item.filePath === path);
    if (!asset) {
      return '';
    }
    const blob = await readCoursewareAssetBlob(detail.courseware.id, asset.id);
    return URL.createObjectURL(blob);
  };

  const sectionsPayload = (detail: CoursewareDetail): SectionPayload[] =>
    detail.sections.map((section) => ({
      id: section.id,
      heading: section.heading,
      sortOrder: section.sortOrder,
      contentJson: section.contentJson,
    }));

  return {
    subjects,
    textbooks,
    textbookSections,
    coursewares,
    publishedCoursewares,
    grants,
    schools,
    current,
    loading,
    generateForm,
    generation,
    loadAll,
    loadSubjects,
    loadTextbooks,
    loadTextbookSections,
    loadCoursewares,
    loadSchools,
    loadGrants,
    loadCurrent,
    toggleSubjectStatus,
    addSubject,
    addTextbook,
    addTextbookSection,
    runGenerate,
    saveCourseware,
    publish,
    unpublish,
    remove,
    grant,
    revoke,
    uploadAsset,
    readAsset,
    sectionsPayload,
  };
};

export type PlatformContentContext = ReturnType<typeof usePlatformContent>;

export const platformContentKey: InjectionKey<PlatformContentContext> = Symbol('platform-content');

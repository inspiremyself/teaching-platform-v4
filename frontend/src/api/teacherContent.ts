import request from '@/utils/request';
import type {
  ContentBrowseItem,
  ContentFilterParams,
  ContentVisibilityItem,
  CoursewareSectionItem,
  CreateAssignmentPayload,
  CreateAssignmentResult,
  PlatformSubjectItem,
  TeacherAssignmentItem,
  TextbookItem,
} from '@/types/teacherContent';
import type { TextbookSectionItem } from '@/types/platformContent';

const BASE = '/teacher/content';

export const listTeacherCoursewares = (params: ContentFilterParams = {}) =>
  request.get<ContentBrowseItem[]>(`${BASE}/coursewares`, { params });

export const listTeacherCoursewareSections = (coursewareId: number) =>
  request.get<CoursewareSectionItem[]>(`${BASE}/coursewares/${coursewareId}/sections`);

export const getTeacherCoursewareSection = (coursewareId: number, sectionId: number) =>
  request.get<CoursewareSectionItem>(`${BASE}/coursewares/${coursewareId}/sections/${sectionId}`);

export const getTeacherCoursewareVisibility = (coursewareId: number) =>
  request.get<ContentVisibilityItem>(`${BASE}/coursewares/${coursewareId}/visibility`);

export const listTeacherTextbooks = () => request.get<TextbookItem[]>(`${BASE}/textbooks`);

export const listTeacherSubjects = () => request.get<PlatformSubjectItem[]>(`${BASE}/subjects`);

export const listTeacherTextbookSections = (textbookId: number) =>
  request.get<TextbookSectionItem[]>(`${BASE}/textbooks/${textbookId}/sections`);

export const readTeacherCoursewareAssetBlob = async (
  coursewareId: number,
  assetId: number,
): Promise<Blob> => {
  const buffer = await request.get<ArrayBuffer>(
    `${BASE}/coursewares/${coursewareId}/assets/${assetId}`,
    { responseType: 'arraybuffer' },
  );
  return new Blob([buffer]);
};

export const createCoursewareAssignment = (data: CreateAssignmentPayload) =>
  request.post<CreateAssignmentResult>(`${BASE}/assignments`, data);

export const listCoursewareAssignments = (classId: number) =>
  request.get<TeacherAssignmentItem[]>(`${BASE}/assignments`, { params: { classId } });

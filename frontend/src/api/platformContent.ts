import request from '@/utils/request';
import type {
  CoursewareAssetItem,
  CoursewareDetail,
  CoursewareItem,
  CreatePlatformSubjectPayload,
  CreateTextbookPayload,
  CreateTextbookSectionPayload,
  GenerateCoursewarePayload,
  GrantSchoolsPayload,
  PlatformSubjectItem,
  ShareGrantItem,
  TextbookItem,
  TextbookSectionItem,
  UpdateCoursewarePayload,
  UpdatePlatformSubjectPayload,
  UpdateTextbookPayload,
  UpdateTextbookSectionPayload,
} from '@/types/platformContent';

const BASE = '/admin/platform-content';

export const listPlatformContentSubjects = () => request.get<PlatformSubjectItem[]>(`${BASE}/subjects`);

export const createPlatformSubject = (data: CreatePlatformSubjectPayload) =>
  request.post<PlatformSubjectItem>(`${BASE}/subjects`, data);

export const updatePlatformSubject = (id: number, data: UpdatePlatformSubjectPayload) =>
  request.put<PlatformSubjectItem>(`${BASE}/subjects/${id}`, data);

export const changePlatformSubjectStatus = (id: number, status: string) =>
  request.post<PlatformSubjectItem>(`${BASE}/subjects/${id}/status`, { status });

export const listPlatformTextbooks = (globalSubjectId?: number | null) =>
  request.get<TextbookItem[]>(`${BASE}/textbooks`, {
    params: globalSubjectId ? { globalSubjectId } : undefined,
  });

export const createPlatformTextbook = (data: CreateTextbookPayload) =>
  request.post<TextbookItem>(`${BASE}/textbooks`, data);

export const updatePlatformTextbook = (id: number, data: UpdateTextbookPayload) =>
  request.put<TextbookItem>(`${BASE}/textbooks/${id}`, data);

export const listPlatformTextbookSections = (textbookId: number) =>
  request.get<TextbookSectionItem[]>(`${BASE}/textbooks/${textbookId}/sections`);

export const createPlatformTextbookSection = (textbookId: number, data: CreateTextbookSectionPayload) =>
  request.post<TextbookSectionItem>(`${BASE}/textbooks/${textbookId}/sections`, data);

export const updatePlatformTextbookSection = (
  textbookId: number,
  sectionId: number,
  data: UpdateTextbookSectionPayload,
) => request.put<TextbookSectionItem>(`${BASE}/textbooks/${textbookId}/sections/${sectionId}`, data);

export const generateCourseware = (data: GenerateCoursewarePayload) =>
  request.post<CoursewareDetail>(`${BASE}/coursewares/generate`, data);

export const listPlatformCoursewares = () => request.get<CoursewareItem[]>(`${BASE}/coursewares`);

export const getPlatformCourseware = (id: number) => request.get<CoursewareDetail>(`${BASE}/coursewares/${id}`);

export const getPlatformCoursewarePreview = (id: number) =>
  request.get<CoursewareDetail>(`${BASE}/coursewares/${id}/preview`);

export const updatePlatformCourseware = (id: number, data: UpdateCoursewarePayload) =>
  request.put<CoursewareDetail>(`${BASE}/coursewares/${id}`, data);

export const deletePlatformCourseware = (id: number) =>
  request.delete<{ deleted: boolean }>(`${BASE}/coursewares/${id}`);

export const publishCourseware = (id: number) =>
  request.post<CoursewareDetail>(`${BASE}/coursewares/${id}/publish`);

export const unpublishCourseware = (id: number) =>
  request.post<CoursewareDetail>(`${BASE}/coursewares/${id}/unpublish`);

export const listCoursewareGrants = (id: number) =>
  request.get<ShareGrantItem[]>(`${BASE}/coursewares/${id}/grants`);

export const grantCoursewareSchools = (id: number, data: GrantSchoolsPayload) =>
  request.post<ShareGrantItem[]>(`${BASE}/coursewares/${id}/grants`, data);

export const revokeCoursewareSchool = (id: number, schoolId: number) =>
  request.delete<{ deleted: boolean }>(`${BASE}/coursewares/${id}/grants/${schoolId}`);

export const uploadCoursewareAsset = (coursewareId: number, file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<CoursewareAssetItem>(`${BASE}/coursewares/${coursewareId}/assets`, formData);
};

export const readCoursewareAssetBlob = async (coursewareId: number, assetId: number): Promise<Blob> => {
  const buffer = await request.get<ArrayBuffer>(`${BASE}/coursewares/${coursewareId}/assets/${assetId}`, {
    responseType: 'arraybuffer',
  });
  return new Blob([buffer]);
};

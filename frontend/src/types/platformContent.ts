import type { CoursewareAssetKind, CoursewareStatus, PlatformSubjectStatus } from './enums';

export interface PlatformSubjectItem {
  id: number;
  code: string;
  name: string;
  status: PlatformSubjectStatus;
}

export interface TextbookItem {
  id: number;
  globalSubjectId: number;
  code: string;
  title: string;
  sectionCount: number;
}

export interface TextbookSectionItem {
  id: number;
  textbookId: number;
  parentId: number | null;
  heading: string;
  sortOrder: number;
  leaf: boolean;
}

export interface CoursewareItem {
  id: number;
  title: string;
  subtitle: string | null;
  globalSubjectId: number | null;
  status: CoursewareStatus;
  publishedAt: string | null;
  sectionCount: number;
}

export interface CoursewareSectionItem {
  id: number;
  coursewareId: number;
  heading: string;
  sortOrder: number;
  contentJson: string;
  assets: CoursewareAssetItem[];
}

export interface CoursewareAssetItem {
  id: number;
  kind: CoursewareAssetKind;
  filePath: string;
  mime: string | null;
  sizeBytes: number | null;
}

export interface CoursewareDetail {
  courseware: CoursewareItem;
  sections: CoursewareSectionItem[];
  mounts: MountPayload[];
  assets: CoursewareAssetItem[];
}

export interface ShareGrantItem {
  id: number;
  schoolId: number;
  grantedBy: number;
}

export interface MountPayload {
  mountType: string;
  mountTargetId: number;
}

export interface SectionPayload {
  id?: number | null;
  heading: string;
  sortOrder: number;
  contentJson: string;
}

export interface CreatePlatformSubjectPayload {
  code: string;
  name: string;
}

export interface UpdatePlatformSubjectPayload {
  name: string;
}

export interface CreateTextbookPayload {
  globalSubjectId: number;
  code: string;
  title: string;
}

export interface UpdateTextbookPayload {
  globalSubjectId: number;
  title: string;
}

export interface CreateTextbookSectionPayload {
  parentId?: number | null;
  heading: string;
  sortOrder: number;
}

export interface UpdateTextbookSectionPayload {
  parentId?: number | null;
  heading: string;
  sortOrder: number;
}

export interface GenerateCoursewarePayload {
  coursewareId?: number | null;
  globalSubjectId: number;
  textbookSectionId: number;
  title: string;
  subtitle?: string;
}

export interface UpdateCoursewarePayload {
  title: string;
  subtitle?: string;
  globalSubjectId: number;
  sections: SectionPayload[];
  mounts?: MountPayload[];
}

export interface GrantSchoolsPayload {
  schoolIds: number[];
}

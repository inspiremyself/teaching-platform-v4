import type { ContentFilterType, CoursewareStatus } from './enums';
import type { CoursewareSectionItem, PlatformSubjectItem, TextbookItem } from './platformContent';

export interface ContentBrowseItem {
  id: number;
  title: string;
  subtitle: string | null;
  globalSubjectId: number | null;
  status: CoursewareStatus;
  sectionCount: number;
}

export interface ContentVisibilityItem {
  visible: boolean;
  status: string;
}

export interface ContentFilterParams {
  filterType?: ContentFilterType;
  textbookId?: number | null;
  sectionId?: number | null;
  coursewareSectionId?: number | null;
  subjectId?: number | null;
}

export type { CoursewareSectionItem, PlatformSubjectItem, TextbookItem };

export interface AssignmentTargetPayload {
  classId: number;
  studentIds: number[];
}

export interface CreateAssignmentPayload {
  coursewareId: number;
  sectionIds: number[];
  targets: AssignmentTargetPayload[];
  startAt?: string | null;
  endAt?: string | null;
}

export interface CreateAssignmentResult {
  assignmentGroupId: string;
  rowCount: number;
}

export interface TeacherAssignmentItem {
  assignmentGroupId: string;
  coursewareId: number;
  coursewareTitle: string;
  classId: number;
  targetType: 'CLASS' | 'STUDENT';
  targetId: number;
  sectionId: number | null;
  displayOrder: number;
  startAt: string | null;
  endAt: string | null;
  createdAt: string;
}

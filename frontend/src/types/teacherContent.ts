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

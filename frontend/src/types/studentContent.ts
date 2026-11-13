import type { CoursewareAssetItem } from './platformContent';

export interface StudentSectionRef {
  sectionId: number;
  heading: string;
}

export interface StudentAssignmentGroup {
  assignmentGroupId: string;
  coursewareId: number;
  coursewareTitle: string;
  createdAt: string;
  sections: StudentSectionRef[];
}

export interface StudentSectionContent {
  coursewareId: number;
  sectionId: number;
  heading: string;
  contentJson: string;
  assets: CoursewareAssetItem[];
}

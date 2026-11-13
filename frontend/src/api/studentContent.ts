import request from '@/utils/request';
import type {
  StudentAssignmentGroup,
  StudentSectionContent,
} from '@/types/studentContent';

const BASE = '/student/content';

export const listStudentAssignments = () =>
  request.get<StudentAssignmentGroup[]>(`${BASE}/assignments`);

export const getStudentSection = (coursewareId: number, sectionId: number) =>
  request.get<StudentSectionContent>(`${BASE}/coursewares/${coursewareId}/sections/${sectionId}`);

export const readStudentAsset = (coursewareId: number, assetId: number) =>
  request.get<ArrayBuffer>(
    `${BASE}/coursewares/${coursewareId}/assets/${assetId}`,
    { responseType: 'arraybuffer' },
  );

import type { ClassStatus } from '@/types/class';
import type {
  CreateStudentPayload,
  ImportStudentsPayload,
  ImportStudentsResult,
  Student,
  StudentStatus,
  UpdateStudentPayload,
} from '@/types/student';

export type TeacherStatus = 'ACTIVE' | 'DISABLED';
export type ClassTeacherRole = 'HEAD' | 'SUBJECT';
export type SchoolStatus = 'ACTIVE' | 'DISABLED';
export type SubjectStatus = 'ACTIVE' | 'DISABLED';

export interface AdminTeacher {
  id: number;
  username: string;
  displayName: string;
  status: TeacherStatus;
  classCount: number;
}

export interface AdminSubject {
  id: number;
  name: string;
  code: string;
  status: SubjectStatus;
  assignmentCount: number;
}

export interface CreateTeacherPayload {
  username: string;
  displayName: string;
  password: string;
}

export interface UpdateTeacherPayload {
  displayName: string;
}

export interface CreateSubjectPayload {
  name: string;
  code: string;
}

export interface UpdateSubjectPayload {
  name: string;
  code: string;
}

export interface ClassTeacherAssignment {
  teacherUserId: number;
  teacherName: string;
  role: ClassTeacherRole;
  subjectId: number | null;
  subjectName: string | null;
}

export interface ClassTeacherInput {
  teacherUserId: number;
  role: ClassTeacherRole;
  subjectId?: number | null;
}

export interface AdminClass {
  id: number;
  name: string;
  code: string;
  status: ClassStatus;
  studentCount: number;
  teachers: ClassTeacherAssignment[];
}

export interface CreateAdminClassPayload {
  name: string;
  code: string;
  teachers: ClassTeacherInput[];
}

export interface UpdateAdminClassPayload {
  name: string;
  status: ClassStatus;
  teachers: ClassTeacherInput[];
}

export interface AdminStudent extends Student {
  className: string;
  classCode: string;
}

export interface SchoolInfo {
  id: number;
  name: string;
  code: string;
  status: SchoolStatus;
}

export interface AuditLogListItem {
  id: number;
  userId: number | null;
  username: string | null;
  displayName: string | null;
  action: string;
  resourceType: string;
  resourceId: number | null;
  detailJson: string | null;
  traceId: string | null;
  clientIp: string | null;
  actorRole: string | null;
  createdAt: string;
}

export interface ListAuditLogsParams {
  page?: number;
  pageSize?: number;
  action?: string;
  resourceType?: string;
  userId?: number;
  from?: string;
  to?: string;
}

export interface UpdateSchoolPayload {
  name: string;
}

export type {
  CreateStudentPayload,
  ImportStudentsPayload,
  ImportStudentsResult,
  StudentStatus,
  UpdateStudentPayload,
};

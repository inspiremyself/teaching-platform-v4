import request from '@/utils/request';
import type {
  AdminClass,
  AdminStudent,
  AdminSubject,
  AdminTeacher,
  AuditLogListItem,
  CreateAdminClassPayload,
  CreateStudentPayload,
  CreateSubjectPayload,
  CreateTeacherPayload,
  ImportStudentsPayload,
  ImportStudentsResult,
  ListAuditLogsParams,
  ResetStudentPasswordPayload,
  SchoolInfo,
  StudentStatus,
  SubjectStatus,
  TeacherStatus,
  UpdateAdminClassPayload,
  UpdateSchoolPayload,
  UpdateStudentPayload,
  UpdateSubjectPayload,
  UpdateTeacherPayload,
} from '@/types/admin';
import type { PageResult } from '@/types/common';

export const getSchool = () => request.get<SchoolInfo>('/admin/school');

export const updateSchool = (data: UpdateSchoolPayload) => request.put<SchoolInfo>('/admin/school', data);

export const listTeachers = (status?: TeacherStatus) =>
  request.get<AdminTeacher[]>('/admin/teachers', { params: status ? { status } : undefined });

export const createTeacher = (data: CreateTeacherPayload) => request.post<AdminTeacher>('/admin/teachers', data);

export const updateTeacher = (id: number, data: UpdateTeacherPayload) =>
  request.put<AdminTeacher>(`/admin/teachers/${id}`, data);

export const changeTeacherStatus = (id: number, status: TeacherStatus) =>
  request.post<AdminTeacher>(`/admin/teachers/${id}/status`, { status });

export const listSubjects = (status?: SubjectStatus) =>
  request.get<AdminSubject[]>('/admin/subjects', { params: status ? { status } : undefined });

export const createSubject = (data: CreateSubjectPayload) =>
  request.post<AdminSubject>('/admin/subjects', data);

export const updateSubject = (id: number, data: UpdateSubjectPayload) =>
  request.put<AdminSubject>(`/admin/subjects/${id}`, data);

export const changeSubjectStatus = (id: number, status: SubjectStatus) =>
  request.post<AdminSubject>(`/admin/subjects/${id}/status`, { status });

export const listAdminClasses = (teacherUserId?: number) =>
  request.get<AdminClass[]>('/admin/classes', {
    params: teacherUserId ? { teacherUserId } : undefined,
  });

export const getAdminClass = (id: number) => request.get<AdminClass>(`/admin/classes/${id}`);

export const createAdminClass = (data: CreateAdminClassPayload) =>
  request.post<AdminClass>('/admin/classes', data);

export const updateAdminClass = (id: number, data: UpdateAdminClassPayload) =>
  request.put<AdminClass>(`/admin/classes/${id}`, data);

export const deleteAdminClass = (id: number) => request.delete<{ deleted: boolean }>(`/admin/classes/${id}`);

export const listAdminStudents = (classId?: number) =>
  request.get<AdminStudent[]>('/admin/students', { params: classId ? { classId } : undefined });

export const createAdminStudent = (data: CreateStudentPayload) =>
  request.post<AdminStudent>('/admin/students', data);

export const updateAdminStudent = (id: number, data: UpdateStudentPayload) =>
  request.put<AdminStudent>(`/admin/students/${id}`, data);

export const importAdminStudents = (data: ImportStudentsPayload) =>
  request.post<ImportStudentsResult>('/admin/students/import', data);

export const changeAdminStudentStatus = (id: number, status: StudentStatus) =>
  request.post<AdminStudent>(`/admin/students/${id}/status`, { status });

export const resetAdminStudentPassword = (id: number, data: ResetStudentPasswordPayload) =>
  request.post<{ reset: boolean }>(`/admin/students/${id}/password`, data);

export const listAuditLogs = (params: ListAuditLogsParams = {}) =>
  request.get<PageResult<AuditLogListItem>>('/admin/audit-logs', { params });

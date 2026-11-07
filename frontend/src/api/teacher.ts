import request from '@/utils/request';
import type { TeacherAnalysisOverview } from '@/types/analysis';

export const getAnalysis = () => request.get<TeacherAnalysisOverview>('/analysis/teacher-overview');
export const getTeacherDashboard = () => request.get<any>('/analysis/dashboard');
export const getPlagiarism = () => request.get<any[]>('/plagiarism/tasks');
export const getPlagiarismDetail = (id: string | number) => request.get<any>(`/plagiarism/tasks/${id}`);
export const reviewPlagiarism = (id: string | number, data: any) => request.post(`/plagiarism/tasks/${id}/review`, data);

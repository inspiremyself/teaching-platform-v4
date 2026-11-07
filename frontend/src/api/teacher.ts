import request from '@/utils/request';
import type { TeacherAnalysisOverview, TeacherDashboardData } from '@/types/analysis';

export interface PlagiarismTask {
  id: number;
  studentName?: string;
  businessType?: string;
  businessName?: string;
  similarityRate?: number | null;
  topMatchSummary?: string | null;
  riskLevel?: string | null;
  status?: string | null;
  reviewStatus?: string | null;
  reviewComment?: string | null;
  reviewConclusion?: string | null;
  review?: {
    status?: string | null;
    reviewStatus?: string | null;
    conclusion?: string | null;
    reviewConclusion?: string | null;
    comment?: string | null;
    reviewComment?: string | null;
    reviewedAt?: string | null;
  } | null;
  rawResultJson?: string | Record<string, unknown> | null;
  topMatchReference?: string | null;
  matchedSegments?: unknown;
  reviewContext?: unknown;
  context?: unknown;
}

export interface ReviewPlagiarismPayload {
  reviewComment: string;
}

export const getAnalysis = () => request.get<TeacherAnalysisOverview>('/analysis/teacher-overview');
export const getTeacherDashboard = () => request.get<TeacherDashboardData>('/analysis/dashboard');
export const getPlagiarism = () => request.get<PlagiarismTask[]>('/plagiarism/tasks');
export const getPlagiarismDetail = (id: string | number) =>
  request.get<PlagiarismTask>(`/plagiarism/tasks/${id}`);
export const reviewPlagiarism = (id: string | number, data: ReviewPlagiarismPayload) =>
  request.post<void>(`/plagiarism/tasks/${id}/review`, data);

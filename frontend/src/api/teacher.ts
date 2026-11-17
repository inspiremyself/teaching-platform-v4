import request from '@/utils/request';
import { teacherParticipationPath } from '@/api/endpoints';
import type {
  ParticipationBusinessType,
  TeacherAnalysisOverview,
  TeacherDashboardData,
  TeacherParticipationDetail,
} from '@/types/analysis';

export const getAnalysis = () => request.get<TeacherAnalysisOverview>('/analysis/teacher-overview');
export const getTeacherDashboard = () => request.get<TeacherDashboardData>('/analysis/dashboard');
export const getTeacherParticipation = (
  businessType: ParticipationBusinessType,
  businessId: number,
) => request.get<TeacherParticipationDetail>(teacherParticipationPath(businessType, businessId));

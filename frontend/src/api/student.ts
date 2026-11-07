import request from '@/utils/request';
import type { StudentDashboardData, StudentScoreOverview } from '@/types/analysis';

export const getScores = () => request.get<StudentScoreOverview>('/analysis/my-score-overview');
export const getStudentDashboard = () =>
  request.get<StudentDashboardData>('/analysis/student-dashboard');

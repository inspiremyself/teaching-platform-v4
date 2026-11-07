import request from '@/utils/request';
import type { StudentScoreOverview } from '@/types/analysis';

export const getScores = () => request.get<StudentScoreOverview>('/analysis/my-score-overview');
export const getStudentDashboard = () => request.get<any>('/analysis/student-dashboard');

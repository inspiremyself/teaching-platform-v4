import request from '@/utils/request';
import type { TeacherAnalysisOverview } from '@/types/analysis';

export const getMaterials = () => request.get<any[]>('/materials');
export const createMaterial = (data: any) => request.post('/materials', data);
export const updateMaterial = (id: string | number, data: any) => request.put(`/materials/${id}`, data);
export const deleteMaterial = (id: string | number) => request.delete(`/materials/${id}`);

export const getQuestions = () => request.get<any[]>('/questions');
export const createQuestion = (data: any) => request.post('/questions', data);
export const updateQuestion = (id: string | number, data: any) => request.put(`/questions/${id}`, data);
export const deleteQuestion = (id: string | number) => request.delete(`/questions/${id}`);

export const getLabs = () => request.get<any[]>('/labs');
export const createLab = (data: any) => request.post('/labs', data);
export const updateLab = (id: string | number, data: any) => request.put(`/labs/${id}`, data);
export const getLabSteps = (id: string | number) => request.get<any[]>(`/labs/${id}/steps`);
export const createLabStep = (id: string | number, data: any) => request.post(`/labs/${id}/steps`, data);
export const gradeLabReport = (id: string | number, data: any) => request.post(`/teacher/lab-reports/${id}/grade`, data);

export const getHomeworks = () => request.get<any[]>('/homeworks');
export const createHomework = (data: any) => request.post('/homeworks', data);
export const updateHomework = (id: string | number, data: any) => request.put(`/homeworks/${id}`, data);
export const getHomeworkDetail = (id: string | number) => request.get<any>(`/homeworks/${id}`);
export const getHomeworkSubmissions = (id: string | number) => request.get<any[]>(`/teacher/homeworks/${id}/submissions`);
export const gradeHomeworkSubmission = (id: string | number, data: any) => request.post(`/teacher/homework-submissions/${id}/grade`, data);

export { listExams as getExams, getExamDetail, createExam, updateExam, changeExamStatus, getExamResults, getExamSubmissionDetail, gradeExamSubmission } from './exams';

export const getAnalysis = () => request.get<TeacherAnalysisOverview>('/analysis/teacher-overview');
export const getTeacherDashboard = () => request.get<any>('/analysis/dashboard');
export const getPlagiarism = () => request.get<any[]>('/plagiarism/tasks');
export const getPlagiarismDetail = (id: string | number) => request.get<any>(`/plagiarism/tasks/${id}`);
export const reviewPlagiarism = (id: string | number, data: any) => request.post(`/plagiarism/tasks/${id}/review`, data);

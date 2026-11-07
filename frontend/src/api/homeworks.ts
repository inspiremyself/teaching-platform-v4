import request from '@/utils/request';
import type {
  AddHomeworkQuestionFromBankRequest,
  ChangeHomeworkStatusRequest,
  CopyQuestionToBankRequest,
  CopyQuestionToBankResult,
  CreateHomeworkRequest,
  GradeHomeworkAnswerRequest,
  GradeHomeworkRequest,
  GradeHomeworkResult,
  HomeworkListItem,
  HomeworkSubmissionItem,
  HomeworkSubmissionReturnResult,
  SaveHomeworkQuestionRequest,
  SaveHomeworkDraftResult,
  StudentHomeworkDetail,
  SubmitHomeworkRequest,
  TeacherHomeworkDetail,
  TeacherHomeworkQuestionItem,
} from '@/types/homework';

export const listTeacherHomeworks = () => request.get<HomeworkListItem[]>('/teacher/homeworks');

export const createHomework = (data: CreateHomeworkRequest) => request.post<HomeworkListItem>('/teacher/homeworks', data);

export const updateHomework = (id: number | string, data: CreateHomeworkRequest) =>
  request.put<HomeworkListItem>(`/teacher/homeworks/${id}`, data);

export const changeHomeworkStatus = (id: number | string, data: ChangeHomeworkStatusRequest) =>
  request.post<HomeworkListItem>(`/teacher/homeworks/${id}/status`, data);

export const addHomeworkQuestionFromBank = (id: number | string, data: AddHomeworkQuestionFromBankRequest) =>
  request.post<TeacherHomeworkQuestionItem>(`/teacher/homeworks/${id}/questions/bank`, data);

export const addInlineHomeworkQuestion = (id: number | string, data: SaveHomeworkQuestionRequest) =>
  request.post<TeacherHomeworkQuestionItem>(`/teacher/homeworks/${id}/questions/inline`, data);

/** 教师作业详情：编辑弹窗回显与增量提交的入口 */
export const getTeacherHomeworkDetail = (id: number | string) =>
  request.get<TeacherHomeworkDetail>(`/teacher/homeworks/${id}`);

/** 更新作业题目快照（BANK 与 INLINE 共用，仅作用于本作业） */
export const updateHomeworkQuestion = (id: number | string, questionId: number, data: SaveHomeworkQuestionRequest) =>
  request.put<TeacherHomeworkQuestionItem>(`/teacher/homeworks/${id}/questions/${questionId}`, data);

/** 删除作业题目（仅草稿且无学生作答） */
export const deleteHomeworkQuestion = (id: number | string, questionId: number) =>
  request.delete<void>(`/teacher/homeworks/${id}/questions/${questionId}`);

/** 把作业题复制到题库（幂等，重复复制返回已有题目） */
export const copyHomeworkQuestionToBank = (homeworkQuestionId: number, data: CopyQuestionToBankRequest) =>
  request.post<CopyQuestionToBankResult>(`/teacher/homework-questions/${homeworkQuestionId}/copy-to-bank`, data);

export const listHomeworkSubmissions = (homeworkId: number | string) =>
  request.get<HomeworkSubmissionItem[]>(`/teacher/homeworks/${homeworkId}/submissions`);

export const getHomeworkSubmissionDetail = (submissionId: number | string) =>
  request.get<HomeworkSubmissionItem>(`/teacher/homework-submissions/${submissionId}`);

export const gradeHomeworkSubmission = (submissionId: number | string, data: GradeHomeworkRequest) =>
  request.post<GradeHomeworkResult>(`/teacher/homework-submissions/${submissionId}/grade`, data);

/** 教师打回已提交/已批改的作业提交，学生可重新编辑提交 */
export const returnHomeworkSubmission = (submissionId: number | string) =>
  request.post<HomeworkSubmissionReturnResult>(`/teacher/homework-submissions/${submissionId}/return`);

export const releaseHomeworkScores = (id: number | string) =>
  request.post<void>(`/teacher/homeworks/${id}/release-scores`);

export const gradeHomeworkAnswer = (answerId: number | string, data: GradeHomeworkAnswerRequest) =>
  request.post<void>(`/teacher/homework-answers/${answerId}/grade`, data);

export const listStudentHomeworks = () => request.get<HomeworkListItem[]>('/student/homeworks');

export const getStudentHomeworkDetail = (id: number | string) =>
  request.get<StudentHomeworkDetail>(`/student/homeworks/${id}`);

export const submitHomework = (id: number | string, data: SubmitHomeworkRequest) =>
  request.post<StudentHomeworkDetail>(`/student/homeworks/${id}/submit`, data);

/** 学生保存作业草稿：不触发查重与评分，仅落库当前作答内容。 */
export const saveStudentHomeworkDraft = (id: number | string, data: SubmitHomeworkRequest) =>
  request.put<SaveHomeworkDraftResult>(`/student/homeworks/${id}/draft`, data);

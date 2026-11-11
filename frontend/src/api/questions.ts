import request from '@/utils/request';
import type { AxiosRequestConfig } from 'axios';
import type { CreateQuestionPayload, QuestionItem, SaveQuestionPayload } from '@/types/question';

export const listQuestions = () => request.get<QuestionItem[]>('/questions');

export const createQuestion = (data: CreateQuestionPayload, config?: AxiosRequestConfig) =>
  request.post<QuestionItem>('/questions', data, config);

export const updateQuestion = (id: number, data: SaveQuestionPayload) => request.put<QuestionItem>(`/questions/${id}`, data);

export const deleteQuestion = (id: number) => request.delete<{ deleted: boolean }>(`/questions/${id}`);

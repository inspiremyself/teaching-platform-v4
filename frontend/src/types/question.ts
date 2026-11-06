export type QuestionType = 'SINGLE' | 'MULTI' | 'JUDGE' | 'FILL' | 'SHORT';
export type QuestionDifficulty = 'EASY' | 'MEDIUM' | 'HARD';

/** 题库题型守卫：仅接受收敛后的五类题型，供筛选、标签与归一逻辑复用 */
export const isQuestionType = (value: string): value is QuestionType =>
  value === 'SINGLE' || value === 'MULTI' || value === 'JUDGE' || value === 'FILL' || value === 'SHORT';

export interface QuestionItem {
  id: number;
  code: string;
  type: QuestionType | string;
  stem: string;
  difficulty: QuestionDifficulty | string;
  defaultScore: number;
  optionsJson: string | null;
  answerJson: string;
  analysisText: string;
  /** 题目来源：MANUAL / HOMEWORK_INLINE / LAB_STEP；null 表示历史或未知来源 */
  sourceType?: string | null;
  sourceRef?: string | null;
  scoringConfigJson?: string | null;
}

export interface SaveQuestionPayload {
  code: string;
  type: string;
  stem: string;
  difficulty: string;
  defaultScore: number;
  optionsJson: string | null;
  answerJson: string;
  analysisText: string;
}

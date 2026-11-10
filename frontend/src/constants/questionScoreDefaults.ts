/**
 * 组卷分值默认值。
 *
 * <p>题库不保存分值，分值只存在于组卷配置与题目快照中。
 * 从题库选题进入组卷配置清单时，按题型给出默认分，教师可随时在配置清单里修改。</p>
 */
export const QUESTION_DEFAULT_SCORE: Record<string, number> = {
  SINGLE: 2,
  MULTI: 3,
  JUDGE: 2,
  FILL: 4,
  SHORT: 10,
  CODE: 10,
}

/** 未知题型统一回落 5 分 */
export const FALLBACK_DEFAULT_SCORE = 5

/** 按题型取默认分：大小写与空白不敏感 */
export const defaultScoreByType = (type: string | null | undefined): number => {
  if (!type) {
    return FALLBACK_DEFAULT_SCORE
  }
  return QUESTION_DEFAULT_SCORE[type.trim().toUpperCase()] ?? FALLBACK_DEFAULT_SCORE
}

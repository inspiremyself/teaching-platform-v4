/** FNV-1a 32 位哈希：把学生 + 考试种子转换为稳定整数 */
function fnv1a32(input: string): number {
  let hash = 0x811c9dc5
  for (let i = 0; i < input.length; i += 1) {
    hash ^= input.charCodeAt(i)
    hash = Math.imul(hash, 0x01000193)
  }
  return hash >>> 0
}

/** mulberry32 伪随机数发生器：同一种子跨浏览器输出一致 */
function mulberry32(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state + 0x6d2b79f5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/**
 * 按种子稳定洗牌：先按 sortOrder 归位再 Fisher-Yates，保证同一学生重复调用顺序不变。
 */
export function shuffleExamQuestions<T extends { sortOrder?: number | null }>(
  questions: T[],
  seedKey: string,
): T[] {
  if (questions.length <= 1) return [...questions]
  const ordered = [...questions].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  const random = mulberry32(fnv1a32(seedKey))
  for (let i = ordered.length - 1; i > 0; i -= 1) {
    const j = Math.floor(random() * (i + 1))
    ;[ordered[i], ordered[j]] = [ordered[j], ordered[i]]
  }
  return ordered
}

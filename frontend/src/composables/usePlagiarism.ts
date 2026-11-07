import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { getPlagiarism, getPlagiarismDetail, reviewPlagiarism } from '@/api/teacher';
import type { InjectionKey } from 'vue';

export function usePlagiarism() {
type RiskLevel = 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN';
type ReviewStatus = 'PENDING' | 'COMPLETED' | 'REVIEWED';

interface PlagiarismTaskLike {
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

interface EvidenceBlock {
  title: string;
  content: string;
}

interface SegmentBlock {
  title: string;
  content: string;
  rate?: string;
}

const loading = ref(false);
const listError = ref('');
const rows = ref<PlagiarismTaskLike[]>([]);
const selectedTaskId = ref<number | null>(null);
const detailLoading = ref(false);
const detailError = ref('');
const detail = ref<PlagiarismTaskLike | null>(null);
const reviewComment = ref('');
const reviewLoading = ref(false);
const searchQuery = ref('');
const businessTypeFilter = ref('');
const statusFilter = ref('');
const riskFilter = ref<RiskLevel | ''>('');
let detailRequestSeq = 0;
let disposed = false;

const businessTypeLabel = (type?: string) => {
  const map: Record<string, string> = {
    HOMEWORK: '作业',
    LAB: '实验',
    EXAM: '考试',
  };
  return type ? map[type] || type : '未分类';
};

const statusLabel = (status?: string) => {
  const map: Record<string, string> = {
    PENDING: '待处理',
    COMPLETED: '已完成',
    REVIEWED: '已复核',
  };
  return status ? map[status] || status : '待处理';
};

const statusTone = (status?: string) => {
  const map: Record<string, string> = {
    PENDING: 'warning',
    COMPLETED: 'primary',
    REVIEWED: 'success',
  };
  return status ? map[status] || 'neutral' : 'warning';
};

const riskTone = (level?: string) => {
  const map: Record<string, string> = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'safe',
    UNKNOWN: 'neutral',
  };
  return level ? map[level] || 'neutral' : 'neutral';
};

const riskLevelLabel = (level?: string) => {
  const map: Record<string, string> = {
    HIGH: '高风险',
    MEDIUM: '中风险',
    LOW: '低风险',
    UNKNOWN: '暂无结果',
  };
  return level ? map[level] || level : '暂无结果';
};

const readNumber = (value: unknown) => {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
};

const formatRate = (value: number | null) => {
  if (typeof value !== 'number') return '-';
  return `${value.toFixed(2)}%`;
};

const getAvatarText = (primary?: string, fallback?: string) => {
  const source = (primary || fallback || '查').trim();
  return source.slice(0, 1).toUpperCase();
};

const getStudentName = (task?: PlagiarismTaskLike | null) => task?.studentName || '未命名学生';
const getBusinessType = (task?: PlagiarismTaskLike | null) => task?.businessType || '';
const getBusinessName = (task?: PlagiarismTaskLike | null) => task?.businessName || '未命名任务';

const getSimilarityRate = (task?: PlagiarismTaskLike | null) => {
  if (!task) return null;
  return readNumber(task.similarityRate);
};

const getTopSummary = (task?: PlagiarismTaskLike | null) => {
  const direct = typeof task?.topMatchSummary === 'string' ? task.topMatchSummary.trim() : '';
  if (direct) return direct;
  const parsed = parseRawResult(task?.rawResultJson);
  const fromRaw = typeof parsed?.topMatchSummary === 'string' ? parsed.topMatchSummary.trim() : '';
  if (fromRaw) return fromRaw;
  const candidate = task as unknown;
  const contextSummary =
    candidate && typeof candidate === 'object' && 'summary' in candidate && typeof candidate.summary === 'string'
      ? candidate.summary.trim()
      : '';
  return contextSummary;
};

const topMatchReference = (task?: PlagiarismTaskLike | null) => {
  if (!task) return '-';
  if (typeof task.topMatchReference === 'string' && task.topMatchReference.trim()) {
    return task.topMatchReference.trim();
  }
  const parsed = parseRawResult(task.rawResultJson);
  if (typeof parsed?.topMatchReference === 'string' && parsed.topMatchReference.trim()) {
    return parsed.topMatchReference.trim();
  }
  return '-';
};

function parseRawResult(rawResult: PlagiarismTaskLike['rawResultJson']) {
  if (!rawResult) return null;
  if (typeof rawResult === 'object') return rawResult as Record<string, unknown>;
  if (typeof rawResult === 'string') {
    try {
      return JSON.parse(rawResult) as Record<string, unknown>;
    } catch {
      return null;
    }
  }
  return null;
}

const normalizedStatus = (task?: PlagiarismTaskLike | null): ReviewStatus => {
  const candidate = task?.review?.reviewStatus || task?.review?.status || task?.reviewStatus || task?.status || 'PENDING';
  if (candidate === 'REVIEWED' || candidate === 'COMPLETED') {
    return candidate;
  }
  return 'PENDING';
};

const getReviewComment = (task?: PlagiarismTaskLike | null) => {
  const candidate = task?.review?.reviewComment || task?.review?.comment || task?.reviewComment;
  return typeof candidate === 'string' ? candidate.trim() : '';
};

const getReviewConclusion = (task?: PlagiarismTaskLike | null) => {
  const candidate = task?.review?.reviewConclusion || task?.review?.conclusion || task?.reviewConclusion;
  return typeof candidate === 'string' ? candidate.trim() : '';
};

const normalizedRiskLevel = (task?: PlagiarismTaskLike | null): RiskLevel => {
  const risk = task?.riskLevel?.toUpperCase();
  if (risk === 'HIGH' || risk === 'MEDIUM' || risk === 'LOW') {
    return risk;
  }
  const rate = getSimilarityRate(task);
  if (typeof rate !== 'number') return 'UNKNOWN';
  if (rate >= 40) return 'HIGH';
  if (rate >= 20) return 'MEDIUM';
  return 'LOW';
};

const parsedAlgorithmResult = computed(() => parseRawResult(detail.value?.rawResultJson));

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase();
  return rows.value.filter(row => {
    const matchesKeyword =
      !keyword ||
      getStudentName(row).toLowerCase().includes(keyword) ||
      getBusinessName(row).toLowerCase().includes(keyword) ||
      getTopSummary(row).toLowerCase().includes(keyword) ||
      topMatchReference(row).toLowerCase().includes(keyword);

    const matchesBusiness = !businessTypeFilter.value || getBusinessType(row) === businessTypeFilter.value;
    const matchesStatus = !statusFilter.value || normalizedStatus(row) === statusFilter.value;
    const matchesRisk = !riskFilter.value || normalizedRiskLevel(row) === riskFilter.value;
    return matchesKeyword && matchesBusiness && matchesStatus && matchesRisk;
  });
});

const activeTask = computed(() => filteredRows.value.find(item => item.id === selectedTaskId.value) || filteredRows.value[0] || null);
const nextTask = computed(() => {
  const index = filteredRows.value.findIndex(row => row.id === activeTask.value?.id);
  if (index < 0) return filteredRows.value[0] || null;
  return filteredRows.value[index + 1] || null;
});
const pendingCount = computed(() => rows.value.filter(row => normalizedStatus(row) === 'PENDING').length);
const reviewedCount = computed(() => rows.value.filter(row => normalizedStatus(row) === 'REVIEWED').length);
const highRiskCount = computed(() => rows.value.filter(row => normalizedRiskLevel(row) === 'HIGH').length);

const averageSimilarityText = computed(() => {
  const values = filteredRows.value.map(row => getSimilarityRate(row)).filter((item): item is number => typeof item === 'number');
  if (!values.length) return '-';
  const average = values.reduce((sum, item) => sum + item, 0) / values.length;
  return `${average.toFixed(2)}%`;
});

const highlightedTaskTitle = computed(() => {
  if (!activeTask.value) return '暂无可用查重任务';
  return `${getStudentName(activeTask.value)} · ${getBusinessName(activeTask.value)}`;
});

const highlightedTaskMeta = computed(() => {
  if (!activeTask.value) {
    return listError.value || '等待接口返回查重任务后进入治理工作流';
  }
  return `${businessTypeLabel(getBusinessType(activeTask.value))} · ${formatRate(getSimilarityRate(activeTask.value))} · ${riskLevelLabel(normalizedRiskLevel(activeTask.value))}`;
});

const toolbarMetaText = computed(() => {
  if (riskFilter.value) {
    return `已锁定 ${riskLevelLabel(riskFilter.value)} 范围`;
  }
  if (statusFilter.value) {
    return `已锁定 ${statusLabel(statusFilter.value)} 任务`;
  }
  return '未锁定风险或状态，可浏览完整治理队列';
});

const activeTaskSubline = computed(() => {
  if (!activeTask.value) return '请选择左侧任务进入详情区。';
  return `${businessTypeLabel(getBusinessType(activeTask.value))} · ${getBusinessName(activeTask.value)} · ${formatRate(getSimilarityRate(activeTask.value))}`;
});

const activeTaskOrderLabel = computed(() => {
  const index = filteredRows.value.findIndex(row => row.id === activeTask.value?.id);
  if (index < 0) return '未选中任务';
  return `任务 ${index + 1} / ${filteredRows.value.length}`;
});

const reviewHintText = computed(() => {
  if (!activeTask.value) return '请选择一个查重任务后再提交复核。';
  if (normalizedRiskLevel(activeTask.value) === 'HIGH') {
    return '当前任务处于高风险范围，建议优先补充判定依据与后续处理意见。';
  }
  return '复核意见会直接通过 reviewPlagiarism 提交，并触发列表与详情刷新。';
});

const reviewContextBlocks = computed<EvidenceBlock[]>(() => {
  if (!detail.value) return [];
  const taskRecord = detail.value as Record<string, unknown>;
  const parsed = parsedAlgorithmResult.value as Record<string, unknown> | null;
  const candidates: Array<[string, unknown]> = [
    ['教师复核意见', getReviewComment(detail.value)],
    ['复核结论', getReviewConclusion(detail.value)],
    ['风险说明', taskRecord.riskSummary],
    ['疑似来源说明', taskRecord.referenceSummary],
    ['上下文说明', taskRecord.contextSummary],
    ['算法摘要', parsed?.topMatchSummary],
  ];

  return candidates
    .map(([title, value]) => ({ title, content: typeof value === 'string' ? value.trim() : '' }))
    .filter(item => item.content);
});

const matchedSegments = computed<SegmentBlock[]>(() => {
  const blocks: SegmentBlock[] = [];
  const raw = parsedAlgorithmResult.value as Record<string, unknown> | null;
  const sources = [
    (detail.value as Record<string, unknown> | null)?.matchedSegments,
    (detail.value as Record<string, unknown> | null)?.matches,
    raw?.matchedSegments,
    raw?.matches,
  ];

  sources.forEach(source => {
    if (!Array.isArray(source)) return;
    source.forEach((item, index) => {
      if (!item || typeof item !== 'object') return;
      const record = item as Record<string, unknown>;
      const title = [record.reference, record.source, record.title].find(value => typeof value === 'string' && value.trim()) as string | undefined;
      const content = [record.summary, record.snippet, record.content, record.matchText].find(value => typeof value === 'string' && value.trim()) as string | undefined;
      if (!content) return;
      const rate = formatRate(readNumber(record.similarityRate ?? record.rate ?? record.score));
      blocks.push({
        title: title || `命中片段 ${index + 1}`,
        content,
        rate: rate === '-' ? undefined : rate,
      });
    });
  });

  return blocks.slice(0, 6);
});

const syncReviewComment = (task: PlagiarismTaskLike | null) => {
  reviewComment.value = getReviewComment(task) || getReviewComment(detail.value) || '';
};

const loadDetail = async (id: number) => {
  const requestId = ++detailRequestSeq;
  detailLoading.value = true;
  detailError.value = '';
  detail.value = null;
  try {
    const data = await getPlagiarismDetail(id);
    if (disposed || requestId !== detailRequestSeq) return;
    detail.value = data;
    syncReviewComment(data);
  } catch (error) {
    if (disposed || requestId !== detailRequestSeq) return;
    detailError.value = error instanceof Error ? error.message : '无法获取查重详情，请稍后重试。';
  } finally {
    if (requestId === detailRequestSeq) {
      detailLoading.value = false;
    }
  }
};

const fetchData = async () => {
  loading.value = true;
  listError.value = '';
  try {
    rows.value = await getPlagiarism();
    if (!rows.value.some(item => item.id === selectedTaskId.value)) {
      selectedTaskId.value = rows.value[0]?.id ?? null;
    }
  } catch (error) {
    listError.value = error instanceof Error ? error.message : '无法获取查重任务列表，请稍后重试。';
    rows.value = [];
    selectedTaskId.value = null;
  } finally {
    loading.value = false;
  }
};

const selectTask = async (row: PlagiarismTaskLike) => {
  selectedTaskId.value = row.id;
  syncReviewComment(row);
  await loadDetail(row.id);
};

const focusPendingTask = async () => {
  const target = filteredRows.value.find(row => normalizedStatus(row) === 'PENDING') || rows.value.find(row => normalizedStatus(row) === 'PENDING');
  if (target) {
    await selectTask(target);
  }
};

const focusNextTask = async () => {
  if (nextTask.value) {
    await selectTask(nextTask.value);
  }
};

const resetFilters = () => {
  searchQuery.value = '';
  businessTypeFilter.value = '';
  statusFilter.value = '';
  riskFilter.value = '';
};

const appendQuickComment = (text: string) => {
  reviewComment.value = reviewComment.value ? `${reviewComment.value}\n${text}` : text;
};

const submitReview = async () => {
  if (!activeTask.value) return;
  const submittedTaskId = activeTask.value.id;
  reviewLoading.value = true;
  try {
    await reviewPlagiarism(submittedTaskId, { reviewComment: reviewComment.value });
    ElMessage.success('复核完成');
    await fetchData();
    await loadDetail(submittedTaskId);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复核失败，请稍后重试。');
  } finally {
    reviewLoading.value = false;
  }
};

watch(
  activeTask,
  async current => {
    if (!current) {
      detail.value = null;
      detailError.value = '';
      reviewComment.value = '';
      return;
    }

    if (detail.value?.id === current.id) {
      syncReviewComment(current);
      return;
    }

    syncReviewComment(current);
    await loadDetail(current.id);
  },
  { immediate: false }
);

onMounted(fetchData);

onUnmounted(() => {
  disposed = true;
  detailRequestSeq += 1;
});

  return { computed, onMounted, ref, watch, ElMessage, getPlagiarism, getPlagiarismDetail, reviewPlagiarism, loading, listError, rows, selectedTaskId, detailLoading, detailError, detail, reviewComment, reviewLoading, searchQuery, businessTypeFilter, statusFilter, riskFilter, businessTypeLabel, statusLabel, statusTone, riskTone, riskLevelLabel, readNumber, formatRate, getAvatarText, getStudentName, getBusinessType, getBusinessName, getSimilarityRate, getTopSummary, topMatchReference, parseRawResult, normalizedStatus, getReviewComment, getReviewConclusion, normalizedRiskLevel, parsedAlgorithmResult, filteredRows, activeTask, nextTask, pendingCount, reviewedCount, highRiskCount, averageSimilarityText, highlightedTaskTitle, highlightedTaskMeta, toolbarMetaText, activeTaskSubline, activeTaskOrderLabel, reviewHintText, reviewContextBlocks, matchedSegments, syncReviewComment, loadDetail, fetchData, selectTask, focusPendingTask, focusNextTask, resetFilters, appendQuickComment, submitReview };
}

export type PlagiarismState = ReturnType<typeof usePlagiarism>;
export const PlagiarismKey: InjectionKey<PlagiarismState> = Symbol('PlagiarismState');

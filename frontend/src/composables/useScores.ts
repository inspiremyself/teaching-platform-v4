import { computed, onMounted, ref } from 'vue';
import { ChatLineRound, Collection, DataAnalysis, Document, Finished, Medal, Reading } from '@element-plus/icons-vue';
import { getScores } from '@/api/student';
import type { AnalysisInsight, AnalysisSummaryCard, StudentGroupedScore, StudentScoreOverview, StudentScoreRecord } from '@/types/analysis';
import type { InjectionKey } from 'vue';

export function useScores() {
type ScoreGroupSection = StudentGroupedScore & {
  icon: typeof Collection;
  performanceLabel: string;
  records: StudentScoreRecord[];
};

const loading = ref(false);
const loaded = ref(false);
const loadError = ref('');
const scoreOverview = ref<StudentScoreOverview | null>(null);
const metricIcons = [Finished, DataAnalysis, Collection, Reading];
const typeOrder: StudentScoreRecord['businessType'][] = ['LAB', 'HOMEWORK', 'EXAM'];

const summaryCards = computed<AnalysisSummaryCard[]>(() => scoreOverview.value?.summaryCards ?? []);
const groupedScores = computed<StudentGroupedScore[]>(() => scoreOverview.value?.groupedScores ?? []);
const recentRecords = computed<StudentScoreRecord[]>(() => scoreOverview.value?.recentRecords ?? []);
const feedbackNotes = computed<AnalysisInsight[]>(() => scoreOverview.value?.feedbackNotes ?? []);

const numberValue = (value: string | number | undefined | null) => {
  if (typeof value === 'number') {
    return value;
  }
  if (typeof value !== 'string') {
    return 0;
  }
  const parsed = Number(value.replace(/[^\d.-]/g, ''));
  return Number.isFinite(parsed) ? parsed : 0;
};

const primarySummaryCard = computed(() => {
  const preferred = summaryCards.value.find(card => /(overall|average|avg|score|grade)/i.test(card.key));
  return preferred ?? summaryCards.value[0] ?? null;
});

const secondarySummaryCards = computed(() => {
  const primaryKey = primarySummaryCard.value?.key;
  const filtered = summaryCards.value.filter(card => card.key !== primaryKey);
  return filtered.slice(0, 3);
});

const completionCard = computed(() => summaryCards.value.find(card => card.key === 'completionRate') ?? null);
const completedCard = computed(() => summaryCards.value.find(card => card.key === 'completedItems') ?? null);
const completionRateNumeric = computed(() => {
  const value = Number(scoreOverview.value?.totalItems ? Math.round(((scoreOverview.value.completedItems ?? 0) / scoreOverview.value.totalItems) * 100) : numberValue(completionCard.value?.value));
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.max(0, Math.min(100, value));
});
const completionRateDisplay = computed(() => `${completionRateNumeric.value}%`);

const completedItemsDisplay = computed(() => {
  const completed = Number(scoreOverview.value?.completedItems ?? completedCard.value?.value ?? 0);
  const total = Number(scoreOverview.value?.totalItems ?? 0);
  return total > 0 ? `${completed} / ${total}` : `${completed}`;
});

const progressDetail = computed(() => {
  const completed = Number(scoreOverview.value?.completedItems ?? completedCard.value?.value ?? 0);
  const explicitTotal = Number(scoreOverview.value?.totalItems ?? 0);
  if (!explicitTotal) {
    return completed > 0 ? `当前已累计 ${completed} 项已评分任务，后续会随着更多成绩回写继续更新总览。` : '当前暂无可统计的任务总量。';
  }
  return `当前共有 ${explicitTotal} 项任务纳入统计，已回写 ${completed} 项成绩；完成率越高，整体判断越接近真实学期表现。`;
});

const strongestGroup = computed(() => {
  if (!groupedScores.value.length) {
    return null;
  }
  return [...groupedScores.value].sort((left, right) => right.avgScore - left.avgScore)[0];
});

const weakestGroup = computed(() => {
  if (!groupedScores.value.length) {
    return null;
  }
  return [...groupedScores.value].sort((left, right) => left.avgScore - right.avgScore)[0];
});

const scoreLevelLabel = computed(() => {
  const score = numberValue(primarySummaryCard.value?.value);
  if (score >= 90) return '优秀';
  if (score >= 80) return '良好';
  if (score >= 70) return '稳步提升';
  if (score >= 60) return '达到及格线';
  if (score > 0) return '需重点补强';
  return '等待成绩回写';
});

const overallInterpretation = computed(() => {
  if (strongestGroup.value && weakestGroup.value && strongestGroup.value.businessType !== weakestGroup.value.businessType) {
    return `${strongestGroup.value.label}目前表现最好，${weakestGroup.value.label}更值得优先复盘。`;
  }
  if (strongestGroup.value) {
    return `${strongestGroup.value.label}已形成当前最稳定的得分表现。`;
  }
  return '当前成绩总览会随着新的评分记录持续更新。';
});

const recentHighlights = computed(() => {
  return [...recentRecords.value]
    .sort((left, right) => {
      const leftTime = left.gradedAt ? new Date(left.gradedAt).getTime() : 0;
      const rightTime = right.gradedAt ? new Date(right.gradedAt).getTime() : 0;
      return rightTime - leftTime;
    })
    .slice(0, 6);
});

const performanceLabel = (avgScore: number) => {
  if (avgScore >= 90) return '整体表现优秀';
  if (avgScore >= 80) return '整体表现良好';
  if (avgScore >= 70) return '基础表现稳定';
  if (avgScore > 0) return '建议重点复盘';
  return '等待评分数据';
};

const sectionIcon = (type: StudentScoreRecord['businessType']) => {
  if (type === 'LAB') return Collection;
  if (type === 'HOMEWORK') return Document;
  return Reading;
};

const groupSections = computed<ScoreGroupSection[]>(() => {
  const groupedMap = new Map(groupedScores.value.map(item => [item.businessType, item]));
  return typeOrder
    .map(type => groupedMap.get(type))
    .filter((item): item is StudentGroupedScore => Boolean(item))
    .map(item => ({
      ...item,
      icon: sectionIcon(item.businessType),
      performanceLabel: performanceLabel(item.avgScore),
      records: recentHighlights.value.filter(record => record.businessType === item.businessType).slice(0, item.businessType === 'EXAM' ? 2 : 4),
    }));
});

const actionableInsights = computed<AnalysisInsight[]>(() => {
  const insights: AnalysisInsight[] = [];

  if (weakestGroup.value) {
    insights.push({
      tone: 'warning',
      title: `优先关注 ${weakestGroup.value.label}`,
      description: `${weakestGroup.value.label} 当前均分为 ${weakestGroup.value.avgScore.toFixed(1)}，是三个成绩板块中最弱的一项，建议先按最近评分记录回看失分点。`,
    });
  }

  insights.push({
    tone: completionRateNumeric.value >= 80 ? 'positive' : 'highlight',
    title: completionRateNumeric.value >= 80 ? '成绩覆盖度较完整' : '继续补齐未评分任务',
    description:
      completionRateNumeric.value >= 80
        ? `当前完成率为 ${completionRateDisplay.value}，总览已基本能反映当前学习状态，可以重点转向分组差异和教师建议。`
        : `当前完成率为 ${completionRateDisplay.value}，仍有部分任务未回写成绩；在看总评时要同时结合未完成/未评分任务的后续影响。`,
  });

  if (feedbackNotes.value[0]) {
    insights.push({
      tone: feedbackNotes.value[0].tone,
      title: `结合教师反馈：${feedbackNotes.value[0].title}`,
      description: feedbackNotes.value[0].description,
    });
  } else if (strongestGroup.value) {
    insights.push({
      tone: 'positive',
      title: `保持 ${strongestGroup.value.label} 的优势节奏`,
      description: `${strongestGroup.value.label} 当前均分达到 ${strongestGroup.value.avgScore.toFixed(1)}，可继续沿用这部分的复习与提交节奏，并把方法迁移到其他板块。`,
    });
  }

  return insights.slice(0, 3);
});

const loadScores = async () => {
  loading.value = true;
  loadError.value = '';
  try {
    scoreOverview.value = await getScores();
    loaded.value = true;
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '成绩总览加载失败';
  } finally {
    loading.value = false;
  }
};

const secondaryMetricIcon = (index: number) => metricIcons[index % metricIcons.length];

const typeLabel = (type: StudentScoreRecord['businessType']) => {
  if (type === 'LAB') return '实验';
  if (type === 'HOMEWORK') return '作业';
  return '考试';
};

const formatScore = (score: number) => `${score.toFixed(1).replace(/\.0$/, '')} 分`;

const formatDate = (value: string | null) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
};

const scrollToFeedback = () => {
  document.getElementById('feedback-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
};

onMounted(loadScores);

  return { computed, onMounted, ref, ChatLineRound, Collection, DataAnalysis, Document, Finished, Medal, Reading, getScores, loading, loaded, loadError, scoreOverview, metricIcons, typeOrder, summaryCards, groupedScores, recentRecords, feedbackNotes, numberValue, primarySummaryCard, secondarySummaryCards, completionCard, completedCard, completionRateNumeric, completionRateDisplay, completedItemsDisplay, progressDetail, strongestGroup, weakestGroup, scoreLevelLabel, overallInterpretation, recentHighlights, performanceLabel, sectionIcon, groupSections, actionableInsights, loadScores, secondaryMetricIcon, typeLabel, formatScore, formatDate, scrollToFeedback };
}

export type ScoresState = ReturnType<typeof useScores>;
export const ScoresKey: InjectionKey<ScoresState> = Symbol('ScoresState');

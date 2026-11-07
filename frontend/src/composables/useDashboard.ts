import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { DataLine, List, Opportunity, Warning } from '@element-plus/icons-vue';
import { getTeacherDashboard } from '@/api/teacher';
import type { InjectionKey } from 'vue';

export function useDashboard() {
type SummaryCard = {
  key: string;
  title: string;
  value: number | string;
  description: string;
};

type TrendItem = {
  date: string;
  scoreCount: number;
  avgScore: number;
};

type RecentTask = {
  label: string;
  title: string;
  actor: string;
  route: string;
};

type QuickLink = {
  label: string;
  route: string;
  badge: number;
};

const router = useRouter();
const loading = ref(false);
const loaded = ref(false);
const loadError = ref('');
const summaryCards = ref<SummaryCard[]>([]);
const trend = ref<TrendItem[]>([]);
const recentTasks = ref<RecentTask[]>([]);
const quickLinks = ref<QuickLink[]>([]);
const summaryIcons = [Opportunity, Warning, DataLine, List];
const summaryCardPills = ['总览', '提醒', '进度', '入口'];

const textLooksChinese = (text: string) => /[\u4e00-\u9fff]/.test(text);

const normalizeEnglishText = (text: string) => text.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();

const translateDashboardText = (text: string) => {
  if (!text) return '';
  if (textLooksChinese(text)) return text;

  const normalized = normalizeEnglishText(text);
  const dictionary: Array<[RegExp, string]> = [
    [/dashboard overview|instructor dashboard|teacher workbench|teacher dashboard|dashboard/i, '教师工作台'],
    [/total students|active students|student count|students/i, '学生总数'],
    [/avg grade|average grade|avg score|average score|class average/i, '平均成绩'],
    [/pending submissions|pending grading|to grade|submissions/i, '待批改提交'],
    [/plagiarism alerts|risk alert|alerts|high risk/i, '风险提醒'],
    [/recent submission trend|student performance trends|engagement trend|trend/i, '近期趋势'],
    [/my tasks|tasks reminders|tasks alerts|tasks/i, '待办与提醒'],
    [/recent submissions/i, '近期提交'],
    [/view all/i, '查看全部'],
    [/action required/i, '需要处理'],
    [/stable/i, '整体稳定'],
    [/priority/i, '优先处理'],
    [/office hours/i, '答疑安排'],
    [/grade/i, '评分'],
    [/course|class/i, '课程/班级'],
    [/student/i, '学生'],
    [/assignment|homework/i, '作业'],
    [/lab/i, '实验'],
    [/exam/i, '考试'],
    [/report/i, '报告'],
    [/material/i, '资料'],
    [/resource/i, '资源'],
    [/settings/i, '设置']
  ];

  for (const [pattern, replacement] of dictionary) {
    if (pattern.test(normalized)) {
      return replacement;
    }
  }

  return text;
};

const localizedSummaryCards = computed(() => summaryCards.value.map(card => ({
  ...card,
  title: translateDashboardText(card.title),
  description: translateDashboardText(card.description)
})));

const maxTrendCount = computed(() => Math.max(...trend.value.map(item => item.scoreCount), 1));
const trendTotalCount = computed(() => trend.value.reduce((sum, item) => sum + item.scoreCount, 0));
const trendAverageValue = computed(() => {
  if (!trend.value.length) {
    return 0;
  }
  return trendTotalCount.value / trend.value.length;
});
const trendAverageText = computed(() => trend.value.length ? trendAverageValue.value.toFixed(1) : '0');
const trendChartPoints = computed(() => {
  const items = trend.value;
  if (!items.length) {
    return [];
  }

  return items.map((item, index) => {
    const avgScore = Math.min(Math.max(item.avgScore, 0), 100);
    const x = items.length === 1 ? 50 : 4 + (index / (items.length - 1)) * 92;
    const y = 94 - (avgScore / 100) * 88;

    return {
      ...item,
      avgScore,
      x,
      y
    };
  });
});
const trendLinePath = computed(() => trendChartPoints.value.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`).join(' '));
const trendAxisTicks = computed(() => {
  const maxValue = maxTrendCount.value;
  return [maxValue, Math.ceil(maxValue * 0.66), Math.ceil(maxValue * 0.33), 0];
});
const trendPulseShortText = computed(() => {
  if (trendPulseTitle.value === '存在明显评分高峰') return '有高峰';
  if (trendPulseTitle.value === '评分节奏总体平稳') return '平稳';
  if (trendPulseTitle.value === '近期评分活跃度偏低') return '偏低';
  return '暂无';
});
const peakTrendItem = computed(() => {
  if (!trend.value.length) {
    return null;
  }
  return trend.value.reduce((peak, item) => item.scoreCount > peak.scoreCount ? item : peak, trend.value[0]);
});
const peakTrendValue = computed(() => peakTrendItem.value?.scoreCount ?? 0);
const peakTrendDate = computed(() => peakTrendItem.value ? peakTrendItem.value.date.slice(5) : '暂无');
const peakTrendHint = computed(() => {
  if (!peakTrendItem.value) {
    return '当前还没有形成可对比的评分峰值。';
  }
  return `${peakTrendItem.value.date.slice(5)} 共完成 ${peakTrendItem.value.scoreCount} 条评分记录，均分 ${peakTrendItem.value.avgScore.toFixed(1)}。`;
});
const urgentTaskCount = computed(() => recentTasks.value.filter(task => /待|催|预警|逾期|异常|未/.test(`${task.label}${task.title}`)).length);
const distinctActorsCount = computed(() => new Set(recentTasks.value.map(task => task.actor).filter(Boolean)).size);
const riskAlertCount = computed(() => recentTasks.value.filter(task => /待|催|预警|逾期|异常|未|风险/.test(`${task.label}${task.title}`)).length);
const taskPressureLevel = computed(() => {
  if (!recentTasks.value.length) return '低负载';
  if (recentTasks.value.length >= 5 || urgentTaskCount.value >= 3) return '高负载';
  if (recentTasks.value.length >= 3 || urgentTaskCount.value >= 1) return '需跟进';
  return '稳态巡视';
});
const taskPressureText = computed(() => {
  if (!recentTasks.value.length) {
    return '当前任务列表为空，可优先巡视班级、作业和实验数据。';
  }
  if (taskPressureLevel.value === '高负载') {
    return '待办数量较高，建议先处理红色风险或临近截止事项。';
  }
  if (taskPressureLevel.value === '需跟进') {
    return '已有待办堆积，建议优先进入提交队列或相关管理页清理。';
  }
  return '当前待办压力可控，适合按既定节奏处理。';
});
const workbenchHeadline = computed(() => {
  if (urgentTaskCount.value) {
    return `当前有 ${urgentTaskCount.value} 项优先任务需要尽快处理`;
  }
  if (recentTasks.value.length) {
    return `当前共 ${recentTasks.value.length} 项待办，工作台已为你集中收口`;
  }
  if (trendTotalCount.value) {
    return `近 7 日已完成 ${trendTotalCount.value} 条评分记录，工作节奏整体稳定`;
  }
  return '当前暂无集中待办，可从快捷入口继续巡视班级与教学活动';
});
const workbenchSubline = computed(() => {
  if (recentTasks.value.length) {
    return `涉及 ${distinctActorsCount.value || 1} 个对象与 ${quickLinks.value.length} 个高频入口，适合从首页直接切入处理。`;
  }
  if (quickLinks.value.length) {
    return `已收口 ${quickLinks.value.length} 个常用管理入口，可继续进入班级、作业、实验或考试相关页面。`;
  }
  return '当前教师工作台已加载完成，但暂未返回更多任务或聚合摘要。';
});
const heroStatusText = computed(() => {
  if (loading.value) return '工作台同步中';
  if (loadError.value) return '数据加载异常';
  if (!loaded.value) return '等待数据回流';
  if (riskAlertCount.value) return `当前风险 ${riskAlertCount.value} 项`;
  return '当前状态稳定';
});
const trendInsightText = computed(() => {
  if (!trend.value.length) {
    return '暂无近 7 日评分趋势。';
  }
  return `最高峰出现在 ${peakTrendDate.value}，单日 ${peakTrendValue.value} 条。`;
});
const trendPulseTitle = computed(() => {
  if (!trend.value.length) return '暂无评分波动';
  if (peakTrendValue.value >= trendAverageValue.value * 1.5 && peakTrendValue.value >= 3) return '存在明显评分高峰';
  if (trendAverageValue.value >= 1) return '评分节奏总体平稳';
  return '近期评分活跃度偏低';
});
const trendPulseDescription = computed(() => {
  if (!trend.value.length) {
    return '等待真实趋势数据回流后再形成节奏判断。';
  }
  if (trendPulseTitle.value === '存在明显评分高峰') {
    return '建议结合待办列表复核高峰日前后的批改集中区段，避免遗漏异常提交。';
  }
  if (trendPulseTitle.value === '评分节奏总体平稳') {
    return '近一周评分记录分布较均衡，可继续按照日常批改节奏推进。';
  }
  return '近期评分记录较少，建议结合作业、实验和考试入口检查是否存在未进入队列的数据。';
});
const trendFocusTitle = computed(() => {
  if (urgentTaskCount.value) return '先清理优先任务';
  if (peakTrendItem.value) return `${peakTrendDate.value} 评分峰值需复盘`;
  return '优先巡视高频入口';
});
const trendFocusDescription = computed(() => {
  if (urgentTaskCount.value) {
    return '当前首页已出现明确待办压力，建议先从待办与提醒面板进入对应页面处理。';
  }
  if (peakTrendItem.value) {
    return `峰值日均分 ${peakTrendItem.value.avgScore.toFixed(1)}，可进一步结合业务页核对评分结果和提交质量。`;
  }
  return '如果暂无显著峰值或任务，可从快捷入口继续巡视班级、题库和提交列表。';
});
const quickLinkSummary = computed(() => {
  if (!quickLinks.value.length) return '暂无入口摘要';
  const totalBadge = quickLinks.value.reduce((sum, item) => sum + item.badge, 0);
  return `共 ${quickLinks.value.length} 个入口，累计 ${totalBadge} 项待处理`; 
});
const quickLinkLoadText = computed(() => {
  if (!quickLinks.value.length) {
    return '当前没有返回快捷入口数据。';
  }
  const busiest = quickLinks.value.reduce((target, item) => item.badge > target.badge ? item : target, quickLinks.value[0]);
  return `${busiest.label} 当前待处理 ${busiest.badge} 项，适合作为下一步进入点。`;
});
const riskAlertSummary = computed(() => {
  if (!recentTasks.value.length) return '当前无待处理风险';
  if (!riskAlertCount.value) return '待办存在但风险可控';
  return `当前识别出 ${riskAlertCount.value} 项重点提醒`;
});
const riskAlertDetail = computed(() => {
  if (!recentTasks.value.length) {
    return '当前首页未返回待办任务，可继续按模块巡视整体状态。';
  }
  if (!riskAlertCount.value) {
    return '虽然已有待办，但暂未出现明显逾期、催办或异常类风险。';
  }
  return '建议优先处理带有待办、预警、逾期或异常语义的事项，减少后续积压。';
});
const actorCoverageText = computed(() => {
  if (!recentTasks.value.length) {
    return '暂无任务对象需要跟进。';
  }
  if (distinctActorsCount.value <= 1) {
    return '任务集中在少量对象上，适合快速清理。';
  }
  if (distinctActorsCount.value >= 4) {
    return '任务分散在多个对象上，建议按任务标签先做优先级分层。';
  }
  return '任务覆盖多个对象，适合先按业务模块进入处理。';
});
const recommendedActionTitle = computed(() => {
  if (quickLinks.value.length) {
    const busiest = quickLinks.value.reduce((target, item) => item.badge > target.badge ? item : target, quickLinks.value[0]);
    return `优先进入“${busiest.label}”`;
  }
  if (recentTasks.value.length) {
    return '优先处理首页待办';
  }
  return '继续巡视教学模块';
});
const recommendedActionDescription = computed(() => {
  if (quickLinks.value.length) {
    const busiest = quickLinks.value.reduce((target, item) => item.badge > target.badge ? item : target, quickLinks.value[0]);
    return `${busiest.label} 的待处理量当前最高，可先从该入口切入，缩短后续回访成本。`;
  }
  if (recentTasks.value.length) {
    return '虽然缺少入口负载摘要，但首页待办已经给出明确处理方向。';
  }
  return '当前没有明显积压项，可按班级、作业、实验或分析页顺序继续巡视。';
});
const riskActionTitle = computed(() => {
  if (!riskAlertCount.value) return '维持常规巡视';
  if (riskAlertCount.value >= 3) return '先清理高风险事项';
  return '优先处理提醒项';
});
const riskActionDescription = computed(() => {
  if (!riskAlertCount.value) {
    return '当前没有明显风险告警，可按既定节奏推进班级、作业和实验管理。';
  }
  if (riskAlertCount.value >= 3) {
    return '风险提醒已形成堆积，建议优先进入待办列表逐项消化，再回到常规巡视。';
  }
  return '当前风险数量有限，适合结合首页待办快速清理，避免升级为更高优先级问题。';
});

const localizedSummarySignal = (key: string) => {
  if (/student|class|member/i.test(key)) return '在册概览';
  if (/grade|score|trend/i.test(key)) return '教学节奏';
  if (/submission|task|todo/i.test(key)) return '等待处理';
  if (/alert|risk|warn/i.test(key)) return '风险关注';
  return '工作摘要';
};

const loadDashboard = async () => {
  loading.value = true;
  loadError.value = '';
  try {
    const data = await getTeacherDashboard();
    summaryCards.value = data.summaryCards ?? [];
    trend.value = data.trend ?? [];
    recentTasks.value = data.recentTasks ?? [];
    quickLinks.value = data.quickLinks ?? [];
    loaded.value = true;
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '教师工作台加载失败';
  } finally {
    loading.value = false;
  }
};

const padIndex = (value: number) => String(value).padStart(2, '0');

const quickLinkDescription = (link: QuickLink) => {
  if (link.badge > 0) {
    return `当前待处理 ${link.badge} 项，可直接进入对应管理页继续处理。`;
  }
  return '当前没有积压项，可进入该模块继续巡视整体状态。';
};

const localizedQuickLinkLabel = (label: string) => translateDashboardText(label);
const localizedTaskLabel = (label: string) => translateDashboardText(label);
const localizedTaskTitle = (title: string) => translateDashboardText(title);
const localizedActorText = (actor: string) => translateDashboardText(actor);

onMounted(loadDashboard);

  return { computed, onMounted, ref, useRouter, DataLine, List, Opportunity, Warning, getTeacherDashboard, router, loading, loaded, loadError, summaryCards, trend, recentTasks, quickLinks, summaryIcons, summaryCardPills, textLooksChinese, normalizeEnglishText, translateDashboardText, localizedSummaryCards, maxTrendCount, trendTotalCount, trendAverageValue, trendAverageText, trendChartPoints, trendLinePath, trendAxisTicks, trendPulseShortText, peakTrendItem, peakTrendValue, peakTrendDate, peakTrendHint, urgentTaskCount, distinctActorsCount, riskAlertCount, taskPressureLevel, taskPressureText, workbenchHeadline, workbenchSubline, heroStatusText, trendInsightText, trendPulseTitle, trendPulseDescription, trendFocusTitle, trendFocusDescription, quickLinkSummary, quickLinkLoadText, riskAlertSummary, riskAlertDetail, actorCoverageText, recommendedActionTitle, recommendedActionDescription, riskActionTitle, riskActionDescription, localizedSummarySignal, loadDashboard, padIndex, quickLinkDescription, localizedQuickLinkLabel, localizedTaskLabel, localizedTaskTitle, localizedActorText };
}

export type DashboardState = ReturnType<typeof useDashboard>;
export const DashboardKey: InjectionKey<DashboardState> = Symbol('DashboardState');

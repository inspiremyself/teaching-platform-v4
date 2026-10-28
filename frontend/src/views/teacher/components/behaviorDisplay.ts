/**
 * 实验作答行为展示共享助手（技术方案 §6.2 / D14 / D15）。
 *
 * 队列页六列、摘要卡、提交总览三列、时间线抽屉共用同一套格式化与文案映射，
 * 防止多页面口径漂移。
 */
import type { LabBehaviorTimelineItem, LabReportBehavior } from '@/types/lab';

/** 时长格式化：h:mm:ss；null 显示 `-`（D14） */
export const formatBehaviorDuration = (ms?: number | null): string => {
  if (ms === null || ms === undefined) {
    return '-';
  }
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
};

/** 作答时长展示：SAVED 行（durationOpen）加 `*` 后缀，表示"进行中，截至最近事件" */
export const formatTotalDuration = (behavior: LabReportBehavior): string => {
  const base = formatBehaviorDuration(behavior.totalDurationMs);
  return behavior.durationOpen && base !== '-' ? `${base}*` : base;
};

/** 活跃时长展示：`{active} · {sessionCount}次`（D17①）；无心跳数据显示 `-` */
export const formatActiveDuration = (behavior: LabReportBehavior): string => {
  const active = formatBehaviorDuration(behavior.activeDurationMs);
  if (active === '-') {
    return '-';
  }
  return `${active} · ${behavior.sessionCount}次`;
};

/** riskFlags 中文映射（§6.2 写死） */
export const RISK_FLAG_LABELS: Record<string, string> = {
  DURATION_TOO_SHORT: '作答时长过短',
  PASTE_HEAVY: '大量粘贴内容',
  FOCUS_LOSS: '频繁离开页面',
  NO_BEHAVIOR_DATA: '无行为数据（历史提交）',
};

/** 无行为数据判定：统一按 riskFlags 含 NO_BEHAVIOR_DATA，不依赖 behavior === null（§5.2） */
export const hasNoBehaviorData = (behavior?: LabReportBehavior | null): boolean =>
  !behavior || behavior.riskFlags.includes('NO_BEHAVIOR_DATA');

/** 风险列 tag 类型：HIGH=danger / MEDIUM=warning / LOW=info / 无数据=info 灰显（D14） */
export const riskTagType = (behavior?: LabReportBehavior | null): 'danger' | 'warning' | 'info' => {
  if (!behavior || hasNoBehaviorData(behavior)) {
    return 'info';
  }
  if (behavior.riskLevel === 'HIGH') {
    return 'danger';
  }
  if (behavior.riskLevel === 'MEDIUM') {
    return 'warning';
  }
  return 'info';
};

export const riskTagLabel = (behavior?: LabReportBehavior | null): string => {
  if (!behavior || hasNoBehaviorData(behavior)) {
    return '无数据';
  }
  if (behavior.riskLevel === 'HIGH') {
    return '高风险';
  }
  if (behavior.riskLevel === 'MEDIUM') {
    return '中风险';
  }
  return '低风险';
};

/** 风险 tag tooltip：riskFlags 中文逐条展示 */
export const riskTagTooltip = (behavior?: LabReportBehavior | null): string => {
  if (!behavior || !behavior.riskFlags.length) {
    return '无风险标记';
  }
  return behavior.riskFlags.map((flag) => RISK_FLAG_LABELS[flag] ?? flag).join('；');
};

const LEAVE_REASON_LABELS: Record<string, string> = {
  route: '路由离开',
  unload: '关闭浏览器',
  submitted: '提交成功',
};

const SUBMIT_BLOCKED_REASON_LABELS: Record<string, string> = {
  'no-saved-answer': '无已保存答案',
  'summary-required': '实验小结必填',
  'autosave-failed': '自动保存失败',
};

const formatPayloadNumber = (value: unknown): number | null =>
  typeof value === 'number' && Number.isFinite(value) ? value : null;

/** 事件中文标题（时间线主文案） */
export const behaviorEventTitle = (item: LabBehaviorTimelineItem): string => {
  const payload = item.payload ?? {};
  switch (item.eventType) {
    case 'LAB_ENTER':
      return '进入实验作答';
    case 'LAB_LEAVE':
      return `离开作答（${LEAVE_REASON_LABELS[String(payload.reason)] ?? '未知原因'}）`;
    case 'STEP_VIEW':
      return item.stepId != null ? `查看题项 #${item.stepId}` : '查看题项';
    case 'PASTE':
      return payload.fieldKind === 'CODE' ? '粘贴代码内容' : '粘贴文本内容';
    case 'VISIBILITY':
      return payload.state === 'hidden' ? '切到后台' : '回到页面';
    case 'WINDOW_BLUR':
      return '窗口失焦';
    case 'WINDOW_FOCUS':
      return '窗口聚焦';
    case 'HEARTBEAT':
      return '活跃心跳';
    case 'SUBMIT_CLICK':
      return '点击提交';
    case 'SUBMIT_BLOCKED':
      return '提交被拦截';
    case 'SAVE_FAILED':
      return '保存失败';
    default:
      return item.eventType;
  }
};

/** 事件 payload 摘要（时间线次文案；无有效内容返回空串） */
export const behaviorEventPayloadSummary = (item: LabBehaviorTimelineItem): string => {
  const payload = item.payload ?? {};
  const parts: string[] = [];
  const pushNumber = (label: string, raw: unknown, formatter: (value: number) => string) => {
    const value = formatPayloadNumber(raw);
    if (value !== null) {
      parts.push(`${label} ${formatter(value)}`);
    }
  };
  const pushStepParts = () => {
    const fromStepId = formatPayloadNumber(payload.fromStepId);
    if (fromStepId !== null) {
      parts.push(`来自题项 #${fromStepId}`);
    }
    const stepId = formatPayloadNumber(payload.stepId);
    if (stepId !== null) {
      parts.push(`题项 #${stepId}`);
    }
    pushNumber('停留', payload.dwellMs, (v) => formatBehaviorDuration(v));
  };

  switch (item.eventType) {
    case 'LAB_LEAVE':
    case 'STEP_VIEW': {
      pushStepParts();
      break;
    }
    case 'PASTE': {
      pushStepParts();
      pushNumber('粘贴', payload.pastedLength, (v) => `${v} 字符`);
      if (payload.blocked === true) {
        parts.push('该题禁止粘贴');
      }
      break;
    }
    case 'VISIBILITY': {
      pushNumber('离开时长', payload.hiddenMs, (v) => formatBehaviorDuration(v));
      break;
    }
    case 'WINDOW_FOCUS': {
      pushNumber('失焦时长', payload.blurMs, (v) => formatBehaviorDuration(v));
      break;
    }
    case 'HEARTBEAT': {
      pushNumber('活跃', payload.activeMs, (v) => formatBehaviorDuration(v));
      break;
    }
    case 'SUBMIT_BLOCKED': {
      const reason = SUBMIT_BLOCKED_REASON_LABELS[String(payload.reason)];
      if (reason) {
        parts.push(reason);
      }
      break;
    }
    case 'SAVE_FAILED': {
      pushStepParts();
      if (payload.silent === true) {
        parts.push('自动保存');
      }
      break;
    }
    default:
      break;
  }
  return parts.join('；');
};

/** 时间线时间格式化（复用队列页口径：yyyy-MM-dd hh:mm） */
export const formatBehaviorTime = (value?: string | null): string =>
  value ? value.replace('T', ' ').slice(0, 19) : '-';

/**
 * 逐题停留紧凑格式："5min 30s" / "45s"；null 或 0 返回 null（表示无有效停留，
 * 由调用方决定是否显示 "—" 或跳过标记）。
 *
 * 与 formatBehaviorDuration（"h:mm:ss" 摘要口径）并存：前者适合
 * 逐题对比面板，后者适合行列摘要。
 */
export const formatStepDwell = (ms: number | null | undefined): string | null => {
  if (ms == null || ms <= 0) {
    return null;
  }
  const totalSeconds = Math.floor(ms / 1000);
  if (totalSeconds < 60) {
    return `${totalSeconds}s`;
  }
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return seconds === 0 ? `${minutes}min` : `${minutes}min ${seconds}s`;
};

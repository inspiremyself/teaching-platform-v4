/**
 * 实验作答行为采集器（技术方案 §6.2 / D12 / D17）。
 *
 * 单一采集器模块，只在 LabSteps.vue 作答会话内激活（进入 start / 离开 stop）：
 * - 队列缓冲，5s 定时或满 20 条批量发送；正常发送走 request.ts（统一鉴权/拦截/解包）；
 * - 卸载/切后台兜底用 fetch keepalive 带完整 Authorization 头——不使用 sendBeacon
 *   （sendBeacon 无法带自定义头，会造出无鉴权写入通道，D12）；
 * - eventUuid 用 newEventUuid() 生成（crypto.randomUUID 仅在安全上下文可用，
 *   非 secure context 降级到 getRandomValues / Math.random），服务端按 uuid 幂等；
 * - 浏览器直接关闭时组件 onBeforeUnmount 不执行：LAB_LEAVE{reason:"unload"} 与
 *   末段不满 60s 的尾部心跳由 beforeunload 钩子内部补发（D17②）；
 * - 收到 closed:true（作答中途实验被关闭）立即停采并丢弃后续事件（D16 配对行为）。
 */
import { reportLabBehaviorEvents } from '@/api/labs';
import type { LabBehaviorEventItem, LabBehaviorEventType } from '@/types/lab';
import { useAuthStore } from '@/stores/auth';

/** 批量发送间隔（毫秒） */
const FLUSH_INTERVAL_MS = 5000;
/** 队列满该条数立即发送 */
const BATCH_SIZE = 20;
/** 心跳间隔（毫秒），活跃时长主数据源 */
const HEARTBEAT_INTERVAL_MS = 60000;
/** 单批发送条数上限（对齐服务端 max-batch-size） */
const MAX_BATCH_SIZE = 100;
/** 同一 uuid 的最大重试次数 */
const MAX_RETRY = 3;

interface CollectorOptions {
  labId: number | string;
}

/** 采集器内部可变状态；模块级单例，同一时刻只允许一个作答会话 */
const state = {
  started: false,
  closed: false,
  labId: '' as string,
  queue: [] as LabBehaviorEventItem[],
  retryByUuid: new Map<string, number>(),
  flushTimer: 0,
  heartbeatTimer: 0,
  retryTimer: 0,
  currentStepId: null as number | null,
  currentStepSince: 0,
  lastBeatAt: 0,
  hiddenAt: null as number | null,
};

/**
 * 生成事件幂等 UUID（RFC 4122 v4）。
 * crypto.randomUUID 只在安全上下文（https / localhost）提供，通过内网 IP、隧道或旧内核
 * 访问时为 undefined；此时降级到 crypto.getRandomValues，再兜底 Math.random，
 * 保证埋点永远不会中断提交等主流程。
 */
const newEventUuid = (): string => {
  const webCrypto = typeof crypto !== 'undefined' ? crypto : undefined;
  if (webCrypto && typeof webCrypto.randomUUID === 'function') {
    return webCrypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  if (webCrypto && typeof webCrypto.getRandomValues === 'function') {
    webCrypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < bytes.length; i += 1) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }
  // 版本位固定为 4，变体位固定为 10xx
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 4)}-${hex.slice(4, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16)}`;
};

const enqueue = (eventType: LabBehaviorEventType, payload?: Record<string, unknown>, stepId?: number | null) => {
  if (!state.started || state.closed) {
    return;
  }
  const item: LabBehaviorEventItem = {
    eventUuid: newEventUuid(),
    eventType,
    occurredAt: new Date().toISOString(),
    stepId: stepId ?? null,
    payload: payload ?? {},
  };
  state.queue.push(item);
  if (state.queue.length >= BATCH_SIZE) {
    void flush();
  }
};

/** 正常路径批量发送（request.ts 出口）；失败按 uuid 计重试，≤3 次后丢弃 */
const flush = async () => {
  if (!state.started || state.closed || state.queue.length === 0) {
    return;
  }
  const batch = state.queue.slice(0, MAX_BATCH_SIZE);
  try {
    const result = await reportLabBehaviorEvents(state.labId, batch);
    // 发送成功：移除已确认条目并清理重试计数
    state.queue = state.queue.slice(batch.length);
    batch.forEach(item => state.retryByUuid.delete(item.eventUuid));
    if (result?.closed) {
      // 作答中途实验被关闭：停采并丢弃后续事件
      state.closed = true;
      behaviorCollector.stop();
    }
  } catch {
    // 失败：该批事件复用原 uuid 计一次重试，超上限的丢弃，其余保留等待退避重发
    const survivors: LabBehaviorEventItem[] = [];
    for (const item of batch) {
      const retries = (state.retryByUuid.get(item.eventUuid) ?? 0) + 1;
      if (retries <= MAX_RETRY) {
        state.retryByUuid.set(item.eventUuid, retries);
        survivors.push(item);
      } else {
        state.retryByUuid.delete(item.eventUuid);
      }
    }
    state.queue = [...survivors, ...state.queue.slice(batch.length)];
    scheduleRetry();
  }
};

/** 指数退避重试：按当前队列中已重试次数的最大值取 2s / 4s / 8s */
const scheduleRetry = () => {
  if (state.retryTimer || state.closed || state.queue.length === 0) {
    return;
  }
  const maxRetries = Math.max(...state.queue.map(item => state.retryByUuid.get(item.eventUuid) ?? 0));
  const delayMs = Math.min(8000, 1000 * 2 ** Math.max(1, maxRetries));
  state.retryTimer = window.setTimeout(() => {
    state.retryTimer = 0;
    void flush();
  }, delayMs);
};

/**
 * 卸载兜底发送：fetch keepalive 带完整鉴权头（与 sendBeacon 等效但鉴权完整，D12）。
 * keepalive 无法读响应，发送后直接清空队列。
 */
const flushKeepalive = () => {
  if (state.queue.length === 0) {
    return;
  }
  const token = useAuthStore().token;
  fetch(`/api/v1/student/labs/${state.labId}/behavior-events`, {
    method: 'POST',
    keepalive: true,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ events: state.queue.slice(0, MAX_BATCH_SIZE) }),
  }).catch(() => {
    // 卸载中无法重试，静默吞掉
  });
  state.queue = [];
};

/** 心跳节拍：仅页面可见时累计并上报 activeMs（§4.2 契约） */
const heartbeatTick = () => {
  if (document.visibilityState !== 'visible') {
    return;
  }
  const now = Date.now();
  const activeMs = now - state.lastBeatAt;
  state.lastBeatAt = now;
  if (activeMs > 0) {
    enqueue('HEARTBEAT', { activeMs });
  }
};

/**
 * 尾部心跳（D17②）：卸载/切后台前补发末段不满 60s 的活跃增量，
 * 否则"作答 5 分 30 秒直接关页面"会丢失最后 30 秒活跃时长。
 */
const emitTailHeartbeat = () => {
  if (document.visibilityState !== 'visible') {
    return;
  }
  const now = Date.now();
  const tailMs = now - state.lastBeatAt;
  if (tailMs > 0 && tailMs < HEARTBEAT_INTERVAL_MS) {
    state.lastBeatAt = now;
    enqueue('HEARTBEAT', { activeMs: tailMs });
  }
};

/** 当前题停留时长（LAB_LEAVE / 切换时归属上一题） */
const currentStepDwellMs = () => (state.currentStepId == null ? 0 : Date.now() - state.currentStepSince);

const onVisibilityChange = () => {
  if (document.visibilityState === 'hidden') {
    state.hiddenAt = Date.now();
    enqueue('VISIBILITY', { state: 'hidden' });
    // 切后台时兜底发送，避免长时间 hidden 丢缓冲
    flushKeepalive();
    return;
  }
  const hiddenMs = state.hiddenAt == null ? 0 : Date.now() - state.hiddenAt;
  state.hiddenAt = null;
  // 活跃计时从恢复可见起算，hidden 段不计入心跳
  state.lastBeatAt = Date.now();
  enqueue('VISIBILITY', { state: 'visible', hiddenMs });
};

const onWindowBlur = () => {
  enqueue('WINDOW_BLUR', {});
};

const onWindowFocus = () => {
  enqueue('WINDOW_FOCUS', {});
};

const onBeforeUnload = () => {
  // D17②：组件 onBeforeUnmount 在直接关浏览器时不执行，收尾事件在此补发
  emitTailHeartbeat();
  enqueue('LAB_LEAVE', {
    reason: 'unload',
    lastStepId: state.currentStepId ?? undefined,
    dwellMs: currentStepDwellMs() || undefined,
  });
  flushKeepalive();
};

export const behaviorCollector = {
  /**
   * 启动采集（作答页 mounted、readonly=false 时调用）。
   * 重复调用先复位旧会话，保证重进作答页状态干净。
   */
  start(options: CollectorOptions) {
    this.stop();
    state.started = true;
    state.closed = false;
    state.labId = String(options.labId);
    state.queue = [];
    state.retryByUuid.clear();
    state.currentStepId = null;
    state.currentStepSince = 0;
    state.lastBeatAt = Date.now();
    state.hiddenAt = document.visibilityState === 'hidden' ? Date.now() : null;

    state.flushTimer = window.setInterval(() => void flush(), FLUSH_INTERVAL_MS);
    state.heartbeatTimer = window.setInterval(heartbeatTick, HEARTBEAT_INTERVAL_MS);
    document.addEventListener('visibilitychange', onVisibilityChange);
    window.addEventListener('blur', onWindowBlur);
    window.addEventListener('focus', onWindowFocus);
    window.addEventListener('beforeunload', onBeforeUnload);
  },

  /** 停止采集并自卸全部监听与计时器；调用前如需收尾事件请先自行 track */
  stop() {
    state.started = false;
    if (state.flushTimer) {
      window.clearInterval(state.flushTimer);
      state.flushTimer = 0;
    }
    if (state.heartbeatTimer) {
      window.clearInterval(state.heartbeatTimer);
      state.heartbeatTimer = 0;
    }
    if (state.retryTimer) {
      window.clearTimeout(state.retryTimer);
      state.retryTimer = 0;
    }
    document.removeEventListener('visibilitychange', onVisibilityChange);
    window.removeEventListener('blur', onWindowBlur);
    window.removeEventListener('focus', onWindowFocus);
    window.removeEventListener('beforeunload', onBeforeUnload);
    state.queue = [];
    state.retryByUuid.clear();
    state.currentStepId = null;
  },

  /** 通用埋点入口：SUBMIT_CLICK / SUBMIT_BLOCKED / SAVE_FAILED / PASTE 等 */
  track(eventType: LabBehaviorEventType, payload?: Record<string, unknown>, stepId?: number | null) {
    enqueue(eventType, payload, stepId);
  },

  /**
   * 题项切换埋点（STEP_VIEW）：dwellMs 由采集器按内部时间戳计算并归属上一题，
   * 组件只在切换成功后报新题 id。首次调用产生首题的 STEP_VIEW（无 fromStepId）。
   */
  setStep(stepId: number) {
    if (!state.started || state.closed) {
      return;
    }
    const now = Date.now();
    const fromStepId = state.currentStepId;
    const dwellMs = fromStepId == null ? undefined : now - state.currentStepSince;
    state.currentStepId = stepId;
    state.currentStepSince = now;
    enqueue('STEP_VIEW', {
      fromStepId: fromStepId ?? undefined,
      dwellMs,
    }, stepId);
  },

  /**
   * 路由离开 / 提交成功的收尾：组件生命周期可达的场景（与 beforeunload 的 unload 分支互斥）。
   * reason = route（路由离开）/ submitted（提交成功）。
   * 入队后立即走正常路径 flush：调用方随后会 stop() 清空队列，不发则收尾事件丢失。
   */
  leave(reason: 'route' | 'submitted') {
    if (!state.started || state.closed) {
      return;
    }
    enqueue('LAB_LEAVE', {
      reason,
      lastStepId: state.currentStepId ?? undefined,
      dwellMs: currentStepDwellMs() || undefined,
    });
    void flush();
  },
};

export type BehaviorCollector = typeof behaviorCollector;

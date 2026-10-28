package com.opencode.teachingplatform.lab.behavior.dto;

import java.util.List;
import java.util.Map;

/**
 * 行为采集接口请求契约。
 *
 * <p>注意 {@link IngestItem#occurredAt()} 与 {@link IngestItem#payload()} 刻意保持
 * 原始类型（String / Map）：方案 §5.1 要求"单条校验失败计入 dropped、不整批 400"，
 * 若让 Jackson 直接绑定 OffsetDateTime，解析失败会整批抛 400，违背逐条容错语义。</p>
 */
public final class BehaviorRequests {

    private BehaviorRequests() {
    }

    /**
     * 批量上报请求体。
     *
     * @param events 事件数组；空 → 整批 40000；长度超 max-batch-size → 整批 40000
     */
    public record IngestBatch(List<IngestItem> events) {
    }

    /**
     * 单条行为事件。
     *
     * @param eventUuid  客户端幂等键（crypto.randomUUID），非空 ≤36 字符，重复丢弃
     * @param eventType  事件类型字符串，非法值该条丢弃
     * @param occurredAt 客户端发生时间（ISO_OFFSET_DATE_TIME 字符串），不可解析该条丢弃
     * @param stepId     关联题项，可空；非空时必须属于该实验，否则该条丢弃
     * @param payload    扩展字段；序列化超 max-payload-bytes 该条丢弃
     */
    public record IngestItem(String eventUuid,
                             String eventType,
                             String occurredAt,
                             Long stepId,
                             Map<String, Object> payload) {
    }
}

package com.opencode.teachingplatform.lab.behavior;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 行为采集与风险规则配置（{@code lab.behavior.*}，技术方案 §4.4 / D9 / D12）。
 *
 * <p>阈值必须可配：不同实验强度差异大，硬编码无法适配（D9 否决理由）。
 * 本地 profile 不覆盖，全部走默认值。与 {@code AppAuditLogProperties} 同构，
 * 由 {@link BehaviorConfig} 通过 {@code @EnableConfigurationProperties} 注册。</p>
 */
@ConfigurationProperties(prefix = "lab.behavior")
public class BehaviorProperties {

    /** 采集入口约束：批量上限、payload 上限、客户端时钟容差。 */
    private final Ingest ingest = new Ingest();

    /** 风险规则阈值：三条规则的权重与判定线。 */
    private final Risk risk = new Risk();

    public Ingest getIngest() {
        return ingest;
    }

    public Risk getRisk() {
        return risk;
    }

    public static class Ingest {

        /** 单批事件条数上限（D4）。 */
        private int maxBatchSize = 100;

        /** 单条 payload JSON 字节上限（D4）。 */
        private int maxPayloadBytes = 4096;

        /** 客户端时钟偏差容差（毫秒），超差以 receivedAt 替代并打 clockAdjusted 标记（D6）。 */
        private long clockSkewToleranceMs = 600000L;

        public int getMaxBatchSize() {
            return maxBatchSize;
        }

        public void setMaxBatchSize(int maxBatchSize) {
            this.maxBatchSize = maxBatchSize;
        }

        public int getMaxPayloadBytes() {
            return maxPayloadBytes;
        }

        public void setMaxPayloadBytes(int maxPayloadBytes) {
            this.maxPayloadBytes = maxPayloadBytes;
        }

        public long getClockSkewToleranceMs() {
            return clockSkewToleranceMs;
        }

        public void setClockSkewToleranceMs(long clockSkewToleranceMs) {
            this.clockSkewToleranceMs = clockSkewToleranceMs;
        }
    }

    public static class Risk {

        /** DURATION_TOO_SHORT：总时长 < 题项数 × 本值（毫秒）→ +40。 */
        private long perStepMinMs = 45000L;

        /** PASTE_HEAVY：粘贴字符数阈值（>）→ 与比率同时满足时 +30。 */
        private int pasteCharsThreshold = 500;

        /** PASTE_HEAVY：粘贴字符 / 终稿字符比率阈值（>）。 */
        private double pasteRatioThreshold = 0.3D;

        /** FOCUS_LOSS：切屏次数阈值（>=）。 */
        private int hiddenCountThreshold = 5;

        /** FOCUS_LOSS：隐藏时长 / 总时长比率阈值（>）。 */
        private double hiddenRatioThreshold = 0.5D;

        public long getPerStepMinMs() {
            return perStepMinMs;
        }

        public void setPerStepMinMs(long perStepMinMs) {
            this.perStepMinMs = perStepMinMs;
        }

        public int getPasteCharsThreshold() {
            return pasteCharsThreshold;
        }

        public void setPasteCharsThreshold(int pasteCharsThreshold) {
            this.pasteCharsThreshold = pasteCharsThreshold;
        }

        public double getPasteRatioThreshold() {
            return pasteRatioThreshold;
        }

        public void setPasteRatioThreshold(double pasteRatioThreshold) {
            this.pasteRatioThreshold = pasteRatioThreshold;
        }

        public int getHiddenCountThreshold() {
            return hiddenCountThreshold;
        }

        public void setHiddenCountThreshold(int hiddenCountThreshold) {
            this.hiddenCountThreshold = hiddenCountThreshold;
        }

        public double getHiddenRatioThreshold() {
            return hiddenRatioThreshold;
        }

        public void setHiddenRatioThreshold(double hiddenRatioThreshold) {
            this.hiddenRatioThreshold = hiddenRatioThreshold;
        }
    }
}

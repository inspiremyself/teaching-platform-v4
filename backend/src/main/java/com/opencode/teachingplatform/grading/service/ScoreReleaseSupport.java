package com.opencode.teachingplatform.grading.service;

import java.util.Map;

/**
 * 三闭环成绩发布的返回视图共用构造。
 *
 * <p>发布动作本身的权限校验、落库与补写逻辑仍留在各业务域，
 * 这里只收敛同构的结果视图，避免三处重复拼装。</p>
 */
public final class ScoreReleaseSupport {

    private ScoreReleaseSupport() {
    }

    public static Map<String, Object> releasedView(String scoreVisibilityMode) {
        return Map.of("released", true, "scoreVisibilityMode", scoreVisibilityMode);
    }
}

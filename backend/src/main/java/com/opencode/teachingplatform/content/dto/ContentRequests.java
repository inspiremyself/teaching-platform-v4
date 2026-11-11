package com.opencode.teachingplatform.content.dto;

import java.util.List;

/**
 * 内容目录域的请求 / 值对象聚合容器。
 *
 * <p>本期无 Controller 消费，仅由领域服务与集成测试使用；阶段 3 / 4 端点复用同一套 record。</p>
 */
public final class ContentRequests {

    private ContentRequests() {
    }

    /** 内容单元引用，承载 D21 可见性判定所需的归属与创建者信息。 */
    public record ContentRef(String contentType, Long contentId, String ownerScope, Long ownerSchoolId,
                             Long createdBy) {
    }

    /** 一条挂载目标；mounts 全量替换时按数组整体传入。 */
    public record MountRequest(String mountType, Long mountTargetId) {
    }

    /** 一次布置的一个班级目标；studentIds 为空表示整班，非空表示该班点名学生。 */
    public record CoursewareAssignmentTargetRequest(Long classId, List<Long> studentIds) {
    }

    /** 规范化后的布置目标；targetId 在 CLASS 行等于 classId，在 STUDENT 行等于学生用户 id。 */
    public record ResolvedTarget(Long classId, String targetType, Long targetId) {
    }

    /** 五类筛选请求；按教材 / 教材小节 / 课件小节均精确匹配，不展开树。 */
    public record ContentFilterRequest(String filterType, Long textbookId, Long sectionId,
                                       Long coursewareSectionId, Long subjectId) {
    }
}

package com.opencode.teachingplatform.content.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 内容目录域的请求 / 值对象聚合容器。
 *
 * <p>阶段 3 端点消费前 5 个 record 与平台内容相关请求 record；阶段 4 布置端点复用同一套 record。</p>
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

    /** 规范化后的布置目标；targetId 在 CLASS 行等于 classId，在 STUDENT 行等于 targetId。 */
    public record ResolvedTarget(Long classId, String targetType, Long targetId) {
    }

    /** 五类筛选请求；按教材 / 教材小节 / 课件小节均精确匹配，不展开树。 */
    public record ContentFilterRequest(String filterType, Long textbookId, Long sectionId,
                                       Long coursewareSectionId, Long subjectId) {
    }

    /** 新建平台学科；code 为平台级唯一标识，创建后不可变。 */
    public record CreatePlatformSubjectRequest(
            @NotBlank
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$",
                    message = "平台学科编码须为大写字母开头的大写字母、数字或下划线")
            String code,
            @NotBlank @Size(max = 128) String name) {
    }

    /** 改名平台学科；不接收 code，平台学科编码不可修改。 */
    public record UpdatePlatformSubjectRequest(@NotBlank @Size(max = 128) String name) {
    }

    /** 启停平台学科；取值由服务端 SubjectStatus.valueOf 校验，非法值返回 40000。 */
    public record ChangeSubjectStatusRequest(@NotBlank String status) {
    }

    /** 新建平台教材；globalSubjectId 必填并须为 ACTIVE 平台学科。 */
    public record CreateTextbookRequest(@NotNull Long globalSubjectId,
                                         @NotBlank @Size(max = 64) String code,
                                         @NotBlank @Size(max = 255) String title) {
    }

    /** 编辑平台教材；不接收 code，教材编码不可修改。 */
    public record UpdateTextbookRequest(@NotNull Long globalSubjectId,
                                         @NotBlank @Size(max = 255) String title) {
    }

    /** 新建教材小节；textbookId 只由路径参数提供。 */
    public record CreateTextbookSectionRequest(Long parentId,
                                                @NotBlank @Size(max = 255) String heading,
                                                @NotNull @Min(1) Integer sortOrder) {
    }

    /** 编辑教材小节；不接收 textbookId。 */
    public record UpdateTextbookSectionRequest(Long parentId,
                                                @NotBlank @Size(max = 255) String heading,
                                                @NotNull @Min(1) Integer sortOrder) {
    }

    /** AI 生成课件；coursewareId 为空是首次生成，非空是重新生成。 */
    public record GenerateCoursewareRequest(Long coursewareId,
                                             @NotNull Long globalSubjectId,
                                             @NotNull Long textbookSectionId,
                                             @NotBlank @Size(max = 255) String title,
                                             @Size(max = 255) String subtitle) {
    }

    /** 课件全量编辑；sections 为小节全量列表，mounts 为挂载全量列表。 */
    public record UpdateCoursewareRequest(@NotBlank @Size(max = 255) String title,
                                           @Size(max = 255) String subtitle,
                                           @NotNull Long globalSubjectId,
                                           @NotNull @Valid List<SectionPayload> sections,
                                           List<MountRequest> mounts) {
    }

    /** 一个小节；id 非空表示更新既有小节，为空表示新增。 */
    public record SectionPayload(Long id,
                                  @NotBlank @Size(max = 255) String heading,
                                  @NotNull @Min(1) Integer sortOrder,
                                  @NotBlank String contentJson) {
    }

    /** 按学校集合授权；schoolIds 为空数组视为非法。 */
    public record GrantSchoolsRequest(@NotEmpty List<Long> schoolIds) {
    }
}

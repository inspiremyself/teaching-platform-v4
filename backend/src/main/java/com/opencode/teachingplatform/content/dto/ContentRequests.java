package com.opencode.teachingplatform.content.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 内容目录域的请求 / 值对象聚合容器。
 *
 * <p>阶段 3 端点消费前 5 个 record 与平台内容相关请求 record；阶段 4 追加
 * {@link CreateCoursewareAssignmentRequest}（含局部严格字段白名单反序列化器），
 * 目标元素复用现网 {@code CoursewareAssignmentTargetRequest}。</p>
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

    /**
     * 一次课件布置的唯一请求体：sectionIds 为空数组表示整份，targets 为目标唯一表达。
     *
     * <p>严格字段白名单。Spring Boot 默认关闭 {@code FAIL_ON_UNKNOWN_PROPERTIES}，而
     * {@code ignoreUnknown = false} 只是注解默认值、不会重新打开该开关，故由局部反序列化器
     * 在解析阶段拒绝未知字段；抛出的 {@link JsonMappingException} 经 Spring 包装成
     * {@code HttpMessageNotReadableException}，仍由现网 GlobalExceptionHandler 映射 40000，
     * 不影响其它请求体与全局 Jackson 配置。</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonDeserialize(using = CreateCoursewareAssignmentRequest.Deserializer.class)
    public record CreateCoursewareAssignmentRequest(
            @NotNull Long coursewareId,
            @NotNull List<Long> sectionIds,
            @NotEmpty @Valid List<CoursewareAssignmentTargetRequest> targets,
            OffsetDateTime startAt,
            OffsetDateTime endAt) {

        private static final Set<String> ALLOWED_FIELDS =
                Set.of("coursewareId", "sectionIds", "targets", "startAt", "endAt");

        /** 只作用于本请求体的字段白名单解析器；不注册到 ObjectMapper，不改变其它端点行为。 */
        public static final class Deserializer extends JsonDeserializer<CreateCoursewareAssignmentRequest> {

            @Override
            public CreateCoursewareAssignmentRequest deserialize(JsonParser parser,
                                                                 DeserializationContext context)
                    throws IOException {
                JsonNode node = parser.getCodec().readTree(parser);
                Iterator<String> fieldNames = node.fieldNames();
                while (fieldNames.hasNext()) {
                    String field = fieldNames.next();
                    if (!ALLOWED_FIELDS.contains(field)) {
                        throw JsonMappingException.from(parser, "请求体含不支持的字段：" + field);
                    }
                }
                return new CreateCoursewareAssignmentRequest(
                        optionalLong(parser, node, "coursewareId"),
                        longList(parser, node.get("sectionIds"), "sectionIds"),
                        targets(parser, node.get("targets")),
                        optionalTime(parser, node, "startAt"),
                        optionalTime(parser, node, "endAt"));
            }

            private List<CoursewareAssignmentTargetRequest> targets(JsonParser parser, JsonNode value)
                    throws IOException {
                if (value == null || value.isNull()) {
                    return null;
                }
                if (!value.isArray()) {
                    throw JsonMappingException.from(parser, "targets 须为数组");
                }
                List<CoursewareAssignmentTargetRequest> targets = new ArrayList<>();
                for (JsonNode item : value) {
                    if (!item.isObject()) {
                        throw JsonMappingException.from(parser, "targets 元素须为对象");
                    }
                    Long classId = optionalLong(parser, item, "classId");
                    JsonNode studentIds = item.get("studentIds");
                    List<Long> ids = studentIds == null || studentIds.isNull()
                            ? List.of()
                            : longList(parser, studentIds, "studentIds");
                    targets.add(new CoursewareAssignmentTargetRequest(classId, ids));
                }
                return targets;
            }

            private Long optionalLong(JsonParser parser, JsonNode node, String field) throws IOException {
                JsonNode value = node.get(field);
                if (value == null || value.isNull()) {
                    return null;
                }
                if (!value.isIntegralNumber()) {
                    throw JsonMappingException.from(parser, field + " 须为整数");
                }
                return value.longValue();
            }

            private List<Long> longList(JsonParser parser, JsonNode value, String field) throws IOException {
                if (value == null || value.isNull()) {
                    return null;
                }
                if (!value.isArray()) {
                    throw JsonMappingException.from(parser, field + " 须为数组");
                }
                List<Long> ids = new ArrayList<>();
                for (JsonNode item : value) {
                    if (!item.isIntegralNumber()) {
                        throw JsonMappingException.from(parser, field + " 元素须为整数");
                    }
                    ids.add(item.longValue());
                }
                return ids;
            }

            private OffsetDateTime optionalTime(JsonParser parser, JsonNode node, String field)
                    throws IOException {
                JsonNode value = node.get(field);
                if (value == null || value.isNull()) {
                    return null;
                }
                if (!value.isTextual()) {
                    throw JsonMappingException.from(parser, field + " 须为 ISO 时间字符串");
                }
                try {
                    return OffsetDateTime.parse(value.asText(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                } catch (DateTimeParseException ex) {
                    throw JsonMappingException.from(parser, field + " 时间格式不正确");
                }
            }
        }
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

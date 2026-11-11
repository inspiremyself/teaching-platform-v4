package com.opencode.teachingplatform.content.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 内容目录域的列表项 / 视图对象聚合容器。
 *
 * <p>只承载响应体形状，不写业务判断；实体到列表项的组装在各 Service 内完成。</p>
 */
public final class ContentViews {

    private ContentViews() {
    }

    /** 平台学科字典条目。 */
    public record PlatformSubjectItem(Long id, String code, String name, String status) {
    }

    /** 平台教材条目；sectionCount 由教材小节数实时统计。 */
    public record TextbookItem(Long id, Long globalSubjectId, String code, String title, Integer sectionCount) {
    }

    /** 教材小节条目；leaf 由「无子行」判定，不落库。 */
    public record TextbookSectionItem(Long id, Long textbookId, Long parentId, String heading, Integer sortOrder,
                                      boolean leaf) {
    }

    /** 课件列表条目。 */
    public record CoursewareItem(Long id, String title, String subtitle, Long globalSubjectId, String status,
                                 OffsetDateTime publishedAt, Integer sectionCount) {
    }

    /** 课件小节条目；contentJson 是正文唯一真相源，assets 是该课件素材清单（教师端按路径找素材 id）。 */
    public record CoursewareSectionItem(Long id, Long coursewareId, String heading, Integer sortOrder,
                                        String contentJson, List<CoursewareAssetItem> assets) {
    }

    /** 课件详情；sections / mounts / assets 为该课件的完整子集合。 */
    public record CoursewareDetail(CoursewareItem courseware, List<CoursewareSectionItem> sections,
                                    List<ContentRequests.MountRequest> mounts,
                                    List<CoursewareAssetItem> assets) {
    }

    /** 课件素材条目；filePath 是 data/files 下的相对路径。 */
    public record CoursewareAssetItem(Long id, String kind, String filePath, String mime, Long sizeBytes) {
    }

    /** 素材二进制内容；由 Service 返回，Controller 包装成 ResponseEntity。 */
    public record DownloadedAsset(byte[] bytes, String mime, String fileName) {
    }

    /** 一条按学校授权记录。 */
    public record ShareGrantItem(Long id, Long schoolId, Long grantedBy) {
    }

    /** 教师侧课件浏览条目。 */
    public record ContentBrowseItem(Long id, String title, String subtitle, Long globalSubjectId, String status,
                                    Integer sectionCount) {
    }

    /** 可见性判定结果。 */
    public record ContentVisibilityItem(boolean visible, String status) {
    }
}

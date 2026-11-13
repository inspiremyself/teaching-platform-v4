package com.opencode.teachingplatform.content.controller;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.content.dto.ContentViews.DownloadedAsset;
import com.opencode.teachingplatform.content.dto.ContentViews.StudentAssignmentGroup;
import com.opencode.teachingplatform.content.dto.ContentViews.StudentSectionContent;
import com.opencode.teachingplatform.content.service.StudentContentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学生内容端点族：阅读列表 / 小节正文 / 素材，三个 GET 共用同一份可见布置判定。
 *
 * <p>只有素材端点承担 {@code Range/206} 协议层；Service 始终返回全量字节，不感知 Range。</p>
 */
@RestController
@RequestMapping("/api/v1/student/content")
@Validated
@PreAuthorize("hasRole('STUDENT')")
public class StudentContentController {

    private static final Pattern SINGLE_RANGE = Pattern.compile("^bytes=(\\d+)-(\\d*)$");

    private final StudentContentService studentContentService;

    public StudentContentController(StudentContentService studentContentService) {
        this.studentContentService = studentContentService;
    }

    @GetMapping("/assignments")
    public ApiResponse<List<StudentAssignmentGroup>> listAssignments() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(studentContentService.listGroups(currentUser));
    }

    @GetMapping("/coursewares/{coursewareId}/sections/{sectionId}")
    public ApiResponse<StudentSectionContent> readSection(@PathVariable Long coursewareId,
                                                           @PathVariable Long sectionId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(studentContentService.readSection(currentUser, coursewareId, sectionId));
    }

    @GetMapping("/coursewares/{coursewareId}/assets/{assetId}")
    public ResponseEntity<byte[]> readAsset(
            @PathVariable Long coursewareId, @PathVariable Long assetId,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        DownloadedAsset asset = studentContentService.readAsset(currentUser, coursewareId, assetId);
        byte[] bytes = asset.bytes();
        String mimeValue = asset.mime() == null || asset.mime().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : asset.mime();
        MediaType mime = MediaType.parseMediaType(mimeValue);
        Matcher matcher = SINGLE_RANGE.matcher(rangeHeader == null ? "" : rangeHeader.trim());
        if (!matcher.matches()) {
            return fullResponse(bytes, mime, asset.fileName());
        }
        try {
            long start = Long.parseLong(matcher.group(1));
            String endGroup = matcher.group(2);
            long end = endGroup.isEmpty() ? bytes.length - 1 : Long.parseLong(endGroup);
            if (start >= bytes.length || end >= bytes.length || start > end) {
                return fullResponse(bytes, mime, asset.fileName());
            }
            byte[] part = Arrays.copyOfRange(bytes, (int) start, (int) end + 1);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(mime)
                    .header("Content-Range", "bytes " + start + "-" + end + "/" + bytes.length)
                    .header("Accept-Ranges", "bytes")
                    .header("Content-Disposition", "inline; filename=\"" + asset.fileName() + "\"")
                    .body(part);
        } catch (NumberFormatException ex) {
            return fullResponse(bytes, mime, asset.fileName());
        }
    }

    /**
     * 无 Range、多区间、非法、数字溢出与越界一律退化为 200 全量，不新增状态码。
     *
     * <p>响应体用 byte[] 而非 Resource：Spring MVC 会对 200 的 Resource 响应自行按请求 Range 截取并返回
     * 206 / multipart/byteranges，那样非法 Range 就无法保持「200 全量」。Range 判定必须只发生在本 Controller。</p>
     */
    private ResponseEntity<byte[]> fullResponse(byte[] bytes, MediaType mime, String fileName) {
        return ResponseEntity.ok()
                .contentType(mime)
                .header("Accept-Ranges", "bytes")
                .header("Content-Disposition", "inline; filename=\"" + fileName + "\"")
                .body(bytes);
    }
}

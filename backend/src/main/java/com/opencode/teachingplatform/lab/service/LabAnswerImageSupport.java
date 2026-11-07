package com.opencode.teachingplatform.lab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.file.LocalFileStorageService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LabAnswerImageSupport {

    static final long MAX_IMAGE_BYTES = 1_048_576L;
    static final int MAX_IMAGES_PER_ANSWER = 5;
    /** 实验答案图片的存储根目录，落盘校验一律以它作为边界。 */
    static final String LAB_ANSWER_ROOT = "lab-answers";
    /** 历史兼容正则：只约束目录形态，不要求学生段，用于预览与旧数据回读。 */
    private static final Pattern LAB_ANSWER_PATH_PATTERN = Pattern.compile("^lab-answers/\\d{4}-\\d{2}-\\d{2}/.+");
    /** 新写入正则：路径必须包含上传者学生 id 段，用于保存与预览归属校验。 */
    private static final Pattern LAB_ANSWER_STUDENT_PATH_PATTERN = Pattern.compile("^lab-answers/\\d{4}-\\d{2}-\\d{2}/(\\d+)/.+");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private LabAnswerImageSupport() {
    }

    record ImageMeta(String path, String name, String contentType, long size) {
    }

    record TextAnswerPayload(String text, List<ImageMeta> images) {
    }

    static boolean allowsImages(String questionType) {
        String normalized = normalizeQuestionType(questionType);
        return "TEXT".equals(normalized) || "SHORT_ANSWER".equals(normalized);
    }

    static String normalizeQuestionType(String questionType) {
        String normalized = questionType == null ? "" : questionType.trim().toUpperCase(Locale.ROOT);
        if ("SHORT".equals(normalized)) {
            return "SHORT_ANSWER";
        }
        return normalized;
    }

    static void validateUpload(String contentType, long size) {
        String normalizedType = normalizeContentType(contentType);
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedType)) {
            throw new BusinessException(40000, "仅支持 png、jpeg、webp 图片");
        }
        if (size <= 0) {
            throw new BusinessException(40000, "上传文件不能为空");
        }
        if (size > MAX_IMAGE_BYTES) {
            throw new BusinessException(40000, "图片过大，请压缩或裁剪后重试");
        }
    }

    static TextAnswerPayload parseTextPayload(ObjectMapper objectMapper, String answerPayloadJson) {
        if (answerPayloadJson == null || answerPayloadJson.isBlank()) {
            return new TextAnswerPayload("", List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(answerPayloadJson);
            if (!root.isObject()) {
                throw new BusinessException(40000, "答案结构不合法");
            }
            String text = root.path("text").isMissingNode() || root.path("text").isNull()
                    ? ""
                    : root.path("text").asText("");
            List<ImageMeta> images = new ArrayList<>();
            JsonNode imagesNode = root.path("images");
            if (!imagesNode.isMissingNode() && !imagesNode.isNull()) {
                if (!imagesNode.isArray()) {
                    throw new BusinessException(40000, "图片列表格式不合法");
                }
                for (JsonNode imageNode : imagesNode) {
                    images.add(parseImageMeta(imageNode));
                }
            }
            return new TextAnswerPayload(text, images);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(40000, "答案结构不合法");
        }
    }

    static String canonicalizeTextPayload(ObjectMapper objectMapper, String answerText, TextAnswerPayload payload) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("kind", "text");
            root.put("text", answerText == null ? "" : answerText);
            ArrayNode imagesNode = root.putArray("images");
            for (ImageMeta image : payload.images()) {
                ObjectNode imageNode = imagesNode.addObject();
                imageNode.put("path", image.path());
                imageNode.put("name", image.name());
                imageNode.put("contentType", image.contentType());
                imageNode.put("size", image.size());
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new BusinessException(40000, "答案结构不合法");
        }
    }

    /**
     * 保存作答前校验图片归属：路径必须带上传者学生 id 段，且与当前学生一致。
     *
     * <p>防止学生把他人已上传的图片路径写进自己的答案，从而绕过归属校验读取他人图片。</p>
     *
     * @param questionType      题项题型，非 TEXT / SHORT_ANSWER 不允许图片
     * @param images            图片元数据列表
     * @param ownerStudentId    当前作答学生 id，作为路径学生段的唯一合法值
     * @param storageRoot       文件存储根目录
     */
    static void validateImagesForSave(String questionType,
                                      List<ImageMeta> images,
                                      Long ownerStudentId,
                                      LocalFileStorageService localFileStorageService,
                                      Path storageRoot) {
        if (images == null || images.isEmpty()) {
            return;
        }
        if (!allowsImages(questionType)) {
            throw new BusinessException(40000, "当前题型不支持图片作答");
        }
        if (images.size() > MAX_IMAGES_PER_ANSWER) {
            throw new BusinessException(40000, "单题最多上传 5 张图片");
        }
        for (ImageMeta image : images) {
            assertOwnerSegmentMatches(image.path(), ownerStudentId);
            validateStoredImageMeta(image, localFileStorageService, storageRoot);
        }
    }

    /**
     * 校验路径中的学生段等于作答学生 id。
     *
     * @param rawPath       图片相对路径，允许为 null（由后续元数据校验拒绝）
     * @param ownerStudentId 当前作答学生 id
     */
    private static void assertOwnerSegmentMatches(String rawPath, Long ownerStudentId) {
        if (rawPath == null || rawPath.isBlank() || ownerStudentId == null) {
            throw new BusinessException(40000, "图片路径不合法");
        }
        String normalizedPath = normalizeRelativePath(rawPath);
        Matcher matcher = LAB_ANSWER_STUDENT_PATH_PATTERN.matcher(normalizedPath);
        if (!matcher.matches() || !matcher.group(1).equals(String.valueOf(ownerStudentId))) {
            throw new BusinessException(40000, "图片路径不合法");
        }
    }

    /**
     * 判断路径是否符合实验答案图片的历史目录形态（仅用于预览入口的前置正则）。
     *
     * @param path 已规范化的相对路径
     * @return 是否符合 {@code lab-answers/yyyy-MM-dd/...}
     */
    static boolean matchesAnswerPath(String path) {
        return path != null && LAB_ANSWER_PATH_PATTERN.matcher(path).matches();
    }

    /**
     * 预览时的图片归属校验：学生只能读取自己答案里的图片。
     *
     * <p>路径带学生段且当前用户是学生时比较捕获组与答案归属学生，不等抛 40300；
     * 教师与旧式路径（无学生段）直接跳过比对，由调用方的实验/班级权限兜底。</p>
     *
     * @param path           已规范化的图片相对路径
     * @param currentUser    当前登录用户
     * @param ownerStudentId 该图片所挂答案的归属学生 id
     */
    static void assertStudentSegmentMatches(String path, CurrentUser currentUser, Long ownerStudentId) {
        if (currentUser == null || currentUser.role() != UserRole.STUDENT) {
            return;
        }
        if (path == null || ownerStudentId == null) {
            return;
        }
        Matcher matcher = LAB_ANSWER_STUDENT_PATH_PATTERN.matcher(path);
        if (!matcher.matches()) {
            return;
        }
        if (!matcher.group(1).equals(String.valueOf(ownerStudentId))) {
            throw new BusinessException(40300, "无权限访问该图片");
        }
    }

    static void validateStoredImageMeta(ImageMeta image,
                                        LocalFileStorageService localFileStorageService,
                                        Path storageRoot) {
        if (image.path() == null || image.path().isBlank()
                || image.name() == null || image.name().isBlank()
                || image.contentType() == null || image.contentType().isBlank()
                || image.size() <= 0) {
            throw new BusinessException(40000, "图片元数据不完整");
        }
        String normalizedContentType = normalizeContentType(image.contentType());
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new BusinessException(40000, "仅支持 png、jpeg、webp 图片");
        }
        String normalizedPath = normalizeRelativePath(image.path());
        if (!LAB_ANSWER_PATH_PATTERN.matcher(normalizedPath).matches()) {
            throw new BusinessException(40000, "图片路径不合法");
        }
        // 落盘边界收敛到 lab-answers 根，任何逃逸出该目录的路径都拒绝。
        Path labAnswerRoot = storageRoot.resolve(LAB_ANSWER_ROOT).normalize();
        Path targetPath = storageRoot.resolve(normalizedPath).normalize();
        if (!targetPath.startsWith(labAnswerRoot)) {
            throw new BusinessException(40000, "图片路径不合法");
        }
        if (!Files.exists(targetPath)) {
            throw new BusinessException(40000, "图片文件不存在，请重新上传");
        }
        try {
            long actualSize = Files.size(targetPath);
            if (actualSize > MAX_IMAGE_BYTES) {
                throw new BusinessException(40000, "图片过大，请压缩或裁剪后重试");
            }
            if (image.size() != actualSize) {
                throw new BusinessException(40000, "图片元数据与落盘文件不一致");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(40000, "图片文件不存在，请重新上传");
        }
        localFileStorageService.read(normalizedPath);
    }

    static TextAnswerPayload appendImage(TextAnswerPayload payload, ImageMeta image) {
        if (payload.images().size() >= MAX_IMAGES_PER_ANSWER) {
            throw new BusinessException(40000, "单题最多上传 5 张图片");
        }
        List<ImageMeta> images = new ArrayList<>(payload.images());
        images.add(image);
        return new TextAnswerPayload(payload.text(), images);
    }

    static void ensureUploadQuota(TextAnswerPayload payload) {
        if (payload.images().size() >= MAX_IMAGES_PER_ANSWER) {
            throw new BusinessException(40000, "单题最多上传 5 张图片");
        }
    }

    /**
     * 规范化相对路径：统一分隔符并拒绝任何 {@code ..} 段。
     *
     * <p>空路径原样返回，由调用方的正则统一拒绝，避免把“空”和“非法”混成两种错误语义。</p>
     *
     * @param relativePath 原始相对路径
     * @return 规范化后的相对路径
     */
    static String normalizeRelativePath(String relativePath) {
        String normalized = relativePath.replace('\\', '/').trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        for (String segment : normalized.split("/", -1)) {
            if ("..".equals(segment)) {
                throw new BusinessException(40000, "图片路径不合法");
            }
        }
        return normalized;
    }

    static boolean pathMatchesStoredImage(String answerJson, String normalizedPath, ObjectMapper objectMapper) {
        if (answerJson == null || answerJson.isBlank()) {
            return false;
        }
        try {
            TextAnswerPayload payload = parseTextPayload(objectMapper, answerJson);
            return payload.images().stream().anyMatch(image -> normalizeRelativePath(image.path()).equals(normalizedPath));
        } catch (BusinessException ex) {
            return false;
        }
    }

    static List<ImageMeta> extractImages(String answerJson, ObjectMapper objectMapper) {
        if (answerJson == null || answerJson.isBlank()) {
            return List.of();
        }
        try {
            return parseTextPayload(objectMapper, answerJson).images();
        } catch (BusinessException ex) {
            return List.of();
        }
    }

    private static ImageMeta parseImageMeta(JsonNode imageNode) {
        if (!imageNode.isObject()) {
            throw new BusinessException(40000, "图片元数据不完整");
        }
        String path = imageNode.path("path").asText(null);
        String name = imageNode.path("name").asText(null);
        String contentType = imageNode.path("contentType").asText(null);
        if (!imageNode.has("size") || imageNode.path("size").isNull()) {
            throw new BusinessException(40000, "图片元数据不完整");
        }
        long size = imageNode.path("size").asLong(0L);
        return new ImageMeta(path, name, contentType, size);
    }

    static String normalizeContentType(String contentType) {
        return normalizeContentTypeValue(contentType);
    }

    private static String normalizeContentTypeValue(String contentType) {
        if (contentType == null) {
            return "";
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(normalized)) {
            return "image/jpeg";
        }
        return normalized;
    }
}

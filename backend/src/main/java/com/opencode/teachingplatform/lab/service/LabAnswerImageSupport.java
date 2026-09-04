package com.opencode.teachingplatform.lab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.file.LocalFileStorageService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class LabAnswerImageSupport {

    static final long MAX_IMAGE_BYTES = 1_048_576L;
    static final int MAX_IMAGES_PER_ANSWER = 5;
    private static final Pattern LAB_ANSWER_PATH_PATTERN = Pattern.compile("^lab-answers/\\d{4}-\\d{2}-\\d{2}/.+");
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

    static void validateImagesForSave(String questionType,
                                      List<ImageMeta> images,
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
            validateStoredImageMeta(image, localFileStorageService, storageRoot);
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
        Path targetPath = storageRoot.resolve(normalizedPath).normalize();
        if (!targetPath.startsWith(storageRoot)) {
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

    static String normalizeRelativePath(String relativePath) {
        return relativePath.replace('\\', '/').trim();
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

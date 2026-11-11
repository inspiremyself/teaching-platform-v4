package com.opencode.teachingplatform.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.GenerateCoursewareRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.SectionPayload;
import com.opencode.teachingplatform.content.dto.ContentViews.CoursewareDetail;
import com.opencode.teachingplatform.content.entity.TextbookSection;
import com.opencode.teachingplatform.content.repository.TextbookSectionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 生成课件：显式触发、同步调用、只落草稿。
 *
 * <p>HTTP 调用在事务外，解析与正文校验全部通过后才交给 PlatformContentService 落库；
 * 解析失败最多重试 1 次，任一步失败都不落库。</p>
 */
@Service
public class CoursewareGenerationService {

    private static final String OWNER_SCOPE = "PLATFORM";
    private static final int MAX_ATTEMPTS = 2;

    private static final String SYSTEM_PROMPT = """
            你是高校课程讲解课件的生成器。只输出 JSON，不要输出 Markdown 代码块。
            输出格式：{"sections":[{"heading":"小节标题","content":{"version":1,"paragraphs":[
            {"class":"text","sentences":[{"text":"句子正文","narration":"","image":"","audio":""}],"exercises":[]}]}}]}
            要求：class 只能是 text / tip / example / note；exercises 必须是空数组；image 与 audio 留空字符串。""";

    private final CoursewareAiGateway coursewareAiGateway;
    private final CoursewareContentJsonSupport contentJsonSupport;
    private final ContentSubjectScopeService contentSubjectScopeService;
    private final PlatformSubjectActiveSupport activeSupport;
    private final TextbookSectionRepository textbookSectionRepository;
    private final PlatformContentService platformContentService;
    private final ObjectMapper objectMapper;

    public CoursewareGenerationService(CoursewareAiGateway coursewareAiGateway,
                                       CoursewareContentJsonSupport contentJsonSupport,
                                       ContentSubjectScopeService contentSubjectScopeService,
                                       PlatformSubjectActiveSupport activeSupport,
                                       TextbookSectionRepository textbookSectionRepository,
                                       PlatformContentService platformContentService,
                                       ObjectMapper objectMapper) {
        this.coursewareAiGateway = coursewareAiGateway;
        this.contentJsonSupport = contentJsonSupport;
        this.contentSubjectScopeService = contentSubjectScopeService;
        this.activeSupport = activeSupport;
        this.textbookSectionRepository = textbookSectionRepository;
        this.platformContentService = platformContentService;
        this.objectMapper = objectMapper;
    }

    /** 显式触发 AI 生成；coursewareId 为空是首次生成，非空是重新生成。 */
    public CoursewareDetail generate(CurrentUser currentUser, GenerateCoursewareRequest request) {
        contentSubjectScopeService.validate(currentUser, OWNER_SCOPE, request.globalSubjectId(), null);
        activeSupport.requireActive(request.globalSubjectId());
        TextbookSection textbookSection = textbookSectionRepository.findById(request.textbookSectionId())
                .orElseThrow(() -> new BusinessException(40400, "教材小节不存在"));
        String userPrompt = buildUserPrompt(request, textbookSection.getHeading());
        List<SectionPayload> payloads = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS && payloads == null; attempt++) {
            String raw = coursewareAiGateway.complete(SYSTEM_PROMPT, userPrompt);
            try {
                payloads = parse(raw);
            } catch (BusinessException ex) {
                if (attempt == MAX_ATTEMPTS - 1) {
                    throw new BusinessException(40000, "AI 生成结果解析失败，未写入任何数据");
                }
            }
        }
        return platformContentService.saveGenerated(currentUser, request, payloads);
    }

    /** 解析模型输出并逐节校验正文协议与容量。 */
    private List<SectionPayload> parse(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode sections = root.path("sections");
            if (!sections.isArray() || sections.isEmpty()) {
                throw new BusinessException(40000, "AI 生成结果缺少小节");
            }
            List<SectionPayload> payloads = new ArrayList<>();
            int index = 0;
            for (JsonNode node : sections) {
                index++;
                String heading = node.path("heading").asText("第 " + index + " 节");
                String contentJson = objectMapper.writeValueAsString(node.path("content"));
                contentJsonSupport.validate(contentJson);
                payloads.add(new SectionPayload(null, heading, index, contentJson));
            }
            return payloads;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(40000, "AI 生成结果解析失败");
        }
    }

    private String buildUserPrompt(GenerateCoursewareRequest request, String textbookHeading) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("课件标题：").append(request.title());
        if (request.subtitle() != null && !request.subtitle().isBlank()) {
            prompt.append("；副标题：").append(request.subtitle());
        }
        prompt.append("；平台学科 id：").append(request.globalSubjectId());
        prompt.append("；教材小节：").append(textbookHeading);
        prompt.append("。请生成结构化讲解正文。");
        return prompt.toString();
    }
}

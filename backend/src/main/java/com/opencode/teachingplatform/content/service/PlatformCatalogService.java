package com.opencode.teachingplatform.content.service;

import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.ChangeSubjectStatusRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.CreatePlatformSubjectRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.CreateTextbookRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.CreateTextbookSectionRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.UpdatePlatformSubjectRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.UpdateTextbookRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.UpdateTextbookSectionRequest;
import com.opencode.teachingplatform.content.dto.ContentViews.PlatformSubjectItem;
import com.opencode.teachingplatform.content.dto.ContentViews.TextbookItem;
import com.opencode.teachingplatform.content.dto.ContentViews.TextbookSectionItem;
import com.opencode.teachingplatform.content.entity.Textbook;
import com.opencode.teachingplatform.content.entity.TextbookSection;
import com.opencode.teachingplatform.content.repository.TextbookRepository;
import com.opencode.teachingplatform.content.repository.TextbookSectionRepository;
import com.opencode.teachingplatform.school.SubjectStatus;
import com.opencode.teachingplatform.school.entity.Subject;
import com.opencode.teachingplatform.school.repository.SubjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 平台目录维护：平台学科字典 + 平台教材目录与教材小节树。
 *
 * <p>教材目录是平台级资产，由平台超管维护；学校只通过 school_textbook 逐条启用，本期不提供写入端点。
 * 教材的学科归属必须指向 ACTIVE 平台学科，与内容侧共用 {@link PlatformSubjectActiveSupport}。</p>
 */
@Service
public class PlatformCatalogService {

    private final SubjectRepository subjectRepository;
    private final TextbookRepository textbookRepository;
    private final TextbookSectionRepository textbookSectionRepository;
    private final ContentSubjectScopeService contentSubjectScopeService;
    private final PlatformSubjectActiveSupport activeSupport;

    public PlatformCatalogService(SubjectRepository subjectRepository,
                                  TextbookRepository textbookRepository,
                                  TextbookSectionRepository textbookSectionRepository,
                                  ContentSubjectScopeService contentSubjectScopeService,
                                  PlatformSubjectActiveSupport activeSupport) {
        this.subjectRepository = subjectRepository;
        this.textbookRepository = textbookRepository;
        this.textbookSectionRepository = textbookSectionRepository;
        this.contentSubjectScopeService = contentSubjectScopeService;
        this.activeSupport = activeSupport;
    }

    /** 平台学科字典全量（含停用项），按编码升序，便于平台超管启停。 */
    public List<PlatformSubjectItem> listSubjects() {
        List<Subject> subjects = new ArrayList<>(subjectRepository.findAll());
        subjects.sort(Comparator.comparing(Subject::getCode));
        return subjects.stream().map(this::toSubjectItem).toList();
    }

    @Transactional
    public PlatformSubjectItem createSubject(CreatePlatformSubjectRequest request) {
        if (subjectRepository.findByCode(request.code()).isPresent()) {
            throw new BusinessException(40902, "平台学科编码已存在");
        }
        Subject subject = new Subject();
        subject.setCode(request.code());
        subject.setName(request.name());
        subject.setStatus(SubjectStatus.ACTIVE);
        return toSubjectItem(subjectRepository.save(subject));
    }

    @Transactional
    public PlatformSubjectItem updateSubject(Long subjectId, UpdatePlatformSubjectRequest request) {
        Subject subject = requireSubject(subjectId);
        subject.setName(request.name());
        return toSubjectItem(subjectRepository.save(subject));
    }

    @Transactional
    public PlatformSubjectItem changeSubjectStatus(Long subjectId, ChangeSubjectStatusRequest request) {
        Subject subject = requireSubject(subjectId);
        subject.setStatus(parseStatus(request.status()));
        return toSubjectItem(subjectRepository.save(subject));
    }

    /** 平台教材列表；globalSubjectId 为空表示不过滤学科。 */
    public List<TextbookItem> listTextbooks(Long globalSubjectId) {
        List<Textbook> textbooks = globalSubjectId == null
                ? new ArrayList<>(textbookRepository.findAll())
                : textbookRepository.findByGlobalSubjectId(globalSubjectId);
        textbooks.sort(Comparator.comparing(Textbook::getId).reversed());
        return textbooks.stream().map(this::toTextbookItem).toList();
    }

    @Transactional
    public TextbookItem createTextbook(CreateTextbookRequest request) {
        contentSubjectScopeService.validateTextbook(request.globalSubjectId());
        activeSupport.requireActive(request.globalSubjectId());
        Textbook textbook = new Textbook();
        textbook.setGlobalSubjectId(request.globalSubjectId());
        textbook.setCode(request.code());
        textbook.setTitle(request.title());
        try {
            return toTextbookItem(textbookRepository.saveAndFlush(textbook));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(40900, "教材编码已存在");
        }
    }

    @Transactional
    public TextbookItem updateTextbook(Long textbookId, UpdateTextbookRequest request) {
        Textbook textbook = requireTextbook(textbookId);
        contentSubjectScopeService.validateTextbook(request.globalSubjectId());
        activeSupport.requireActive(request.globalSubjectId());
        textbook.setGlobalSubjectId(request.globalSubjectId());
        textbook.setTitle(request.title());
        return toTextbookItem(textbookRepository.save(textbook));
    }

    /** 教材小节树，按 sort_order 升序；leaf 由「无子行」判定。 */
    public List<TextbookSectionItem> listSections(Long textbookId) {
        requireTextbook(textbookId);
        List<TextbookSection> sections = new ArrayList<>(textbookSectionRepository.findByTextbookId(textbookId));
        sections.sort(Comparator.comparing(TextbookSection::getSortOrder)
                .thenComparing(TextbookSection::getId));
        return sections.stream().map(this::toSectionItem).toList();
    }

    @Transactional
    public TextbookSectionItem createSection(Long textbookId, CreateTextbookSectionRequest request) {
        requireTextbook(textbookId);
        requireParentInTextbook(textbookId, request.parentId());
        TextbookSection section = new TextbookSection();
        section.setTextbookId(textbookId);
        section.setParentId(request.parentId());
        section.setHeading(request.heading());
        section.setSortOrder(request.sortOrder());
        return toSectionItem(textbookSectionRepository.save(section));
    }

    @Transactional
    public TextbookSectionItem updateSection(Long textbookId, Long sectionId,
                                            UpdateTextbookSectionRequest request) {
        TextbookSection section = textbookSectionRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException(40400, "教材小节不存在"));
        if (!section.getTextbookId().equals(textbookId)) {
            throw new BusinessException(40400, "教材小节不存在");
        }
        requireParentInTextbook(textbookId, request.parentId());
        section.setParentId(request.parentId());
        section.setHeading(request.heading());
        section.setSortOrder(request.sortOrder());
        return toSectionItem(textbookSectionRepository.save(section));
    }

    private Subject requireSubject(Long subjectId) {
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> new BusinessException(40400, "平台学科不存在"));
    }

    private Textbook requireTextbook(Long textbookId) {
        return textbookRepository.findById(textbookId)
                .orElseThrow(() -> new BusinessException(40400, "教材不存在"));
    }

    private void requireParentInTextbook(Long textbookId, Long parentId) {
        if (parentId == null) {
            return;
        }
        TextbookSection parent = textbookSectionRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException(40400, "教材小节不存在"));
        if (!parent.getTextbookId().equals(textbookId)) {
            throw new BusinessException(40400, "教材小节不存在");
        }
    }

    private SubjectStatus parseStatus(String status) {
        try {
            return SubjectStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException(40000, "学科状态须为 ACTIVE 或 DISABLED");
        }
    }

    private PlatformSubjectItem toSubjectItem(Subject subject) {
        return new PlatformSubjectItem(subject.getId(), subject.getCode(), subject.getName(),
                subject.getStatus().name());
    }

    private TextbookItem toTextbookItem(Textbook textbook) {
        int sectionCount = textbookSectionRepository.findByTextbookId(textbook.getId()).size();
        return new TextbookItem(textbook.getId(), textbook.getGlobalSubjectId(), textbook.getCode(),
                textbook.getTitle(), sectionCount);
    }

    private TextbookSectionItem toSectionItem(TextbookSection section) {
        boolean leaf = !textbookSectionRepository.existsByParentId(section.getId());
        return new TextbookSectionItem(section.getId(), section.getTextbookId(), section.getParentId(),
                section.getHeading(), section.getSortOrder(), leaf);
    }
}

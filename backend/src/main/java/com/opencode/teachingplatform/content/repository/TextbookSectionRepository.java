package com.opencode.teachingplatform.content.repository;

import com.opencode.teachingplatform.content.entity.TextbookSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TextbookSectionRepository extends JpaRepository<TextbookSection, Long> {
    List<TextbookSection> findByTextbookId(Long textbookId);

    List<TextbookSection> findByParentId(Long parentId);

    boolean existsByParentId(Long parentId);
}

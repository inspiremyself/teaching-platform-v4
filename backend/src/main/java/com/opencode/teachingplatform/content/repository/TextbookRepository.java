package com.opencode.teachingplatform.content.repository;

import com.opencode.teachingplatform.content.entity.Textbook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TextbookRepository extends JpaRepository<Textbook, Long> {
    List<Textbook> findByGlobalSubjectId(Long globalSubjectId);
}

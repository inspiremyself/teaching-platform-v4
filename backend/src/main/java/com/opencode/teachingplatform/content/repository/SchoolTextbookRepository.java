package com.opencode.teachingplatform.content.repository;

import com.opencode.teachingplatform.content.entity.SchoolTextbook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolTextbookRepository extends JpaRepository<SchoolTextbook, Long> {
    List<SchoolTextbook> findBySchoolId(Long schoolId);

    boolean existsBySchoolIdAndTextbookId(Long schoolId, Long textbookId);
}

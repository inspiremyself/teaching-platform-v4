package com.opencode.teachingplatform.content.repository;

import com.opencode.teachingplatform.content.entity.ContentShareGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentShareGrantRepository extends JpaRepository<ContentShareGrant, Long> {
    boolean existsBySchoolIdAndContentTypeAndContentId(Long schoolId, String contentType, Long contentId);

    Optional<ContentShareGrant> findBySchoolIdAndContentTypeAndContentId(
            Long schoolId, String contentType, Long contentId);

    List<ContentShareGrant> findBySchoolIdAndContentType(Long schoolId, String contentType);

    void deleteByContentTypeAndContentId(String contentType, Long contentId);
}

package com.opencode.teachingplatform.content.repository;

import com.opencode.teachingplatform.content.entity.ContentMount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentMountRepository extends JpaRepository<ContentMount, Long> {
    List<ContentMount> findByContentTypeAndContentId(String contentType, Long contentId);

    List<ContentMount> findByMountTypeAndMountTargetId(String mountType, Long mountTargetId);

    void deleteByContentTypeAndContentId(String contentType, Long contentId);

    void deleteByMountTypeAndMountTargetId(String mountType, Long mountTargetId);
}

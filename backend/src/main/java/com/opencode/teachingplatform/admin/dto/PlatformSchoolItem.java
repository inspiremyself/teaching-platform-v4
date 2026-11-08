package com.opencode.teachingplatform.admin.dto;

import com.opencode.teachingplatform.school.SchoolStatus;

import java.time.OffsetDateTime;

public record PlatformSchoolItem(
        Long id,
        String name,
        String code,
        SchoolStatus status,
        String adminUsername,
        OffsetDateTime createdAt
) {
}

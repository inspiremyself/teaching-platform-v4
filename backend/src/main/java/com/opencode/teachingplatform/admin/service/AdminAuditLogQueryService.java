package com.opencode.teachingplatform.admin.service;

import com.opencode.teachingplatform.admin.dto.AuditLogListItem;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.api.PagedResult;
import com.opencode.teachingplatform.common.audit.AuditAction;
import com.opencode.teachingplatform.common.audit.AuditLog;
import com.opencode.teachingplatform.common.audit.AuditLogRepository;
import com.opencode.teachingplatform.common.audit.AuditResourceType;
import com.opencode.teachingplatform.common.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminAuditLogQueryService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;
    private final SysUserRepository sysUserRepository;

    public AdminAuditLogQueryService(AuditLogRepository auditLogRepository, SysUserRepository sysUserRepository) {
        this.auditLogRepository = auditLogRepository;
        this.sysUserRepository = sysUserRepository;
    }

    @Transactional(readOnly = true)
    public PagedResult<AuditLogListItem> list(
            CurrentUser currentUser,
            int page,
            int pageSize,
            String action,
            String resourceType,
            Long userId,
            String from,
            String to
    ) {
        if (page < 1) {
            throw new BusinessException(40000, "参数不合法");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(40000, "参数不合法");
        }

        AuditAction actionFilter = parseAction(action);
        AuditResourceType resourceTypeFilter = parseResourceType(resourceType);
        OffsetDateTime fromAt = parseFromDate(from);
        OffsetDateTime toAt = parseToDate(to);

        Long schoolId = currentUser.schoolId();
        Specification<AuditLog> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("schoolId"), schoolId));
            if (actionFilter != null) {
                predicates.add(cb.equal(root.get("action"), actionFilter.name()));
            }
            if (resourceTypeFilter != null) {
                predicates.add(cb.equal(root.get("resourceType"), resourceTypeFilter.name()));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (fromAt != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromAt));
            }
            if (toAt != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toAt));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        PageRequest pageRequest = PageRequest.of(
                page - 1,
                pageSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<AuditLog> result = auditLogRepository.findAll(specification, pageRequest);

        Set<Long> userIds = result.getContent().stream()
                .map(AuditLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SysUser> usersById = sysUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        List<AuditLogListItem> items = result.getContent().stream()
                .map(row -> toItem(row, usersById.get(row.getUserId())))
                .toList();

        return new PagedResult<>(items, page, pageSize, result.getTotalElements());
    }

    private AuditLogListItem toItem(AuditLog row, SysUser user) {
        String username = user == null ? null : user.getUsername();
        String displayName = user == null ? null : user.getDisplayName();
        return new AuditLogListItem(
                row.getId(),
                row.getUserId(),
                username,
                displayName,
                row.getAction(),
                row.getResourceType(),
                row.getResourceId(),
                row.getDetailJson(),
                row.getTraceId(),
                row.getClientIp(),
                row.getActorRole(),
                row.getCreatedAt()
        );
    }

    private AuditAction parseAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        try {
            return AuditAction.valueOf(action.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(40000, "参数不合法");
        }
    }

    private AuditResourceType parseResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return null;
        }
        try {
            return AuditResourceType.valueOf(resourceType.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(40000, "参数不合法");
        }
    }

    private OffsetDateTime parseFromDate(String from) {
        if (from == null || from.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(from.trim(), DATE_FORMAT);
            return date.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        } catch (DateTimeParseException ex) {
            throw new BusinessException(40000, "参数不合法");
        }
    }

    private OffsetDateTime parseToDate(String to) {
        if (to == null || to.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(to.trim(), DATE_FORMAT);
            return date.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        } catch (DateTimeParseException ex) {
            throw new BusinessException(40000, "参数不合法");
        }
    }
}

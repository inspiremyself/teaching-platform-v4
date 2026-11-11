package com.opencode.teachingplatform.auth.service;

import com.opencode.teachingplatform.auth.dto.ChangePasswordRequest;
import com.opencode.teachingplatform.auth.dto.LoginRequest;
import com.opencode.teachingplatform.auth.dto.UpdateProfileRequest;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.entity.UserLoginSession;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.common.infra.audit.AuditAction;
import com.opencode.teachingplatform.common.infra.audit.AuditLogService;
import com.opencode.teachingplatform.common.infra.audit.AuditResourceType;
import com.opencode.teachingplatform.common.infra.audit.SecurityAuditEvent;
import com.opencode.teachingplatform.auth.LoginSessionStatus;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.UserStatus;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.school.SchoolStatus;
import com.opencode.teachingplatform.school.entity.School;
import com.opencode.teachingplatform.school.repository.SchoolRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthService {

    private static final int LOGIN_ERROR_CODE = 40101;
    private static final String LOGIN_ERROR_MESSAGE = "用户名、密码或角色错误";
    private static final int LOGIN_CONFLICT_CODE = 40901;
    private static final String LOGIN_CONFLICT_MESSAGE = "该账号已在其他设备登录，继续登录将使旧设备下线";
    private static final int LOGIN_DISABLED_CODE = 40103;
    private static final String LOGIN_DISABLED_MESSAGE = "该账号已被停用，请联系管理员";
    private static final int LOGIN_MULTI_SCHOOL_CODE = 40903;
    private static final String LOGIN_MULTI_SCHOOL_MESSAGE = "该用户名在多个学校存在，请填写学校编码后重试";

    private final SysUserRepository sysUserRepository;
    private final UserLoginSessionRepository userLoginSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuditLogService auditLogService;
    private final SchoolRepository schoolRepository;

    public AuthService(
            SysUserRepository sysUserRepository,
            UserLoginSessionRepository userLoginSessionRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            AuditLogService auditLogService,
            SchoolRepository schoolRepository
    ) {
        this.sysUserRepository = sysUserRepository;
        this.userLoginSessionRepository = userLoginSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.auditLogService = auditLogService;
        this.schoolRepository = schoolRepository;
    }

    @Transactional
    public Map<String, Object> login(LoginRequest request) {
        List<SysUser> users = sysUserRepository.findAllByUsernameAndRole(request.username(), request.role());
        String schoolCode = request.schoolCode() == null ? null : request.schoolCode().trim();
        if (schoolCode != null && !schoolCode.isEmpty()) {
            // 学校编码精确匹配；不存在与「该学校无此账号」统一返回登录错误，不泄露账号存在性
            Long schoolId = schoolRepository.findByCode(schoolCode).map(School::getId).orElse(null);
            if (schoolId == null) {
                logLoginFailure(null, null, request);
                throw new BusinessException(LOGIN_ERROR_CODE, LOGIN_ERROR_MESSAGE);
            }
            users = users.stream()
                    .filter(candidate -> Objects.equals(candidate.getSchoolId(), schoolId))
                    .toList();
        }
        if (users.isEmpty()) {
            logLoginFailure(null, null, request);
            throw new BusinessException(LOGIN_ERROR_CODE, LOGIN_ERROR_MESSAGE);
        }
        if (users.size() > 1) {
            // 尚未确定具体账号，不写登录失败审计，避免跨校信息写入错误学校维度
            throw new BusinessException(LOGIN_MULTI_SCHOOL_CODE, LOGIN_MULTI_SCHOOL_MESSAGE);
        }
        SysUser user = users.get(0);
        if (user.getStatus() != UserStatus.ACTIVE) {
            logLoginFailure(user.getId(), user.getSchoolId(), request);
            throw new BusinessException(LOGIN_DISABLED_CODE, LOGIN_DISABLED_MESSAGE);
        }
        // 平台级账号无学校归属，跳过学校停用校验；其余账号的所属学校停用或缺失时统一拒绝登录。
        if (user.getSchoolId() != null) {
            School school = schoolRepository.findById(user.getSchoolId()).orElse(null);
            if (school == null || school.getStatus() == SchoolStatus.DISABLED) {
                logLoginFailure(user.getId(), user.getSchoolId(), request);
                throw new BusinessException(LOGIN_DISABLED_CODE, LOGIN_DISABLED_MESSAGE);
            }
        }
        if (!matchesPassword(request.password(), user.getPasswordHash())) {
            logLoginFailure(user.getId(), user.getSchoolId(), request);
            throw new BusinessException(LOGIN_ERROR_CODE, LOGIN_ERROR_MESSAGE);
        }
        // 历史 {noop}/无前缀明文账号登录成功后立即升级为 {bcrypt}
        if (needsUpgrade(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        OffsetDateTime now = OffsetDateTime.now();
        userLoginSessionRepository.findFirstByUser_IdAndStatusOrderByCreatedAtDesc(user.getId(), LoginSessionStatus.ACTIVE)
                .ifPresent(existingSession -> handleExistingActiveSession(existingSession, request.forceLogin(), now));

        user.setLastLoginAt(now);
        SysUser saved = sysUserRepository.save(user);
        UserLoginSession loginSession = createActiveSession(saved, now);
        String token = jwtTokenService.issueToken(saved, loginSession.getSessionKey(), now.toInstant());
        auditLogService.logSecurity(new SecurityAuditEvent(
                AuditAction.LOGIN_SUCCESS,
                saved.getId(),
                saved.getSchoolId(),
                saved.getRole().name(),
                AuditResourceType.USER,
                saved.getId(),
                Map.of(
                        "role", saved.getRole().name(),
                        "forceLogin", request.forceLogin()
                )
        ));
        return Map.of(
                "token", token,
                "user", Map.of(
                        "id", saved.getId(),
                        "username", saved.getUsername(),
                        "displayName", saved.getDisplayName(),
                        "role", saved.getRole().name()
                ),
                "permissions", permissionsOf(saved)
        );
    }

    @Transactional
    public Map<String, Object> logout(CurrentUser currentUser) {
        if (currentUser.sessionKey() != null) {
            userLoginSessionRepository.findBySessionKey(currentUser.sessionKey())
                    .ifPresent(session -> {
                        OffsetDateTime now = OffsetDateTime.now();
                        session.setStatus(LoginSessionStatus.LOGGED_OUT);
                        session.setLastSeenAt(now);
                        session.setInvalidatedAt(now);
                        userLoginSessionRepository.save(session);
                    });
        }
        auditLogService.log(currentUser, AuditAction.LOGOUT, AuditResourceType.USER, currentUser.id(), Map.of("loggedOut", true));
        return Map.of("loggedOut", true);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCurrentUserProfile(CurrentUser currentUser) {
        SysUser user = sysUserRepository.findById(currentUser.id()).orElseThrow(() -> new BusinessException(40400, "用户不存在"));
        return toUserProfile(user);
    }

    @Transactional
    public Map<String, Object> updateProfile(CurrentUser currentUser, UpdateProfileRequest request) {
        SysUser user = sysUserRepository.findById(currentUser.id()).orElseThrow(() -> new BusinessException(40400, "用户不存在"));
        user.setDisplayName(request.displayName().trim());
        user.setEmail(normalizeNullable(request.email()));
        user.setPhone(normalizeNullable(request.phone()));
        user.setOfficeHours(normalizeNullable(request.officeHours()));
        user.setBio(normalizeNullable(request.bio()));
        SysUser saved = sysUserRepository.save(user);
        auditLogService.log(
                currentUser,
                AuditAction.PROFILE_UPDATED,
                AuditResourceType.USER,
                currentUser.id(),
                Map.of("displayName", saved.getDisplayName())
        );
        return toUserProfile(saved);
    }

    @Transactional
    public Map<String, Object> changePassword(CurrentUser currentUser, ChangePasswordRequest request) {
        SysUser user = sysUserRepository.findById(currentUser.id()).orElseThrow(() -> new BusinessException(40400, "用户不存在"));
        if (!matchesPassword(request.oldPassword(), user.getPasswordHash())) {
            auditLogService.log(
                    currentUser,
                    AuditAction.PASSWORD_CHANGE_FAILED,
                    AuditResourceType.USER,
                    currentUser.id(),
                    Map.of("failed", true)
            );
            throw new BusinessException(40010, "旧密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        sysUserRepository.save(user);

        // 密码变更后使旧登录态立即失效：保留当前会话，其余 ACTIVE 会话置 KICKED。
        OffsetDateTime now = OffsetDateTime.now();
        String sessionKey = currentUser.sessionKey();
        if (sessionKey == null || sessionKey.isBlank()) {
            userLoginSessionRepository.kickActiveSessionsByUserId(
                    user.getId(), LoginSessionStatus.ACTIVE, LoginSessionStatus.KICKED, now);
        } else {
            userLoginSessionRepository.kickActiveSessionsByUserIdExceptSessionKey(
                    user.getId(), sessionKey, LoginSessionStatus.ACTIVE, LoginSessionStatus.KICKED, now);
        }

        auditLogService.log(currentUser, AuditAction.PASSWORD_CHANGED, AuditResourceType.USER, currentUser.id(), Map.of("changed", true));
        return Map.of("changed", true);
    }

    /**
     * 密码匹配：带编码前缀的哈希走 PasswordEncoder；无前缀历史明文直接字符串比较。
     */
    private boolean matchesPassword(String raw, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (storedHash.startsWith("{")) {
            return passwordEncoder.matches(raw, storedHash);
        }
        return raw.equals(storedHash);
    }

    /**
     * 判断是否需要升级密码哈希：{noop} 与无前缀明文必须升级，只有 {bcrypt} 才交给 upgradeEncoding 判强度。
     */
    private boolean needsUpgrade(String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return true;
        }
        return !storedHash.startsWith("{bcrypt}") || passwordEncoder.upgradeEncoding(storedHash);
    }

    private void logLoginFailure(Long userId, Long schoolId, LoginRequest request) {
        auditLogService.logSecurity(new SecurityAuditEvent(
                AuditAction.LOGIN_FAILURE,
                userId,
                schoolId,
                null,
                AuditResourceType.USER,
                userId,
                Map.of(
                        "username", request.username().trim(),
                        "role", request.role().name()
                )
        ));
    }

    private List<String> permissionsOf(SysUser user) {
        return switch (user.getRole()) {
            case SCHOOL_ADMIN -> List.of(
                    "SCHOOL_READ", "SCHOOL_WRITE",
                    "TEACHER_READ", "TEACHER_WRITE",
                    "CLASS_READ", "CLASS_WRITE",
                    "STUDENT_READ", "STUDENT_WRITE",
                    "CONTENT_WRITE"
            );
            case TEACHER -> List.of("CLASS_READ", "LAB_WRITE", "HOMEWORK_WRITE", "EXAM_WRITE", "ANALYSIS_READ");
            case STUDENT -> List.of("MATERIAL_READ", "LAB_READ", "HOMEWORK_READ", "EXAM_READ", "SCORE_READ");
            case PLATFORM_ADMIN -> List.of(
                    "SCHOOL_READ", "SCHOOL_WRITE", "SCHOOL_PROVISION",
                    "CONTENT_WRITE", "CONTENT_SHARE"
            );
        };
    }

    private Map<String, Object> toUserProfile(SysUser user) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("displayName", user.getDisplayName());
        profile.put("role", user.getRole().name());
        // 平台级账号不写 schoolId / schoolName，也不得对 null 调用 findById。
        if (user.getSchoolId() != null) {
            profile.put("schoolId", user.getSchoolId());
            schoolRepository.findById(user.getSchoolId()).ifPresent(school -> profile.put("schoolName", school.getName()));
        }
        profile.put("email", user.getEmail());
        profile.put("phone", user.getPhone());
        profile.put("officeHours", user.getOfficeHours());
        profile.put("bio", user.getBio());
        profile.put("mustChangePassword", user.isMustChangePassword());
        profile.put("permissions", permissionsOf(user));
        return profile;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void handleExistingActiveSession(UserLoginSession existingSession, boolean forceLogin, OffsetDateTime now) {
        if (existingSession.getExpiresAt().isBefore(now)) {
            existingSession.setStatus(LoginSessionStatus.EXPIRED);
            existingSession.setInvalidatedAt(now);
            userLoginSessionRepository.save(existingSession);
            return;
        }
        if (!forceLogin) {
            throw new BusinessException(LOGIN_CONFLICT_CODE, LOGIN_CONFLICT_MESSAGE);
        }
        existingSession.setStatus(LoginSessionStatus.KICKED);
        existingSession.setInvalidatedAt(now);
        userLoginSessionRepository.save(existingSession);
        SysUser kickedUser = existingSession.getUser();
        auditLogService.logSecurity(new SecurityAuditEvent(
                AuditAction.SESSION_INVALIDATED,
                kickedUser.getId(),
                kickedUser.getSchoolId(),
                kickedUser.getRole().name(),
                AuditResourceType.USER,
                kickedUser.getId(),
                Map.of("reason", "forceLogin")
        ));
    }

    private UserLoginSession createActiveSession(SysUser user, OffsetDateTime now) {
        UserLoginSession loginSession = new UserLoginSession();
        loginSession.setUser(user);
        loginSession.setSessionKey(UUID.randomUUID().toString().replace("-", ""));
        loginSession.setStatus(LoginSessionStatus.ACTIVE);
        loginSession.setLastSeenAt(now);
        loginSession.setExpiresAt(jwtTokenService.calculateExpirationAt(now.toInstant()));
        return userLoginSessionRepository.save(loginSession);
    }
}

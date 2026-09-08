package com.opencode.teachingplatform.auth.repository;

import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    Optional<SysUser> findByUsernameAndRole(String username, UserRole role);
    Optional<SysUser> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsBySchoolIdAndUsername(Long schoolId, String username);
    List<SysUser> findBySchoolIdAndRole(Long schoolId, UserRole role);
    List<SysUser> findBySchoolIdAndRoleAndStatus(Long schoolId, UserRole role, UserStatus status);
    Optional<SysUser> findByIdAndSchoolId(Long id, Long schoolId);
    Optional<SysUser> findByIdAndSchoolIdAndRole(Long id, Long schoolId, UserRole role);
}

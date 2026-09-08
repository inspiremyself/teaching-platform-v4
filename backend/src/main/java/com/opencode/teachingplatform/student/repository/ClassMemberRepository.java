package com.opencode.teachingplatform.student.repository;

import com.opencode.teachingplatform.student.entity.ClassMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClassMemberRepository extends JpaRepository<ClassMember, Long> {
    List<ClassMember> findByClassId(Long classId);
    List<ClassMember> findByClassIdIn(Collection<Long> classIds);
    List<ClassMember> findByStudentUserId(Long studentUserId);
    Optional<ClassMember> findByClassIdAndStudentUserId(Long classId, Long studentUserId);
    Optional<ClassMember> findFirstByStudentUserId(Long studentUserId);
    long countByClassId(Long classId);
    boolean existsByClassId(Long classId);
}

package com.opencode.teachingplatform.plagiarism.repository;

import com.opencode.teachingplatform.plagiarism.entity.PlagiarismTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 查重任务仓储。
 *
 * <p>接口本身很薄，但定义了按学生、按业务键定位查重任务的标准访问入口。</p>
 */
public interface PlagiarismTaskRepository extends JpaRepository<PlagiarismTask, Long> {
    List<PlagiarismTask> findBySchoolId(Long schoolId);
    List<PlagiarismTask> findBySchoolIdAndStudentId(Long schoolId, Long studentId);
    Optional<PlagiarismTask> findByIdAndSchoolId(Long id, Long schoolId);
    Optional<PlagiarismTask> findByBusinessTypeAndBusinessIdAndSchoolId(
            com.opencode.teachingplatform.grading.BusinessType businessType, Long businessId, Long schoolId);
}

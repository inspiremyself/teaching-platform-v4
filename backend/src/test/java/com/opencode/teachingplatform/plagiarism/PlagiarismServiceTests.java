package com.opencode.teachingplatform.plagiarism;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.grading.BusinessType;
import com.opencode.teachingplatform.plagiarism.PlagiarismStatus;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.plagiarism.entity.PlagiarismTask;
import com.opencode.teachingplatform.plagiarism.repository.PlagiarismTaskRepository;
import com.opencode.teachingplatform.plagiarism.service.PlagiarismService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PlagiarismService} 查重详情归属校验单元测试。
 *
 * <p>覆盖教师本人 / 跨教师 / 校管 / 学生本人 / 学生跨人五类归属；
 * 教师归属通过 {@link JdbcTemplate} 的计数查询模拟，非教师角色不会走到该校验。</p>
 */
@ExtendWith(MockitoExtension.class)
class PlagiarismServiceTests {

    private static final Long TASK_ID = 1L;
    private static final Long BUSINESS_ID = 10L;
    private static final Long STUDENT_ID = 2L;

    @Mock
    private PlagiarismTaskRepository plagiarismTaskRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private PlagiarismService plagiarismService;

    @Test
    void teacherCanSeeOwnTaskDetail() {
        when(plagiarismTaskRepository.findByIdAndSchoolId(TASK_ID, 1L)).thenReturn(Optional.of(homeworkTask()));
        // 归属查询命中：businessId 10 + 教师 id 1 → 计数 1
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(BUSINESS_ID), eq(1L), eq(1L))).thenReturn(1);

        Map<String, Object> detail = plagiarismService.getTaskDetail(teacher(1L), TASK_ID);

        assertEquals(TASK_ID, detail.get("taskId"));
        assertEquals("HOMEWORK", detail.get("businessType"));
        assertEquals(BUSINESS_ID, detail.get("businessId"));
        assertEquals(STUDENT_ID, detail.get("studentId"));
        assertEquals(55.0D, ((Number) detail.get("similarityRate")).doubleValue());
        assertEquals("HIGH", detail.get("riskLevel"));
    }

    @Test
    void teacherCannotSeeOtherTeachersTaskDetail() {
        when(plagiarismTaskRepository.findByIdAndSchoolId(TASK_ID, 1L)).thenReturn(Optional.of(homeworkTask()));
        // 归属查询未命中：任务属于教师 1，当前教师 9 的计数为 0
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(BUSINESS_ID), eq(9L), eq(1L))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> plagiarismService.getTaskDetail(teacher(9L), TASK_ID)
        );

        assertEquals(40300, exception.getCode());
        assertEquals("无权限访问该查重记录", exception.getMessage());
    }

    @Test
    void schoolAdminCannotSeeTaskDetail() {
        when(plagiarismTaskRepository.findByIdAndSchoolId(TASK_ID, 1L)).thenReturn(Optional.of(homeworkTask()));

        // 校管在角色分支直接拒绝；等价验证 teacherOwnsTask 对非教师不会放行——
        // 它只接受 TEACHER，且此路径下连归属查询都不会发起。
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> plagiarismService.getTaskDetail(
                        new CurrentUser(1L, "admin9001", "演示校管", UserRole.SCHOOL_ADMIN, 1L, "admin-session"),
                        TASK_ID
                )
        );

        assertEquals(40300, exception.getCode());
        assertEquals("无权限访问该查重记录", exception.getMessage());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void studentCanSeeOwnTaskDetail() {
        when(plagiarismTaskRepository.findByIdAndSchoolId(TASK_ID, 1L)).thenReturn(Optional.of(homeworkTask()));

        Map<String, Object> detail = plagiarismService.getTaskDetail(student(STUDENT_ID), TASK_ID);

        assertEquals(TASK_ID, detail.get("taskId"));
        assertEquals(STUDENT_ID, detail.get("studentId"));
        // 学生本人可见，且不会发起教师归属计数查询
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Integer.class), any(), any(), any());
    }

    @Test
    void studentCannotSeeOtherStudentsTaskDetail() {
        when(plagiarismTaskRepository.findByIdAndSchoolId(TASK_ID, 1L)).thenReturn(Optional.of(homeworkTask()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> plagiarismService.getTaskDetail(student(99L), TASK_ID)
        );

        assertEquals(40300, exception.getCode());
        assertEquals("无权限访问该查重记录", exception.getMessage());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void missingTaskReturnsNotFound() {
        when(plagiarismTaskRepository.findByIdAndSchoolId(TASK_ID, 1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> plagiarismService.getTaskDetail(teacher(1L), TASK_ID)
        );

        assertEquals(40400, exception.getCode());
        assertEquals("查重任务不存在", exception.getMessage());
    }

    private PlagiarismTask homeworkTask() {
        PlagiarismTask task = new PlagiarismTask() {
            @Override
            public Long getId() {
                return TASK_ID;
            }
        };
        task.setBusinessType(BusinessType.HOMEWORK);
        task.setBusinessId(BUSINESS_ID);
        task.setStudentId(STUDENT_ID);
        task.setStatus(PlagiarismStatus.COMPLETED);
        task.setSimilarityRate(55.0D);
        task.setTopMatchSummary("最高相似来源: 体系结构分析作业#-1 (55.0%)");
        return task;
    }

    private CurrentUser teacher(Long id) {
        return new CurrentUser(id, "t9001", "演示教师", UserRole.TEACHER);
    }

    private CurrentUser student(Long id) {
        return new CurrentUser(id, "2026000" + id, "测试学生", UserRole.STUDENT);
    }
}

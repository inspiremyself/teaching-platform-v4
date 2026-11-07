package com.opencode.teachingplatform.plagiarism;

import com.opencode.teachingplatform.common.enums.BusinessType;
import com.opencode.teachingplatform.common.enums.PlagiarismStatus;
import com.opencode.teachingplatform.plagiarism.entity.PlagiarismTask;
import com.opencode.teachingplatform.plagiarism.repository.PlagiarismTaskRepository;
import com.opencode.teachingplatform.plagiarism.service.PlagiarismTaskWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlagiarismTaskWriter} 查重任务统一写入器单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PlagiarismTaskWriterTests {

    private static final Long BUSINESS_ID = 10L;
    private static final Long STUDENT_ID = 2L;

    @Mock
    private PlagiarismTaskRepository repository;

    @InjectMocks
    private PlagiarismTaskWriter writer;

    @Test
    void createsCompletedTaskWhenAbsent() {
        when(repository.findByBusinessTypeAndBusinessId(BusinessType.LAB, BUSINESS_ID))
                .thenReturn(Optional.empty());
        when(repository.save(any(PlagiarismTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        writer.write(BusinessType.LAB, BUSINESS_ID, STUDENT_ID, 42.5D, "最高相似来源: 实验#1", "{\"algorithm\":\"jaccard\"}");

        ArgumentCaptor<PlagiarismTask> captor = ArgumentCaptor.forClass(PlagiarismTask.class);
        verify(repository).save(captor.capture());
        PlagiarismTask saved = captor.getValue();
        assertEquals(BusinessType.LAB, saved.getBusinessType());
        assertEquals(BUSINESS_ID, saved.getBusinessId());
        assertEquals(STUDENT_ID, saved.getStudentId());
        assertEquals(PlagiarismStatus.COMPLETED, saved.getStatus());
        assertEquals(42.5D, saved.getSimilarityRate());
        assertEquals("最高相似来源: 实验#1", saved.getTopMatchSummary());
    }

    @Test
    void updatesExistingTaskInPlace() {
        PlagiarismTask existing = new PlagiarismTask();
        existing.setBusinessType(BusinessType.LAB);
        existing.setBusinessId(BUSINESS_ID);
        existing.setSimilarityRate(10D);
        when(repository.findByBusinessTypeAndBusinessId(BusinessType.LAB, BUSINESS_ID))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(PlagiarismTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        writer.write(BusinessType.LAB, BUSINESS_ID, STUDENT_ID, 88.8D, "新摘要", "{}");

        ArgumentCaptor<PlagiarismTask> captor = ArgumentCaptor.forClass(PlagiarismTask.class);
        verify(repository).save(captor.capture());
        assertSame(existing, captor.getValue());
        assertEquals(88.8D, existing.getSimilarityRate());
        assertEquals(PlagiarismStatus.COMPLETED, existing.getStatus());
    }

    @Test
    void truncatesSummaryToColumnLimit() {
        when(repository.findByBusinessTypeAndBusinessId(BusinessType.HOMEWORK, BUSINESS_ID))
                .thenReturn(Optional.empty());
        when(repository.save(any(PlagiarismTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        String longSummary = "相".repeat(300);

        writer.write(BusinessType.HOMEWORK, BUSINESS_ID, STUDENT_ID, 50D, longSummary, "{}");

        ArgumentCaptor<PlagiarismTask> captor = ArgumentCaptor.forClass(PlagiarismTask.class);
        verify(repository).save(captor.capture());
        assertEquals(255, captor.getValue().getTopMatchSummary().length());
    }
}

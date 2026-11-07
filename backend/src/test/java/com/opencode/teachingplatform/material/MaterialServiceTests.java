package com.opencode.teachingplatform.material;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.material.MaterialVisibility;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.file.LocalFileStorageService;
import com.opencode.teachingplatform.material.dto.MaterialRequests.UploadMaterialRequest;
import com.opencode.teachingplatform.material.entity.CourseMaterial;
import com.opencode.teachingplatform.material.repository.CourseMaterialRepository;
import com.opencode.teachingplatform.material.service.MaterialService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MaterialService} 学校维度数据隔离单元测试。
 */
@ExtendWith(MockitoExtension.class)
class MaterialServiceTests {

    private static final Long SCHOOL_ID = 1L;

    @Mock
    private CourseMaterialRepository courseMaterialRepository;

    @Mock
    private LocalFileStorageService localFileStorageService;

    @InjectMocks
    private MaterialService materialService;

    @Test
    void studentListsOnlyVisibleMaterialsInOwnSchool() {
        when(courseMaterialRepository.findBySchoolIdAndVisibilityOrderByIdDesc(SCHOOL_ID, MaterialVisibility.ALL))
                .thenReturn(List.of(material(1L, SCHOOL_ID, MaterialVisibility.ALL)));

        List<Map<String, Object>> result = materialService.listMaterials(
                new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT, SCHOOL_ID, "session"));

        assertEquals(1, result.size());
        verify(courseMaterialRepository)
                .findBySchoolIdAndVisibilityOrderByIdDesc(SCHOOL_ID, MaterialVisibility.ALL);
    }

    @Test
    void teacherListsOnlyOwnMaterialsInOwnSchool() {
        when(courseMaterialRepository.findBySchoolIdAndUploaderUserIdOrderByIdDesc(SCHOOL_ID, 1L))
                .thenReturn(List.of(material(1L, SCHOOL_ID, MaterialVisibility.ALL)));

        List<Map<String, Object>> result = materialService.listMaterials(
                new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER, SCHOOL_ID, "session"));

        assertEquals(1, result.size());
        verify(courseMaterialRepository)
                .findBySchoolIdAndUploaderUserIdOrderByIdDesc(SCHOOL_ID, 1L);
    }

    @Test
    void schoolAdminListsAllMaterialsInOwnSchool() {
        when(courseMaterialRepository.findBySchoolIdOrderByIdDesc(SCHOOL_ID))
                .thenReturn(List.of(material(1L, SCHOOL_ID, MaterialVisibility.TEACHER)));

        List<Map<String, Object>> result = materialService.listMaterials(
                new CurrentUser(3L, "admin9001", "演示校管", UserRole.SCHOOL_ADMIN, SCHOOL_ID, "session"));

        assertEquals(1, result.size());
        verify(courseMaterialRepository).findBySchoolIdOrderByIdDesc(SCHOOL_ID);
    }

    @Test
    void crossSchoolDownloadIsForbidden() {
        when(courseMaterialRepository.findById(9L))
                .thenReturn(Optional.of(material(9L, 2L, MaterialVisibility.ALL)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> materialService.downloadMaterial(
                        new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT, SCHOOL_ID, "session"), 9L));

        assertEquals(40300, exception.getCode());
    }

    @Test
    void uploadWithoutSchoolIsRejected() {
        UploadMaterialRequest request = new UploadMaterialRequest();
        request.setFile(new MockMultipartFile("file", "a.txt", "text/plain", "bytes".getBytes()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> materialService.uploadMaterial(
                        new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER, null, "session"), request));

        assertEquals(40000, exception.getCode());
        assertEquals("账号未绑定学校，无法上传资料", exception.getMessage());
    }

    @Test
    void uploadWritesCurrentUserSchoolId() {
        when(localFileStorageService.store(anyString(), any())).thenReturn("materials/a.txt");
        when(courseMaterialRepository.save(any(CourseMaterial.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UploadMaterialRequest request = new UploadMaterialRequest();
        request.setFile(new MockMultipartFile("file", "a.txt", "text/plain", "bytes".getBytes()));

        materialService.uploadMaterial(
                new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER, SCHOOL_ID, "session"), request);

        ArgumentCaptor<CourseMaterial> captor = ArgumentCaptor.forClass(CourseMaterial.class);
        verify(courseMaterialRepository).save(captor.capture());
        assertEquals(SCHOOL_ID, captor.getValue().getSchoolId());
    }

    private CourseMaterial material(Long id, Long schoolId, MaterialVisibility visibility) {
        CourseMaterial material = new CourseMaterial() {
            @Override
            public Long getId() {
                return id;
            }
        };
        material.setTitle("资料");
        material.setCategory("COURSE");
        material.setDescription("");
        material.setFilePath("materials/a.txt");
        material.setFileName("a.txt");
        material.setUploaderUserId(1L);
        material.setVisibility(visibility);
        material.setSchoolId(schoolId);
        return material;
    }
}

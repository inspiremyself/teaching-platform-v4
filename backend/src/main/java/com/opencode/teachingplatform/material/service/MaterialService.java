package com.opencode.teachingplatform.material.service;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.material.MaterialVisibility;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.file.LocalFileStorageService;
import com.opencode.teachingplatform.material.dto.MaterialRequests.UpdateMaterialRequest;
import com.opencode.teachingplatform.material.dto.MaterialRequests.UploadMaterialRequest;
import com.opencode.teachingplatform.material.entity.CourseMaterial;
import com.opencode.teachingplatform.material.repository.CourseMaterialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MaterialService {

    private final CourseMaterialRepository courseMaterialRepository;
    private final LocalFileStorageService localFileStorageService;

    public MaterialService(CourseMaterialRepository courseMaterialRepository, LocalFileStorageService localFileStorageService) {
        this.courseMaterialRepository = courseMaterialRepository;
        this.localFileStorageService = localFileStorageService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMaterials(CurrentUser currentUser) {
        Long schoolId = currentUser.schoolId();
        List<CourseMaterial> materials = switch (currentUser.role()) {
            case TEACHER -> courseMaterialRepository
                    .findBySchoolIdAndUploaderUserIdOrderByIdDesc(schoolId, currentUser.id());
            case SCHOOL_ADMIN -> courseMaterialRepository.findBySchoolIdOrderByIdDesc(schoolId);
            case STUDENT -> courseMaterialRepository
                    .findBySchoolIdAndVisibilityOrderByIdDesc(schoolId, MaterialVisibility.ALL);
            case PLATFORM_ADMIN -> throw new BusinessException(40300, "无权限访问资料");
        };
        return materials.stream().map(this::toView).toList();
    }

    @Transactional
    public Map<String, Object> uploadMaterial(CurrentUser currentUser, UploadMaterialRequest request) {
        if (currentUser.role() != UserRole.TEACHER) {
            throw new BusinessException(40300, "无权限上传资料");
        }
        if (currentUser.schoolId() == null) {
            throw new BusinessException(40000, "账号未绑定学校，无法上传资料");
        }
        String storedPath = localFileStorageService.store("materials", request.getFile());
        String originalFilename = request.getFile().getOriginalFilename();

        CourseMaterial material = new CourseMaterial();
        material.setTitle(defaultIfBlank(request.getTitle(), "未命名资料"));
        material.setCategory(defaultIfBlank(request.getCategory(), "COURSE"));
        material.setDescription(defaultIfBlank(request.getDescription(), ""));
        material.setFilePath(storedPath);
        material.setFileName(originalFilename == null || originalFilename.isBlank() ? "material.bin" : originalFilename);
        material.setUploaderUserId(currentUser.id());
        material.setSchoolId(currentUser.schoolId());
        material.setVisibility(request.getVisibility() == null ? MaterialVisibility.ALL : request.getVisibility());
        CourseMaterial saved = courseMaterialRepository.save(material);
        return toView(saved);
    }

    @Transactional
    public Map<String, Object> updateMaterial(CurrentUser currentUser, Long id, UpdateMaterialRequest request) {
        CourseMaterial material = findOwnedMaterial(currentUser, id);
        if (request.getTitle() != null) {
            material.setTitle(request.getTitle());
        }
        if (request.getCategory() != null) {
            material.setCategory(request.getCategory());
        }
        if (request.getDescription() != null) {
            material.setDescription(request.getDescription());
        }
        if (request.getVisibility() != null) {
            material.setVisibility(request.getVisibility());
        }
        return toView(courseMaterialRepository.save(material));
    }

    @Transactional
    public void deleteMaterial(CurrentUser currentUser, Long id) {
        CourseMaterial material = findOwnedMaterial(currentUser, id);
        courseMaterialRepository.delete(material);
        localFileStorageService.delete(material.getFilePath());
    }

    @Transactional(readOnly = true)
    public DownloadedMaterial downloadMaterial(CurrentUser currentUser, Long id) {
        CourseMaterial material = courseMaterialRepository.findByIdAndSchoolId(id, currentUser.schoolId())
                .orElseThrow(() -> new BusinessException(40400, "资料不存在"));
        if (currentUser.role() == UserRole.TEACHER) {
            if (!Objects.equals(material.getUploaderUserId(), currentUser.id())) {
                throw new BusinessException(40300, "无权限访问该资料");
            }
        } else if (material.getVisibility() != MaterialVisibility.ALL) {
            throw new BusinessException(40300, "无权限访问该资料");
        }
        return new DownloadedMaterial(material.getFileName(), localFileStorageService.read(material.getFilePath()));
    }

    private CourseMaterial findOwnedMaterial(CurrentUser currentUser, Long id) {
        if (currentUser.role() != UserRole.TEACHER) {
            throw new BusinessException(40300, "无权限管理资料");
        }
        CourseMaterial material = courseMaterialRepository.findByIdAndSchoolId(id, currentUser.schoolId())
                .orElseThrow(() -> new BusinessException(40400, "资料不存在"));
        if (!Objects.equals(material.getUploaderUserId(), currentUser.id())) {
            throw new BusinessException(40300, "无权限访问该资料");
        }
        return material;
    }

    private Map<String, Object> toView(CourseMaterial material) {
        // 使用 LinkedHashMap 组装：字段值允许为空（如未填描述），Map.of 会因 null 值抛 NPE
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", material.getId());
        view.put("title", material.getTitle());
        view.put("category", material.getCategory());
        view.put("description", material.getDescription());
        view.put("fileName", material.getFileName());
        view.put("visibility", material.getVisibility().name());
        view.put("uploaderUserId", material.getUploaderUserId());
        view.put("downloadUrl", "/api/v1/materials/" + material.getId() + "/download");
        return view;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record DownloadedMaterial(String fileName, byte[] bytes) {
    }
}

package com.opencode.teachingplatform.material.controller;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.content.service.TaskSubjectValidator;
import com.opencode.teachingplatform.material.dto.MaterialRequests.UpdateMaterialRequest;
import com.opencode.teachingplatform.material.dto.MaterialRequests.UploadMaterialRequest;
import com.opencode.teachingplatform.material.service.MaterialService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/materials")
public class MaterialController {

    private final MaterialService materialService;
    private final TaskSubjectValidator taskSubjectValidator;

    public MaterialController(MaterialService materialService, TaskSubjectValidator taskSubjectValidator) {
        this.materialService = materialService;
        this.taskSubjectValidator = taskSubjectValidator;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','STUDENT','SCHOOL_ADMIN')")
    public ApiResponse<?> list() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(materialService.listMaterials(currentUser));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> create(@ModelAttribute UploadMaterialRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        taskSubjectValidator.requireForCreate(currentUser, request.getSubjectId());
        return ApiResponse.ok(materialService.uploadMaterial(currentUser, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> update(@PathVariable Long id, @RequestBody UpdateMaterialRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        taskSubjectValidator.requireForUpdate(currentUser, request.getSubjectId());
        return ApiResponse.ok(materialService.updateMaterial(currentUser, id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> delete(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        materialService.deleteMaterial(currentUser, id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('TEACHER','STUDENT','SCHOOL_ADMIN')")
    public ResponseEntity<ByteArrayResource> download(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        MaterialService.DownloadedMaterial material = materialService.downloadMaterial(currentUser, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(material.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(new ByteArrayResource(material.bytes()));
    }
}

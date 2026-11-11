package com.opencode.teachingplatform.material.dto;

import com.opencode.teachingplatform.material.MaterialVisibility;
import org.springframework.web.multipart.MultipartFile;

public final class MaterialRequests {

    private MaterialRequests() {
    }

    public static class UploadMaterialRequest {
        private String title;
        private String category;
        private String description;
        private MaterialVisibility visibility;
        private MultipartFile file;
        private Long subjectId;

        public Long getSubjectId() {
            return subjectId;
        }

        public void setSubjectId(Long subjectId) {
            this.subjectId = subjectId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public MaterialVisibility getVisibility() {
            return visibility;
        }

        public void setVisibility(MaterialVisibility visibility) {
            this.visibility = visibility;
        }

        public MultipartFile getFile() {
            return file;
        }

        public void setFile(MultipartFile file) {
            this.file = file;
        }
    }

    public static class UpdateMaterialRequest {
        private String title;
        private String category;
        private String description;
        private MaterialVisibility visibility;
        private Long subjectId;

        public Long getSubjectId() {
            return subjectId;
        }

        public void setSubjectId(Long subjectId) {
            this.subjectId = subjectId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public MaterialVisibility getVisibility() {
            return visibility;
        }

        public void setVisibility(MaterialVisibility visibility) {
            this.visibility = visibility;
        }
    }
}

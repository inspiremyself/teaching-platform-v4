package com.opencode.teachingplatform.material;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Component
public class SeedMaterialInitializer implements ApplicationRunner {

    private final Path storageRoot;

    public SeedMaterialInitializer(@Value("${app.file-storage-root}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        for (String relativePath : List.of("materials/course-intro.txt", "materials/lab-safety.txt")) {
            ensureSeedFile(relativePath);
        }
    }

    private void ensureSeedFile(String relativePath) throws IOException {
        Path targetPath = storageRoot.resolve(relativePath).normalize();
        if (!targetPath.startsWith(storageRoot) || Files.exists(targetPath)) {
            return;
        }
        Files.createDirectories(targetPath.getParent());
        ClassPathResource resource = new ClassPathResource("seed-files/" + relativePath);
        try (InputStream inputStream = resource.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

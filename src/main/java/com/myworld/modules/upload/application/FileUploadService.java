package com.myworld.modules.upload.application;

import com.myworld.core.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Slf4j
@Service
public class FileUploadService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.base-url:http://localhost:8080/api/files}")
    private String baseUrl;

    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp", "application/pdf"
    );

    public String uploadProofFile(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty())
            throw new BadRequestException("File is empty");
        if (file.getSize() > MAX_SIZE)
            throw new BadRequestException("File too large. Max 5MB allowed.");
        String contentType = file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType))
            throw new BadRequestException("Invalid file type. Allowed: JPG, PNG, WebP, PDF");

        try {
            org.apache.tika.Tika tika = new org.apache.tika.Tika();
            String detected = tika.detect(file.getInputStream());
            if (!ALLOWED_TYPES.contains(detected)) {
                throw new BadRequestException("File content does not match declared type.");
            }

            Path dir = Paths.get(uploadDir, "proofs", String.valueOf(userId));
            Files.createDirectories(dir);

            String ext = getExtension(file.getOriginalFilename(), contentType);
            String filename = UUID.randomUUID() + ext;
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String url = baseUrl + "/proofs/" + userId + "/" + filename;
            log.info("File uploaded: userId={} path={}", userId, target);
            return url;
        } catch (IOException e) {
            log.error("File upload failed: {}", e.getMessage());
            throw new BadRequestException("File upload failed. Try again.");
        }
    }

    public byte[] getFile(String relativePath) throws IOException {
        Path path = Paths.get(uploadDir, relativePath);
        if (!path.toAbsolutePath().startsWith(Paths.get(uploadDir).toAbsolutePath()))
            throw new BadRequestException("Invalid path");
        return Files.readAllBytes(path);
    }

    private String getExtension(String filename, String contentType) {
        if (filename != null && filename.contains("."))
            return filename.substring(filename.lastIndexOf('.'));
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> ".bin";
        };
    }
}

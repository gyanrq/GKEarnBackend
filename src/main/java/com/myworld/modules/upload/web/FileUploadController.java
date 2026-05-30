package com.myworld.modules.upload.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.core.security.CustomUserDetails;
import com.myworld.modules.upload.application.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService uploadService;

    /**
     * POST /api/upload/proof
     * Upload lead proof image/PDF. Returns the public URL.
     */
    @PostMapping("/api/upload/proof")
    public ApiResponse<Map<String, String>> uploadProof(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        Long userId = getUserId(auth);
        String url = uploadService.uploadProofFile(file, userId);
        return ApiResponse.success(Map.of("url", url), "File uploaded successfully");
    }

    /**
     * GET /api/files/proofs/{userId}/{filename}
     * Serve uploaded files.
     */
    @GetMapping("/api/files/proofs/{userId}/{filename}")
    public ResponseEntity<byte[]> serveFile(
            @PathVariable String userId,
            @PathVariable String filename,
            Authentication auth) throws IOException {

        CustomUserDetails userDetails = null;
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
            userDetails = (CustomUserDetails) auth.getPrincipal();
        }

        if (userDetails == null) {
            throw new com.myworld.core.exception.ForbiddenException("Access denied");
        }

        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !String.valueOf(userDetails.getId()).equals(userId)) {
            throw new com.myworld.core.exception.ForbiddenException("You do not have permission to access this file.");
        }

        String rel = "proofs/" + userId + "/" + filename;
        byte[] data = uploadService.getFile(rel);
        String ct = probeType(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ct))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(data);
    }

    private Long getUserId(Authentication auth) {
        if (auth == null) return 0L;
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getId();
        }
        return 0L;
    }

    private String probeType(String filename) {
        if (filename.endsWith(".pdf"))  return "application/pdf";
        if (filename.endsWith(".png"))  return "image/png";
        if (filename.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}

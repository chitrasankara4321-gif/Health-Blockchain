package com.healthchain.controller;

import com.healthchain.annotation.RequireAuth;
import com.healthchain.annotation.RequireRole;
import com.healthchain.service.DataService;
import com.healthchain.util.CryptoUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emergency and misc controller - replaces emergency_access(), upload_file(), health_check()
 */
@RestController
@RequestMapping("/api")
public class EmergencyController {

    @Autowired
    private DataService dataService;

    /**
     * Emergency access protocol - requires justification
     * Replaces: @app.route('/api/emergency/access', methods=['POST'])
     */
    @PostMapping("/emergency/access")
    @RequireAuth
    @RequireRole("doctor")
    public ResponseEntity<Map<String, Object>> emergencyAccess(@RequestBody Map<String, String> data,
                                                                HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String userId = (String) session.getAttribute("user_id");

        String patientId = data.get("patient_id");
        String reason = data.getOrDefault("reason", "");
        String urgency = data.getOrDefault("urgency", "medium");

        // Log emergency access request
        dataService.logAccess("emergency_access_request", userId, null, patientId,
                "Emergency access: " + reason + " (Urgency: " + urgency + ")",
                request.getRemoteAddr());

        // In a real system, this would:
        // 1. Notify hospital administrators
        // 2. Require additional approval
        // 3. Create high-priority audit trail
        // 4. Grant temporary access

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Emergency access granted");
        response.put("access_granted_until",
                LocalDateTime.now().plusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return ResponseEntity.ok(response);
    }

    /**
     * Secure file upload with authentication (legacy endpoint)
     * Replaces: @app.route('/api/upload', methods=['POST'])
     */
    @PostMapping("/upload")
    @RequireAuth
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file,
                                                           HttpServletRequest request) {
        try {
            if (file.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "No file provided");
                return ResponseEntity.badRequest().body(error);
            }

            HttpSession session = request.getSession(false);
            String userId = (String) session.getAttribute("user_id");

            // Read file data
            byte[] fileData = file.getBytes();

            // Encrypt the file data
            CryptoUtil cryptoUtil = dataService.getCryptoUtil();
            Map<String, Object> encryptedResult = cryptoUtil.encryptWithIntegrity(fileData);
            byte[] encryptedData = (byte[]) encryptedResult.get("encrypted_data");

            // Generate hash of encrypted data
            String fileHash = cryptoUtil.hashData(encryptedData);

            // Store encrypted file
            String filePath = "storage/" + fileHash + ".enc";
            java.nio.file.Files.write(java.nio.file.Paths.get(filePath), encryptedData);

            // In a real system, we'd link this file to a patient in the database
            dataService.logAccess("file_upload", userId, fileHash, null,
                    "Uploaded file: " + file.getOriginalFilename() + " (" + fileData.length + " bytes)",
                    request.getRemoteAddr());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "File encrypted & stored securely");
            response.put("hash", fileHash);
            response.put("uploaded_by", userId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Upload failed: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Health check endpoint
     * Replaces: @app.route('/api/health', methods=['GET'])
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "healthy");
        response.put("secure", true);
        return ResponseEntity.ok(response);
    }
}

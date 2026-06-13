package com.healthchain.controller;

import com.healthchain.annotation.RequireAuth;
import com.healthchain.annotation.RequireRole;
import com.healthchain.service.DataService;
import com.healthchain.util.CryptoUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Patient controller - replaces all patient-related endpoints from secure_server.py
 */
@RestController
@RequestMapping("/api/patient")
public class PatientController {

    @Autowired
    private DataService dataService;

    /**
     * Get patient's own uploaded files
     * Replaces: @app.route('/api/patient/my_files', methods=['GET'])
     */
    @GetMapping("/my_files")
    @RequireAuth
    @RequireRole("patient")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getMyFiles(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            String patientId = (String) session.getAttribute("user_id");

            List<Map<String, Object>> patientFiles = new ArrayList<>();
            File storageDir = new File("storage");

            if (storageDir.exists()) {
                for (File file : storageDir.listFiles()) {
                    if (file.getName().endsWith(".enc")) {
                        String fileHash = file.getName().replace(".enc", "");
                        File metaFile = new File("storage/" + fileHash + ".meta");

                        if (metaFile.exists()) {
                            try {
                                Map<String, Object> metadata = dataService.getCryptoUtil() != null ?
                                        new com.fasterxml.jackson.databind.ObjectMapper().readValue(metaFile,
                                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}) : null;

                                if (metadata == null) continue;

                                // Only include if this file belongs to current patient
                                if (!patientId.equals(metadata.get("patient_id"))) {
                                    continue;
                                }

                                String originalFilename = (String) metadata.get("original_filename");

                                Map<String, Object> fileInfo = new LinkedHashMap<>();
                                fileInfo.put("hash", fileHash);
                                fileInfo.put("filename", originalFilename != null ? originalFilename : "medical_record_" + fileHash.substring(0, 8));
                                fileInfo.put("size", file.length());

                                BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                                LocalDateTime createTime = LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault());
                                fileInfo.put("upload_date", createTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                                fileInfo.put("uploaded_by", patientId);

                                patientFiles.add(fileInfo);
                            } catch (Exception e) {
                                continue;
                            }
                        }
                        // Skip files without metadata (matches Python's 'continue')
                    }
                }
            }

            dataService.logAccess("view_own_files", patientId, null, null,
                    "Viewed " + patientFiles.size() + " own files", request.getRemoteAddr());

            Map<String, Object> response = new HashMap<>();
            response.put("files", patientFiles);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to load files: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Patient uploads their own medical file - accepts all file types
     * Replaces: @app.route('/api/patient/upload', methods=['POST'])
     */
    @PostMapping("/upload")
    @RequireAuth
    @RequireRole("patient")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file,
                                                          HttpServletRequest request) {
        try {
            if (file.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "No file provided");
                return ResponseEntity.badRequest().body(error);
            }

            HttpSession session = request.getSession(false);
            String patientId = (String) session.getAttribute("user_id");
            String originalFilename = file.getOriginalFilename();

            // Read file data
            byte[] fileData = file.getBytes();

            // Encrypt the file data with simple encryption
            CryptoUtil cryptoUtil = dataService.getCryptoUtil();
            byte[] encryptedData = cryptoUtil.encryptSimple(fileData);

            // Add blockchain metadata
            Map<String, Object> blockchainMetadata = new LinkedHashMap<>();
            blockchainMetadata.put("patient_id", patientId);
            String uploadTimestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            blockchainMetadata.put("upload_timestamp", uploadTimestamp);
            blockchainMetadata.put("original_filename", originalFilename);
            blockchainMetadata.put("file_hash", cryptoUtil.hashData(fileData));
            blockchainMetadata.put("encrypted_hash", cryptoUtil.hashData(encryptedData));
            blockchainMetadata.put("file_size", fileData.length);
            blockchainMetadata.put("mime_type", file.getContentType() != null ? file.getContentType() : "application/octet-stream");

            // Generate final hash (matching Python: hashlib.sha256(encrypted_data + metadata_json.encode()).hexdigest())
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            String metadataJson = mapper.writeValueAsString(new TreeMap<>(blockchainMetadata));

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(encryptedData);
            digest.update(metadataJson.getBytes());
            byte[] finalHashBytes = digest.digest();
            String finalHash = bytesToHex(finalHashBytes);

            // Store encrypted file
            String filePath = "storage/" + finalHash + ".enc";
            Files.write(Paths.get(filePath), encryptedData);

            // Store metadata
            String metadataPath = "storage/" + finalHash + ".meta";
            mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(new File(metadataPath), blockchainMetadata);

            dataService.logAccess("file_upload", patientId, finalHash, null,
                    "Uploaded file: " + originalFilename + " (" + CryptoUtil.formatFileSize(fileData.length) + ")",
                    request.getRemoteAddr());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "File uploaded and encrypted successfully");

            Map<String, Object> fileInfo = new LinkedHashMap<>();
            fileInfo.put("hash", finalHash);
            fileInfo.put("filename", originalFilename);
            fileInfo.put("size", fileData.length);
            fileInfo.put("upload_date", uploadTimestamp);
            response.put("file_info", fileInfo);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Upload failed: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Patient downloads their own file
     * Replaces: @app.route('/api/patient/download/<file_hash>', methods=['GET'])
     */
    @GetMapping("/download/{fileHash}")
    @RequireAuth
    @RequireRole("patient")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> downloadFile(@PathVariable String fileHash, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String patientId = (String) session.getAttribute("user_id");

        String filePath = "storage/" + fileHash + ".enc";

        if (!new File(filePath).exists()) {
            dataService.logAccess("file_not_found", patientId, fileHash, null,
                    "File not found", request.getRemoteAddr());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "File not found");
            return ResponseEntity.status(404).body(error);
        }

        try {
            // Read encrypted file
            byte[] encryptedData = Files.readAllBytes(Paths.get(filePath));

            // Verify blockchain metadata
            String metadataPath = "storage/" + fileHash + ".meta";
            String originalFilename = "medical_record_" + fileHash.substring(0, 8);

            if (new File(metadataPath).exists()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> metadata = mapper.readValue(new File(metadataPath),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

                // Verify patient ownership
                if (!patientId.equals(metadata.get("patient_id"))) {
                    dataService.logAccess("unauthorized_access", patientId, fileHash, null,
                            "Unauthorized file access", request.getRemoteAddr());
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "Access denied");
                    return ResponseEntity.status(403).body(error);
                }

                // Get original filename
                if (metadata.get("original_filename") != null) {
                    originalFilename = (String) metadata.get("original_filename");
                }
            }

            // Decrypt the data (using simple decryption, matching Python logic)
            byte[] decryptedData = dataService.getCryptoUtil().decryptSimple(encryptedData);

            dataService.logAccess("file_download", patientId, fileHash, null,
                    "Patient downloaded own file", request.getRemoteAddr());

            // Return the decrypted file with original filename
            ByteArrayResource resource = new ByteArrayResource(decryptedData);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + originalFilename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(decryptedData.length)
                    .body(resource);

        } catch (Exception e) {
            dataService.logAccess("file_download_error", patientId, fileHash, null,
                    e.getMessage(), request.getRemoteAddr());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to download file");
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Patient deletes their own uploaded file
     * Replaces: @app.route('/api/patient/delete-file', methods=['POST'])
     */
    @PostMapping("/delete-file")
    @RequireAuth
    @RequireRole("patient")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> deleteFile(@RequestBody Map<String, String> data,
                                                          HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String patientId = (String) session.getAttribute("user_id");
        String fileHash = data.get("file_hash");

        if (fileHash == null || fileHash.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "File hash required");
            return ResponseEntity.badRequest().body(error);
        }

        String filePath = "storage/" + fileHash + ".enc";
        String metadataPath = "storage/" + fileHash + ".meta";

        if (!new File(filePath).exists()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "File not found");
            return ResponseEntity.status(404).body(error);
        }

        try {
            // Verify patient ownership
            if (new File(metadataPath).exists()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> metadata = mapper.readValue(new File(metadataPath),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

                if (!patientId.equals(metadata.get("patient_id"))) {
                    dataService.logAccess("unauthorized_delete", patientId, fileHash, null,
                            "Unauthorized file deletion", request.getRemoteAddr());
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "Access denied");
                    return ResponseEntity.status(403).body(error);
                }

                String originalFilename = (String) metadata.getOrDefault("original_filename", "medical_record_" + fileHash.substring(0, 8));

                // Delete the encrypted file and metadata
                new File(filePath).delete();
                new File(metadataPath).delete();

                dataService.logAccess("file_deleted", patientId, fileHash, null,
                        "Deleted file: " + originalFilename, request.getRemoteAddr());

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("success", true);
                response.put("message", "File deleted successfully");
                response.put("deleted_file", fileHash);
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "File metadata not found");
                return ResponseEntity.status(404).body(error);
            }

        } catch (Exception e) {
            dataService.logAccess("file_delete_error", patientId, fileHash, null,
                    e.getMessage(), request.getRemoteAddr());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to delete file");
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Get pending access requests for patient
     * Replaces: @app.route('/api/patient/access_requests', methods=['GET'])
     */
    @GetMapping("/access_requests")
    @RequireAuth
    @RequireRole("patient")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getAccessRequests(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            String patientId = (String) session.getAttribute("user_id");

            Map<String, Object> requests = dataService.loadAccessRequests();
            List<Map<String, Object>> pending = (List<Map<String, Object>>) requests.get("pending");

            List<Map<String, Object>> pendingRequests = new ArrayList<>();
            Map<String, Object> users = dataService.loadUsers();
            Map<String, Object> doctors = (Map<String, Object>) users.get("doctors");

            for (Map<String, Object> req : pending) {
                if (patientId.equals(req.get("patient_id"))) {
                    // Find doctor details
                    Map<String, Object> doctorInfo = null;
                    if (doctors != null) {
                        for (Map.Entry<String, Object> entry : doctors.entrySet()) {
                            Map<String, Object> doctor = (Map<String, Object>) entry.getValue();
                            if (req.get("doctor_id").equals(doctor.get("id"))) {
                                doctorInfo = doctor;
                                break;
                            }
                        }
                    }

                    if (doctorInfo != null) {
                        Map<String, Object> enrichedReq = new LinkedHashMap<>(req);
                        enrichedReq.put("doctor_name", doctorInfo.get("name"));
                        enrichedReq.put("doctor_department", doctorInfo.getOrDefault("department", ""));
                        enrichedReq.put("doctor_license", doctorInfo.getOrDefault("license", ""));
                        pendingRequests.add(enrichedReq);
                    }
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("requests", pendingRequests);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to load access requests: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Patient responds to access request
     * Replaces: @app.route('/api/patient/respond_request', methods=['POST'])
     */
    @PostMapping("/respond_request")
    @RequireAuth
    @RequireRole("patient")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> respondToRequest(@RequestBody Map<String, String> data,
                                                                 HttpServletRequest request) {
        try {
            String requestId = data.get("request_id");
            String action = data.get("action"); // 'approve' or 'reject'

            HttpSession session = request.getSession(false);
            String patientId = (String) session.getAttribute("user_id");

            Map<String, Object> requests = dataService.loadAccessRequests();
            List<Map<String, Object>> pending = (List<Map<String, Object>>) requests.get("pending");

            boolean requestFound = false;

            for (int i = 0; i < pending.size(); i++) {
                Map<String, Object> req = pending.get(i);
                if (requestId.equals(req.get("id")) && patientId.equals(req.get("patient_id"))) {
                    requestFound = true;
                    req.put("status", action);
                    req.put("responded_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                    if ("approve".equals(action)) {
                        // Add to assignments
                        Map<String, Object> assignments = dataService.loadAssignments();
                        String doctorId = (String) req.get("doctor_id");

                        List<String> patientList;
                        Object existing = assignments.get(doctorId);
                        if (existing instanceof List) {
                            patientList = new ArrayList<>((List<String>) existing);
                        } else {
                            patientList = new ArrayList<>();
                        }

                        if (!patientList.contains(req.get("patient_id"))) {
                            patientList.add((String) req.get("patient_id"));
                        }
                        assignments.put(doctorId, patientList);
                        dataService.saveAssignments(assignments);

                        ((List<Map<String, Object>>) requests.get("approved")).add(req);
                        dataService.logAccess("access_approved", patientId, null, (String) req.get("patient_id"),
                                "Approved access for doctor " + req.get("doctor_id"), request.getRemoteAddr());
                    } else {
                        ((List<Map<String, Object>>) requests.get("rejected")).add(req);
                        dataService.logAccess("access_rejected", patientId, null, (String) req.get("patient_id"),
                                "Rejected access for doctor " + req.get("doctor_id"), request.getRemoteAddr());
                    }

                    // Remove from pending
                    pending.remove(i);
                    break;
                }
            }

            if (!requestFound) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Request not found");
                return ResponseEntity.status(404).body(error);
            }

            dataService.saveAccessRequests(requests);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Access request " + action + "d successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to process request: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Patient revokes doctor's access to their files
     * Replaces: @app.route('/api/patient/revoke-access', methods=['POST'])
     */
    @PostMapping("/revoke-access")
    @RequireAuth
    @RequireRole("patient")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> revokeAccess(@RequestBody Map<String, String> data,
                                                             HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            String patientId = (String) session.getAttribute("user_id");
            String doctorId = data.get("doctor_id");

            if (doctorId == null || doctorId.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Doctor ID required");
                return ResponseEntity.badRequest().body(error);
            }

            Map<String, Object> assignments = dataService.loadAssignments();

            Object existing = assignments.get(doctorId);
            List<String> patientList;
            if (existing instanceof List) {
                patientList = new ArrayList<>((List<String>) existing);
            } else {
                patientList = new ArrayList<>();
            }

            // Check if doctor has access to this patient
            if (!patientList.contains(patientId)) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Doctor does not have access to your files");
                return ResponseEntity.badRequest().body(error);
            }

            // Remove patient from doctor's assigned list
            patientList.remove(patientId);
            assignments.put(doctorId, patientList);
            dataService.saveAssignments(assignments);

            dataService.logAccess("access_revoked", patientId, null, doctorId,
                    "Patient revoked access from doctor " + doctorId, request.getRemoteAddr());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Access revoked successfully");
            response.put("revoked_doctor", doctorId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to revoke access: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Get list of doctors who have access to patient's files
     * Replaces: @app.route('/api/patient/granted-doctors', methods=['GET'])
     */
    @GetMapping("/granted-doctors")
    @RequireAuth
    @RequireRole("patient")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getGrantedDoctors(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            String patientId = (String) session.getAttribute("user_id");

            Map<String, Object> assignments = dataService.loadAssignments();
            Map<String, Object> users = dataService.loadUsers();
            Map<String, Object> doctorsMap = (Map<String, Object>) users.get("doctors");

            List<Map<String, Object>> grantedDoctors = new ArrayList<>();

            // Find all doctors who have access to this patient
            for (Map.Entry<String, Object> entry : assignments.entrySet()) {
                String doctorId = entry.getKey();
                List<String> patientList;
                if (entry.getValue() instanceof List) {
                    patientList = (List<String>) entry.getValue();
                } else {
                    continue;
                }

                if (patientList.contains(patientId)) {
                    // Find doctor details
                    if (doctorsMap != null) {
                        for (Map.Entry<String, Object> docEntry : doctorsMap.entrySet()) {
                            Map<String, Object> doctor = (Map<String, Object>) docEntry.getValue();
                            if (doctorId.equals(doctor.get("id"))) {
                                Map<String, Object> doctorInfo = new LinkedHashMap<>();
                                doctorInfo.put("id", doctor.get("id"));
                                doctorInfo.put("name", doctor.get("name"));
                                doctorInfo.put("email", doctor.get("email"));
                                doctorInfo.put("department", doctor.getOrDefault("department", "N/A"));
                                doctorInfo.put("specialization", doctor.getOrDefault("specialization", "N/A"));
                                grantedDoctors.add(doctorInfo);
                                break;
                            }
                        }
                    }
                }
            }

            dataService.logAccess("view_granted_doctors", patientId, null, null,
                    "Viewed " + grantedDoctors.size() + " doctors with access", request.getRemoteAddr());

            Map<String, Object> response = new HashMap<>();
            response.put("doctors", grantedDoctors);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to load granted doctors: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

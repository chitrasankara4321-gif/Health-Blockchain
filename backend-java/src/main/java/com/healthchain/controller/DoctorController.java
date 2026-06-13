package com.healthchain.controller;

import com.healthchain.annotation.RequireAuth;
import com.healthchain.annotation.RequireRole;
import com.healthchain.service.DataService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Doctor controller - replaces all doctor-related endpoints from secure_server.py
 */
@RestController
@RequestMapping("/api/doctor")
public class DoctorController {

    @Autowired
    private DataService dataService;

    /**
     * Get patients assigned to current doctor
     * Replaces: @app.route('/api/doctor/patients', methods=['GET'])
     */
    @GetMapping("/patients")
    @RequireAuth
    @RequireRole("doctor")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getAssignedPatients(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            String doctorId = (String) session.getAttribute("user_id");

            Map<String, Object> assignments = dataService.loadAssignments();
            Map<String, Object> users = dataService.loadUsers();

            Object patientIdsObj = assignments.get(doctorId);
            List<String> patientIds;
            if (patientIdsObj instanceof List) {
                patientIds = (List<String>) patientIdsObj;
            } else {
                patientIds = new ArrayList<>();
            }

            List<Map<String, Object>> patients = new ArrayList<>();
            Map<String, Object> patientsMap = (Map<String, Object>) users.get("patients");

            for (String patientId : patientIds) {
                if (patientsMap != null) {
                    for (Map.Entry<String, Object> entry : patientsMap.entrySet()) {
                        Map<String, Object> patient = (Map<String, Object>) entry.getValue();
                        if (patientId.equals(patient.get("id"))) {
                            patients.add(patient);
                            break;
                        }
                    }
                }
            }

            dataService.logAccess("view_patients", doctorId, null, null,
                    "Viewed " + patients.size() + " assigned patients", request.getRemoteAddr());

            Map<String, Object> response = new HashMap<>();
            response.put("patients", patients);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to load patients: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Get files for a specific patient (if doctor has access)
     * Replaces: @app.route('/api/doctor/patient/<patient_id>/files', methods=['GET'])
     */
    @GetMapping("/patient/{patientId}/files")
    @RequireAuth
    @RequireRole("doctor")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getPatientFiles(@PathVariable String patientId,
                                                                HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            String doctorId = (String) session.getAttribute("user_id");

            Map<String, Object> assignments = dataService.loadAssignments();

            // Check if doctor is assigned to this patient
            Object patientIdsObj = assignments.get(doctorId);
            List<String> assignedPatients;
            if (patientIdsObj instanceof List) {
                assignedPatients = (List<String>) patientIdsObj;
            } else {
                assignedPatients = new ArrayList<>();
            }

            if (!assignedPatients.contains(patientId)) {
                dataService.logAccess("unauthorized_access", doctorId, null, patientId,
                        "Attempted to access unassigned patient", request.getRemoteAddr());
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Access denied");
                return ResponseEntity.status(403).body(error);
            }

            // Get patient files from storage
            List<Map<String, Object>> patientFiles = new ArrayList<>();
            File storageDir = new File("storage");

            if (storageDir.exists() && storageDir.listFiles() != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

                for (File file : storageDir.listFiles()) {
                    if (file.getName().endsWith(".enc")) {
                        String fileHash = file.getName().replace(".enc", "");
                        File metaFile = new File("storage/" + fileHash + ".meta");

                        String filePatientId = null;
                        String actualHash = fileHash;
                        String originalFilename = null;

                        if (metaFile.exists()) {
                            try {
                                Map<String, Object> metadata = mapper.readValue(metaFile,
                                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                                filePatientId = (String) metadata.get("patient_id");
                                // Use the hash from metadata for consistency
                                if (metadata.get("file_hash") != null) {
                                    actualHash = (String) metadata.get("file_hash");
                                }
                                originalFilename = (String) metadata.get("original_filename");
                            } catch (Exception e) {
                                continue;
                            }
                        }

                        // Only include files that belong to this patient
                        if (patientId.equals(filePatientId)) {
                            Map<String, Object> fileInfo = new LinkedHashMap<>();
                            fileInfo.put("hash", actualHash);
                            fileInfo.put("filename", originalFilename != null ? originalFilename : "medical_record_" + actualHash.substring(0, 8));
                            fileInfo.put("size", file.length());

                            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                            LocalDateTime createTime = LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault());
                            fileInfo.put("upload_date", createTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                            patientFiles.add(fileInfo);
                        }
                    }
                }
            }

            dataService.logAccess("view_patient_files", doctorId, null, patientId,
                    "Viewed " + patientFiles.size() + " files", request.getRemoteAddr());

            Map<String, Object> response = new HashMap<>();
            response.put("files", patientFiles);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to load patient files: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Doctors are not allowed to download files - view only access
     * Replaces: @app.route('/api/doctor/download/<file_hash>', methods=['GET'])
     */
    @GetMapping("/download/{fileHash}")
    @RequireAuth
    @RequireRole("doctor")
    public ResponseEntity<Map<String, Object>> downloadFile(@PathVariable String fileHash,
                                                             HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String doctorId = (String) session.getAttribute("user_id");

        dataService.logAccess("download_attempt_blocked", doctorId, fileHash, null,
                "Doctor " + doctorId + " attempted to download file - download access blocked for doctors",
                request.getRemoteAddr());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", "Download access restricted");
        response.put("message", "Doctors can only view patient files online. Download functionality is restricted to patients only for security reasons.");
        return ResponseEntity.status(403).body(response);
    }

    /**
     * Doctor views patient file details without downloading
     * Replaces: @app.route('/api/doctor/view/<file_hash>', methods=['GET'])
     */
    @GetMapping("/view/{fileHash}")
    @RequireAuth
    @RequireRole("doctor")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> viewFile(@PathVariable String fileHash,
                                                         HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String doctorId = (String) session.getAttribute("user_id");

        // Find the actual file that corresponds to this hash
        String actualFilePath = null;
        Map<String, Object> foundMetadata = null;

        File storageDir = new File("storage");
        if (storageDir.exists() && storageDir.listFiles() != null) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

            for (File file : storageDir.listFiles()) {
                if (file.getName().endsWith(".meta")) {
                    try {
                        Map<String, Object> metadata = mapper.readValue(file,
                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                        if (fileHash.equals(metadata.get("file_hash"))) {
                            foundMetadata = metadata;
                            // Find the corresponding .enc file
                            String baseName = file.getName().replace(".meta", "");
                            File encFile = new File("storage/" + baseName + ".enc");
                            if (encFile.exists()) {
                                actualFilePath = encFile.getAbsolutePath();
                            }
                            break;
                        }
                    } catch (Exception e) {
                        continue;
                    }
                }
            }
        }

        if (actualFilePath == null) {
            dataService.logAccess("file_not_found", doctorId, fileHash, null,
                    "File not found", request.getRemoteAddr());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "File not found");
            return ResponseEntity.status(404).body(error);
        }

        try {
            String patientId = null;
            String originalFilename = null;
            long fileSize = new File(actualFilePath).length();
            String uploadTimestamp = null;

            if (foundMetadata != null) {
                patientId = (String) foundMetadata.get("patient_id");
                uploadTimestamp = (String) foundMetadata.get("upload_timestamp");
                originalFilename = (String) foundMetadata.get("original_filename");
            }

            // Check if doctor has access to this patient
            Map<String, Object> assignments = dataService.loadAssignments();
            Object patientIdsObj = assignments.get(doctorId);
            List<String> assignedPatients;
            if (patientIdsObj instanceof List) {
                assignedPatients = (List<String>) patientIdsObj;
            } else {
                assignedPatients = new ArrayList<>();
            }

            if (patientId == null || !assignedPatients.contains(patientId)) {
                dataService.logAccess("unauthorized_access", doctorId, fileHash, null,
                        "Doctor " + doctorId + " attempted to view file without patient consent",
                        request.getRemoteAddr());
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Access denied - Patient consent required");
                return ResponseEntity.status(403).body(error);
            }

            // Get file extension for proper display
            String fileExtension = "pdf"; // default
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            }

            // Determine file type category
            String fileType = "document";
            if (Arrays.asList("jpg", "jpeg", "png", "gif", "bmp").contains(fileExtension)) {
                fileType = "image";
            } else if (Arrays.asList("mp4", "avi", "mov", "wmv").contains(fileExtension)) {
                fileType = "video";
            } else if (Arrays.asList("mp3", "wav", "ogg").contains(fileExtension)) {
                fileType = "audio";
            } else if (Arrays.asList("dcm", "dicom").contains(fileExtension)) {
                fileType = "medical_image";
            }

            dataService.logAccess("file_viewed", doctorId, fileHash, null,
                    "Doctor viewed file details for patient " + patientId + " (view only)",
                    request.getRemoteAddr());

            Map<String, Object> fileInfo = new LinkedHashMap<>();
            fileInfo.put("patient_id", patientId);
            fileInfo.put("upload_timestamp", uploadTimestamp);
            fileInfo.put("file_hash", fileHash);
            fileInfo.put("original_filename", originalFilename != null ? originalFilename : "medical_record_" + fileHash.substring(0, 8));
            fileInfo.put("file_size", fileSize);
            fileInfo.put("file_type", fileType);
            fileInfo.put("file_extension", fileExtension);
            fileInfo.put("access_level", "View Only");
            fileInfo.put("security_note", "Download functionality is restricted to patients only");

            Map<String, Object> responseBody = new LinkedHashMap<>();
            responseBody.put("success", true);
            responseBody.put("file_info", fileInfo);
            responseBody.put("message", "File details accessed successfully - View only mode");

            return ResponseEntity.ok(responseBody);

        } catch (Exception e) {
            dataService.logAccess("file_view_error", doctorId, fileHash, null,
                    e.getMessage(), request.getRemoteAddr());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to view file details");
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Doctor requests access to patient data
     * Replaces: @app.route('/api/doctor/request_access', methods=['POST'])
     */
    @PostMapping("/request_access")
    @RequireAuth
    @RequireRole("doctor")
    public ResponseEntity<Map<String, Object>> requestAccess(@RequestBody Map<String, Object> data,
                                                              HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            String doctorId = (String) session.getAttribute("user_id");

            String patientId = (String) data.get("patient_id");
            String reason = (String) data.getOrDefault("reason", "");
            int durationDays = data.get("duration_days") != null ?
                    ((Number) data.get("duration_days")).intValue() : 30;

            // Create access request
            Map<String, Object> accessRequest = new LinkedHashMap<>();
            accessRequest.put("id", UUID.randomUUID().toString());
            accessRequest.put("doctor_id", doctorId);
            accessRequest.put("patient_id", patientId);
            accessRequest.put("reason", reason);
            accessRequest.put("duration_days", durationDays);
            accessRequest.put("status", "pending");
            accessRequest.put("requested_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            accessRequest.put("expires_at", LocalDateTime.now().plusDays(durationDays).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // Save request
            Map<String, Object> requests = dataService.loadAccessRequests();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pending = (List<Map<String, Object>>) requests.get("pending");
            pending.add(accessRequest);
            dataService.saveAccessRequests(requests);

            dataService.logAccess("access_request", doctorId, null, patientId,
                    "Requested access to " + patientId + " for " + durationDays + " days",
                    request.getRemoteAddr());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Access request sent to patient");
            response.put("request_id", accessRequest.get("id"));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to create access request: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}

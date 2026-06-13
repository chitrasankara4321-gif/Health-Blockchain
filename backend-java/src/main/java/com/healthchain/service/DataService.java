package com.healthchain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.healthchain.util.CryptoUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Data service - replaces Python's load_users(), load_assignments(), 
 * load_access_requests(), save_access_requests(), init_database(), log_access()
 */
@Service
public class DataService {

    private static final String USERS_FILE = "users/users.json";
    private static final String ASSIGNMENTS_FILE = "users/assignments.json";
    private static final String AUDIT_LOG_FILE = "audit_logs/access.log";
    private static final String ACCESS_REQUESTS_FILE = "users/access_requests.json";

    private final ObjectMapper objectMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final CryptoUtil cryptoUtil;

    public DataService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.cryptoUtil = new CryptoUtil();
    }

    public CryptoUtil getCryptoUtil() {
        return cryptoUtil;
    }

    public BCryptPasswordEncoder getPasswordEncoder() {
        return passwordEncoder;
    }

    /**
     * Initialize user database with sample doctors and patients
     * Replaces: init_database() from secure_server.py
     */
    public void initDatabase() throws IOException {
        // Create storage directories
        new File("storage").mkdirs();
        new File("users").mkdirs();
        new File("audit_logs").mkdirs();

        // Initialize users file
        if (!new File(USERS_FILE).exists()) {
            String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            Map<String, Object> drWilson = new LinkedHashMap<>();
            drWilson.put("id", "doc_001");
            drWilson.put("name", "Dr. Robert Wilson");
            drWilson.put("email", "dr.wilson@hospital.com");
            drWilson.put("password", passwordEncoder.encode("doc123456"));
            drWilson.put("role", "doctor");
            drWilson.put("department", "Internal Medicine");
            drWilson.put("license", "MD987654");
            drWilson.put("specialization", "General Practice");
            drWilson.put("created", now);

            Map<String, Object> alicePatient = new LinkedHashMap<>();
            alicePatient.put("id", "pat_001");
            alicePatient.put("name", "Alice Thompson");
            alicePatient.put("email", "alice.thompson@email.com");
            alicePatient.put("password", passwordEncoder.encode("pat123456"));
            alicePatient.put("role", "patient");
            alicePatient.put("dob", "1990-05-15");
            alicePatient.put("blood_type", "O+");
            alicePatient.put("phone", "+1-555-0101");
            alicePatient.put("emergency_contact", "John Thompson");
            alicePatient.put("created", now);

            Map<String, Object> bobPatient = new LinkedHashMap<>();
            bobPatient.put("id", "pat_002");
            bobPatient.put("name", "Bob Martinez");
            bobPatient.put("email", "bob.martinez@email.com");
            bobPatient.put("password", passwordEncoder.encode("pat123456"));
            bobPatient.put("role", "patient");
            bobPatient.put("dob", "1985-08-22");
            bobPatient.put("blood_type", "A+");
            bobPatient.put("phone", "+1-555-0102");
            bobPatient.put("emergency_contact", "Maria Martinez");
            bobPatient.put("created", now);

            // New doctor: Dr. Smith
            Map<String, Object> drSiva = new LinkedHashMap<>();
            drSiva.put("id", "doc_002");
            drSiva.put("name", "Dr.Siva Kumar");
            drSiva.put("email", "dr.sivakumar@hospital.com");
            drSiva.put("password", passwordEncoder.encode("admin"));
            drSiva.put("role", "doctor");
            drSiva.put("department", "Cardiology");
            drSiva.put("license", "MD123789");
            drSiva.put("specialization", "Cardiac Surgery");
            drSiva.put("created", now);

            // New patient: Charlie Davis
            Map<String, Object> charliePatient = new LinkedHashMap<>();
            charliePatient.put("id", "pat_003");
            charliePatient.put("name", "Charlie Davis");
            charliePatient.put("email", "charlie.davis@email.com");
            charliePatient.put("password", passwordEncoder.encode("pat123456"));
            charliePatient.put("role", "patient");
            charliePatient.put("dob", "1995-03-10");
            charliePatient.put("blood_type", "B+");
            charliePatient.put("phone", "+1-555-0103");
            charliePatient.put("emergency_contact", "Sarah Davis");
            charliePatient.put("created", now);

            Map<String, Object> doctors = new LinkedHashMap<>();
            doctors.put("dr_wilson", drWilson);
            doctors.put("dr_sankar", drSiva);

            Map<String, Object> patients = new LinkedHashMap<>();
            patients.put("alice_patient", alicePatient);
            patients.put("bob_patient", bobPatient);
            patients.put("charlie_patient", charliePatient);

            Map<String, Object> users = new LinkedHashMap<>();
            users.put("doctors", doctors);
            users.put("patients", patients);

            objectMapper.writeValue(new File(USERS_FILE), users);
        }

        // Initialize empty assignments (doctors must request access)
        if (!new File(ASSIGNMENTS_FILE).exists()) {
            Map<String, List<String>> assignments = new LinkedHashMap<>();
            assignments.put("doc_001", new ArrayList<>());
            assignments.put("doc_002", new ArrayList<>());
            objectMapper.writeValue(new File(ASSIGNMENTS_FILE), assignments);
        }

        // Initialize access requests
        if (!new File(ACCESS_REQUESTS_FILE).exists()) {
            Map<String, List<Object>> requests = new LinkedHashMap<>();
            requests.put("pending", new ArrayList<>());
            requests.put("approved", new ArrayList<>());
            requests.put("rejected", new ArrayList<>());
            objectMapper.writeValue(new File(ACCESS_REQUESTS_FILE), requests);
        }
    }

    /**
     * Load users from database
     * Replaces: load_users()
     */
    public Map<String, Object> loadUsers() throws IOException {
        return objectMapper.readValue(new File(USERS_FILE),
                new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Load doctor-patient assignments
     * Replaces: load_assignments()
     */
    public Map<String, Object> loadAssignments() throws IOException {
        return objectMapper.readValue(new File(ASSIGNMENTS_FILE),
                new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Save assignments
     */
    public void saveAssignments(Map<String, Object> assignments) throws IOException {
        objectMapper.writeValue(new File(ASSIGNMENTS_FILE), assignments);
    }

    /**
     * Load access requests
     * Replaces: load_access_requests()
     */
    public Map<String, Object> loadAccessRequests() throws IOException {
        return objectMapper.readValue(new File(ACCESS_REQUESTS_FILE),
                new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Save access requests
     * Replaces: save_access_requests(requests)
     */
    public void saveAccessRequests(Map<String, Object> requests) throws IOException {
        objectMapper.writeValue(new File(ACCESS_REQUESTS_FILE), requests);
    }

    /**
     * Log access for audit trail
     * Replaces: log_access(action, user_id, file_hash, patient_id, details)
     */
    public void logAccess(String action, String userId, String fileHash, String patientId,
                          String details, String ipAddress) {
        try {
            Map<String, String> logEntry = new LinkedHashMap<>();
            logEntry.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            logEntry.put("action", action);
            logEntry.put("user_id", userId);
            logEntry.put("file_hash", fileHash);
            logEntry.put("patient_id", patientId);
            logEntry.put("details", details);
            logEntry.put("ip_address", ipAddress);

            String logLine = objectMapper.writeValueAsString(logEntry) + "\n";

            // Ensure audit_logs directory exists
            new File("audit_logs").mkdirs();

            try (FileWriter fw = new FileWriter(AUDIT_LOG_FILE, true)) {
                fw.write(logLine);
            }
        } catch (Exception e) {
            System.err.println("Failed to write audit log: " + e.getMessage());
        }
    }

    /**
     * Save users to database
     */
    public void saveUsers(Map<String, Object> users) throws IOException {
        objectMapper.writeValue(new File(USERS_FILE), users);
    }
}

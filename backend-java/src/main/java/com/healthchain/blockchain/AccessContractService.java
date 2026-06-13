package com.healthchain.blockchain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * Smart Contract-like functionality
 * Exact port of Python's AccessContract class from access_control.py
 */
@Service
public class AccessContractService {

    private final BlockchainAccessControl blockchain;
    private Map<String, Object> contracts;

    @Autowired
    public AccessContractService(BlockchainAccessControl blockchain) {
        this.blockchain = blockchain;
        this.contracts = loadContracts();
    }

    /**
     * Load smart contracts
     * Replaces: load_contracts(self)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadContracts() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(new File(blockchain.getContractsFile()),
                    new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Failed to load contracts", e);
        }
    }

    /**
     * Patient grants consent to doctor
     * Replaces: patient_consent(self, patient_id, doctor_id, file_hash, duration_days=30)
     */
    @SuppressWarnings("unchecked")
    public Map.Entry<Boolean, String> patientConsent(String patientId, String doctorId,
                                                      String fileHash, int durationDays) {
        try {
            Map<String, Object> contract = (Map<String, Object>) contracts.get("patient_consent");

            // Validate contract terms
            if (durationDays > 90) {
                return Map.entry(false, "Duration exceeds maximum allowed");
            }

            // Add access grant
            Map<String, Object> block = blockchain.addAccessGrant(
                    doctorId, patientId, fileHash,
                    Arrays.asList("read"), (double) durationDays
            );

            return Map.entry(true, "Consent granted - Block " + block.get("index"));
        } catch (Exception e) {
            return Map.entry(false, "Failed: " + e.getMessage());
        }
    }

    /**
     * Doctor requests access with justification
     * Replaces: doctor_request(self, doctor_id, patient_id, file_hash, justification)
     */
    @SuppressWarnings("unchecked")
    public Map.Entry<Boolean, String> doctorRequest(String doctorId, String patientId,
                                                     String fileHash, String justification) {
        try {
            Map<String, Object> contract = (Map<String, Object>) contracts.get("doctor_access");

            // Validate doctor credentials (simplified)
            if (justification == null || justification.isEmpty()) {
                return Map.entry(false, "Justification required");
            }

            // In real system, verify license, department, etc.

            // Grant access
            Map<String, Object> block = blockchain.addAccessGrant(
                    doctorId, patientId, fileHash,
                    Arrays.asList("read"), 7.0
            );

            return Map.entry(true, "Access granted - Block " + block.get("index"));
        } catch (Exception e) {
            return Map.entry(false, "Failed: " + e.getMessage());
        }
    }

    /**
     * Emergency break-glass access
     * Replaces: emergency_access(self, doctor_id, patient_id, file_hash, urgency, reason)
     */
    @SuppressWarnings("unchecked")
    public Map.Entry<Boolean, String> emergencyAccess(String doctorId, String patientId,
                                                       String fileHash, String urgency, String reason) {
        try {
            Map<String, Object> contract = (Map<String, Object>) contracts.get("emergency_access");

            // Grant temporary access (~1 hour = 0.04 days)
            Map<String, Object> block = blockchain.addAccessGrant(
                    doctorId, patientId, fileHash,
                    Arrays.asList("read", "download"), 0.04
            );

            // Schedule auto-revoke (in real system)

            return Map.entry(true, "Emergency access granted - Block " + block.get("index"));
        } catch (Exception e) {
            return Map.entry(false, "Failed: " + e.getMessage());
        }
    }
}

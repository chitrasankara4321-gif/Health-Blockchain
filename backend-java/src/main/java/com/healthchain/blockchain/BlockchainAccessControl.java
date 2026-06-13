package com.healthchain.blockchain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Blockchain-based Access Control for HealthChain
 * Implements smart contract-like functionality for healthcare data access
 * 
 * Exact port of Python's BlockchainAccessControl class from access_control.py
 */
@Component
public class BlockchainAccessControl {

    private final String chainFile = "blockchain/access_chain.json";
    private final String contractsFile = "blockchain/access_contracts.json";
    private final ObjectMapper objectMapper;

    public BlockchainAccessControl() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        initBlockchain();
    }

    public String getContractsFile() {
        return contractsFile;
    }

    /**
     * Initialize blockchain with genesis block
     * Replaces: init_blockchain(self)
     */
    private void initBlockchain() {
        new File("blockchain").mkdirs();

        if (!new File(chainFile).exists()) {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

                Map<String, Object> accessGrant = new LinkedHashMap<>();
                accessGrant.put("doctor_id", "genesis");
                accessGrant.put("patient_id", "genesis");
                accessGrant.put("file_hash", "genesis");
                accessGrant.put("permissions", Arrays.asList("read", "write"));
                accessGrant.put("expires", null);

                Map<String, Object> genesisBlock = new LinkedHashMap<>();
                genesisBlock.put("index", 0);
                genesisBlock.put("timestamp", timestamp);
                genesisBlock.put("access_grant", accessGrant);
                genesisBlock.put("previous_hash", "0");
                genesisBlock.put("hash", calculateHash(0, timestamp, "genesis"));

                List<Map<String, Object>> chain = new ArrayList<>();
                chain.add(genesisBlock);

                objectMapper.writeValue(new File(chainFile), chain);
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize blockchain", e);
            }
        }

        if (!new File(contractsFile).exists()) {
            try {
                Map<String, Object> patientConsent = new LinkedHashMap<>();
                patientConsent.put("description", "Patient grants access to doctor");
                Map<String, Object> patientTerms = new LinkedHashMap<>();
                patientTerms.put("duration_max", "90_days");
                patientTerms.put("revocable", true);
                patientTerms.put("audit_required", true);
                patientTerms.put("emergency_override", true);
                patientConsent.put("terms", patientTerms);

                Map<String, Object> doctorAccess = new LinkedHashMap<>();
                doctorAccess.put("description", "Doctor requests patient data access");
                Map<String, Object> doctorTerms = new LinkedHashMap<>();
                doctorTerms.put("justification_required", true);
                doctorTerms.put("department_verification", true);
                doctorTerms.put("license_valid", true);
                doctorTerms.put("audit_trail", true);
                doctorAccess.put("terms", doctorTerms);

                Map<String, Object> emergencyAccess = new LinkedHashMap<>();
                emergencyAccess.put("description", "Emergency break-glass access");
                Map<String, Object> emergencyTerms = new LinkedHashMap<>();
                emergencyTerms.put("time_limit", "1_hour");
                emergencyTerms.put("admin_notification", true);
                emergencyTerms.put("post_review", true);
                emergencyTerms.put("auto_revoke", true);
                emergencyAccess.put("terms", emergencyTerms);

                Map<String, Object> contracts = new LinkedHashMap<>();
                contracts.put("patient_consent", patientConsent);
                contracts.put("doctor_access", doctorAccess);
                contracts.put("emergency_access", emergencyAccess);

                objectMapper.writeValue(new File(contractsFile), contracts);
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize contracts", e);
            }
        }
    }

    /**
     * Calculate block hash
     * Replaces: calculate_hash(self, index, timestamp, access_grant)
     */
    public String calculateHash(int index, String timestamp, Object accessGrant) {
        try {
            String accessGrantStr;
            if (accessGrant instanceof String) {
                accessGrantStr = (String) accessGrant;
            } else {
                // Sort keys for consistent hashing (matches Python's json.dumps(sort_keys=True))
                ObjectMapper sortedMapper = new ObjectMapper();
                sortedMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
                accessGrantStr = sortedMapper.writeValueAsString(accessGrant);
            }

            String blockString = "" + index + timestamp + accessGrantStr;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(blockString.getBytes());
            return bytesToHex(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Hash calculation failed", e);
        }
    }

    /**
     * Get the latest block in the chain
     * Replaces: get_latest_block(self)
     */
    public Map<String, Object> getLatestBlock() throws IOException {
        List<Map<String, Object>> chain = loadChain();
        return chain.get(chain.size() - 1);
    }

    /**
     * Add a new access grant to the blockchain
     * Replaces: add_access_grant(self, doctor_id, patient_id, file_hash, permissions, duration_days=None)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> addAccessGrant(String doctorId, String patientId, String fileHash,
                                               List<String> permissions, Double durationDays) throws IOException {
        List<Map<String, Object>> chain = loadChain();
        Map<String, Object> latestBlock = chain.get(chain.size() - 1);

        // Calculate expiration
        String expires = null;
        if (durationDays != null) {
            long durationMinutes = (long) (durationDays * 24 * 60);
            expires = LocalDateTime.now().plusMinutes(durationMinutes).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        Map<String, Object> accessGrant = new LinkedHashMap<>();
        accessGrant.put("doctor_id", doctorId);
        accessGrant.put("patient_id", patientId);
        accessGrant.put("file_hash", fileHash);
        accessGrant.put("permissions", permissions);
        accessGrant.put("granted_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        accessGrant.put("expires", expires);
        accessGrant.put("status", "active");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        Map<String, Object> newBlock = new LinkedHashMap<>();
        newBlock.put("index", chain.size());
        newBlock.put("timestamp", timestamp);
        newBlock.put("access_grant", accessGrant);
        newBlock.put("previous_hash", latestBlock.get("hash"));
        newBlock.put("hash", calculateHash(chain.size(), timestamp, accessGrant));

        chain.add(newBlock);
        saveChain(chain);

        return newBlock;
    }

    /**
     * Verify if doctor has access to patient file
     * Replaces: verify_access(self, doctor_id, patient_id, file_hash)
     */
    @SuppressWarnings("unchecked")
    public Map.Entry<Boolean, String> verifyAccess(String doctorId, String patientId, String fileHash) throws IOException {
        List<Map<String, Object>> chain = loadChain();

        // Iterate in reverse order
        for (int i = chain.size() - 1; i >= 0; i--) {
            Map<String, Object> block = chain.get(i);
            Map<String, Object> grant = (Map<String, Object>) block.get("access_grant");

            if (doctorId.equals(grant.get("doctor_id")) &&
                    patientId.equals(grant.get("patient_id")) &&
                    fileHash.equals(grant.get("file_hash")) &&
                    "active".equals(grant.get("status"))) {

                // Check if expired
                if (grant.get("expires") != null) {
                    LocalDateTime expires = LocalDateTime.parse((String) grant.get("expires"));
                    if (LocalDateTime.now().isAfter(expires)) {
                        return Map.entry(false, "Access expired");
                    }
                }

                return Map.entry(true, "Access granted");
            }
        }

        return Map.entry(false, "No access grant found");
    }

    /**
     * Revoke access grant
     * Replaces: revoke_access(self, doctor_id, patient_id, file_hash)
     */
    @SuppressWarnings("unchecked")
    public boolean revokeAccess(String doctorId, String patientId, String fileHash) throws IOException {
        List<Map<String, Object>> chain = loadChain();

        for (Map<String, Object> block : chain) {
            Map<String, Object> grant = (Map<String, Object>) block.get("access_grant");

            if (doctorId.equals(grant.get("doctor_id")) &&
                    patientId.equals(grant.get("patient_id")) &&
                    fileHash.equals(grant.get("file_hash"))) {

                grant.put("status", "revoked");
                grant.put("revoked_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                break;
            }
        }

        saveChain(chain);
        return true;
    }

    /**
     * Get access history for patient or doctor
     * Replaces: get_access_history(self, patient_id=None, doctor_id=None)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAccessHistory(String patientId, String doctorId) throws IOException {
        List<Map<String, Object>> chain = loadChain();
        List<Map<String, Object>> history = new ArrayList<>();

        for (Map<String, Object> block : chain) {
            Map<String, Object> grant = (Map<String, Object>) block.get("access_grant");

            boolean matches = false;
            if (patientId != null && patientId.equals(grant.get("patient_id"))) {
                matches = true;
            }
            if (doctorId != null && doctorId.equals(grant.get("doctor_id"))) {
                matches = true;
            }

            if (matches) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("block_index", block.get("index"));
                entry.put("timestamp", block.get("timestamp"));
                entry.put("hash", block.get("hash"));
                entry.put("grant", grant);
                history.add(entry);
            }
        }

        return history;
    }

    /**
     * Load blockchain from file
     * Replaces: load_chain(self)
     */
    public List<Map<String, Object>> loadChain() throws IOException {
        return objectMapper.readValue(new File(chainFile),
                new TypeReference<List<Map<String, Object>>>() {});
    }

    /**
     * Save blockchain to file
     * Replaces: save_chain(self, chain)
     */
    public void saveChain(List<Map<String, Object>> chain) throws IOException {
        objectMapper.writeValue(new File(chainFile), chain);
    }

    /**
     * Validate blockchain integrity
     * Replaces: validate_chain(self)
     */
    @SuppressWarnings("unchecked")
    public Map.Entry<Boolean, String> validateChain() throws IOException {
        List<Map<String, Object>> chain = loadChain();

        for (int i = 1; i < chain.size(); i++) {
            Map<String, Object> currentBlock = chain.get(i);
            Map<String, Object> previousBlock = chain.get(i - 1);

            // Check hash continuity
            if (!currentBlock.get("previous_hash").equals(previousBlock.get("hash"))) {
                return Map.entry(false, "Chain broken at block " + i);
            }

            // Recalculate hash to verify
            String calculatedHash = calculateHash(
                    ((Number) currentBlock.get("index")).intValue(),
                    (String) currentBlock.get("timestamp"),
                    currentBlock.get("access_grant")
            );

            if (!calculatedHash.equals(currentBlock.get("hash"))) {
                return Map.entry(false, "Invalid hash at block " + i);
            }
        }

        return Map.entry(true, "Chain is valid");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

package com.healthchain.controller;

import com.healthchain.blockchain.AccessContractService;
import com.healthchain.blockchain.BlockchainAccessControl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blockchain controller - replaces integrate_blockchain_access() from access_control.py
 */
@RestController
@RequestMapping("/api/blockchain")
public class BlockchainController {

    @Autowired
    private BlockchainAccessControl blockchain;

    @Autowired
    private AccessContractService accessContract;

    /**
     * Grant access using blockchain
     * Replaces: @app.route('/api/blockchain/grant_access', methods=['POST'])
     */
    @PostMapping("/grant_access")
    public ResponseEntity<Map<String, Object>> grantAccess(@RequestBody Map<String, Object> data) {
        try {
            String doctorId = (String) data.get("doctor_id");
            String patientId = (String) data.get("patient_id");
            String fileHash = (String) data.get("file_hash");
            int duration = data.get("duration_days") != null ?
                    ((Number) data.get("duration_days")).intValue() : 30;

            Map.Entry<Boolean, String> result = accessContract.patientConsent(
                    patientId, doctorId, fileHash, duration);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", result.getKey());
            response.put("message", result.getValue());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to grant access: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Verify access using blockchain
     * Replaces: @app.route('/api/blockchain/verify_access', methods=['POST'])
     */
    @PostMapping("/verify_access")
    public ResponseEntity<Map<String, Object>> verifyAccess(@RequestBody Map<String, String> data) {
        try {
            String doctorId = data.get("doctor_id");
            String patientId = data.get("patient_id");
            String fileHash = data.get("file_hash");

            Map.Entry<Boolean, String> result = blockchain.verifyAccess(doctorId, patientId, fileHash);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("has_access", result.getKey());
            response.put("message", result.getValue());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to verify access: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Get patient's access history from blockchain
     * Replaces: @app.route('/api/blockchain/history/<patient_id>', methods=['GET'])
     */
    @GetMapping("/history/{patientId}")
    public ResponseEntity<Map<String, Object>> getHistory(@PathVariable String patientId) {
        try {
            List<Map<String, Object>> history = blockchain.getAccessHistory(patientId, null);

            Map<String, Object> response = new HashMap<>();
            response.put("history", history);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to load history: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Validate blockchain integrity
     * Replaces: @app.route('/api/blockchain/validate', methods=['GET'])
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate() {
        try {
            Map.Entry<Boolean, String> result = blockchain.validateChain();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("valid", result.getKey());
            response.put("message", result.getValue());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to validate chain: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}

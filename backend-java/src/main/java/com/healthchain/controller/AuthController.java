package com.healthchain.controller;

import com.healthchain.annotation.RequireAuth;
import com.healthchain.service.DataService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authentication controller - replaces Python's login(), logout(), get_profile() endpoints
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    @Autowired
    private DataService dataService;

    /**
     * User login endpoint
     * Replaces: @app.route('/api/login', methods=['POST'])
     */
    @PostMapping("/login")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> data,
                                                      HttpServletRequest request) {
        try {
            String username = data.get("username");
            String password = data.get("password");

            Map<String, Object> users = dataService.loadUsers();

            // Check doctors
            Map<String, Object> doctors = (Map<String, Object>) users.get("doctors");
            if (doctors != null) {
                for (Map.Entry<String, Object> entry : doctors.entrySet()) {
                    String uid = entry.getKey();
                    Map<String, Object> user = (Map<String, Object>) entry.getValue();

                    if (uid.equals(username) &&
                            dataService.getPasswordEncoder().matches(password, (String) user.get("password"))) {

                        HttpSession session = request.getSession(true);
                        session.setAttribute("user_id", user.get("id"));
                        session.setAttribute("username", uid);
                        session.setAttribute("role", user.get("role"));

                        dataService.logAccess("login", (String) user.get("id"), null, null,
                                "Doctor login: " + user.get("name"), request.getRemoteAddr());

                        Map<String, Object> response = new LinkedHashMap<>();
                        response.put("success", true);

                        Map<String, Object> userInfo = new LinkedHashMap<>();
                        userInfo.put("id", user.get("id"));
                        userInfo.put("name", user.get("name"));
                        userInfo.put("role", user.get("role"));
                        userInfo.put("department", user.getOrDefault("department", ""));
                        userInfo.put("license", user.getOrDefault("license", ""));
                        response.put("user", userInfo);

                        return ResponseEntity.ok(response);
                    }
                }
            }

            // Check patients
            Map<String, Object> patients = (Map<String, Object>) users.get("patients");
            if (patients != null) {
                for (Map.Entry<String, Object> entry : patients.entrySet()) {
                    String uid = entry.getKey();
                    Map<String, Object> user = (Map<String, Object>) entry.getValue();

                    if (uid.equals(username) &&
                            dataService.getPasswordEncoder().matches(password, (String) user.get("password"))) {

                        HttpSession session = request.getSession(true);
                        session.setAttribute("user_id", user.get("id"));
                        session.setAttribute("username", uid);
                        session.setAttribute("role", user.get("role"));

                        dataService.logAccess("login", (String) user.get("id"), null, null,
                                "Patient login: " + user.get("name"), request.getRemoteAddr());

                        Map<String, Object> response = new LinkedHashMap<>();
                        response.put("success", true);

                        Map<String, Object> userInfo = new LinkedHashMap<>();
                        userInfo.put("id", user.get("id"));
                        userInfo.put("name", user.get("name"));
                        userInfo.put("role", user.get("role"));
                        userInfo.put("dob", user.getOrDefault("dob", ""));
                        userInfo.put("blood_type", user.getOrDefault("blood_type", ""));
                        response.put("user", userInfo);

                        return ResponseEntity.ok(response);
                    }
                }
            }

            dataService.logAccess("login_failed", null, null, null,
                    "Failed login attempt: " + username, request.getRemoteAddr());

            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid credentials");
            return ResponseEntity.status(401).body(error);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Login failed: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * User logout endpoint
     * Replaces: @app.route('/api/logout', methods=['POST'])
     */
    @PostMapping("/logout")
    @RequireAuth
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            dataService.logAccess("logout", (String) session.getAttribute("user_id"), null, null,
                    "User logout: " + session.getAttribute("username"), request.getRemoteAddr());
            session.invalidate();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    /**
     * Get current user profile
     * Replaces: @app.route('/api/profile', methods=['GET'])
     */
    @GetMapping("/profile")
    @RequireAuth
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getProfile(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            String userId = (String) session.getAttribute("user_id");

            Map<String, Object> users = dataService.loadUsers();

            // Search in doctors and patients
            for (String userType : new String[]{"doctors", "patients"}) {
                Map<String, Object> userMap = (Map<String, Object>) users.get(userType);
                if (userMap != null) {
                    for (Map.Entry<String, Object> entry : userMap.entrySet()) {
                        Map<String, Object> user = (Map<String, Object>) entry.getValue();
                        if (userId.equals(user.get("id"))) {
                            Map<String, Object> response = new HashMap<>();
                            response.put("user", user);
                            return ResponseEntity.ok(response);
                        }
                    }
                }
            }

            Map<String, Object> error = new HashMap<>();
            error.put("error", "User not found");
            return ResponseEntity.status(404).body(error);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to load profile: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}

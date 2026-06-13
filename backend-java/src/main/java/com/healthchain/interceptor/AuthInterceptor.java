package com.healthchain.interceptor;

import com.healthchain.annotation.RequireAuth;
import com.healthchain.annotation.RequireRole;
import com.healthchain.service.DataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication and role-based access control interceptor.
 * Replaces Python's @require_auth and @require_role decorators.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private DataService dataService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Only intercept handler methods (not static resources)
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // Check for @RequireAuth annotation
        RequireAuth requireAuth = handlerMethod.getMethodAnnotation(RequireAuth.class);
        if (requireAuth != null) {
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("user_id") == null) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
                return false;
            }
        }

        // Check for @RequireRole annotation
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole != null) {
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("user_id") == null) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
                return false;
            }

            String userId = (String) session.getAttribute("user_id");
            String requiredRole = requireRole.value();

            // Load users and find current user
            Map<String, Object> users = dataService.loadUsers();
            Map<String, Object> user = findUserById(users, userId);

            if (user == null || !requiredRole.equals(user.get("role"))) {
                sendError(response, HttpServletResponse.SC_FORBIDDEN, "Insufficient permissions");
                return false;
            }
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findUserById(Map<String, Object> users, String userId) {
        // Check doctors
        Map<String, Object> doctors = (Map<String, Object>) users.get("doctors");
        if (doctors != null) {
            for (Map.Entry<String, Object> entry : doctors.entrySet()) {
                Map<String, Object> doctor = (Map<String, Object>) entry.getValue();
                if (userId.equals(doctor.get("id"))) {
                    return doctor;
                }
            }
        }

        // Check patients
        Map<String, Object> patients = (Map<String, Object>) users.get("patients");
        if (patients != null) {
            for (Map.Entry<String, Object> entry : patients.entrySet()) {
                Map<String, Object> patient = (Map<String, Object>) entry.getValue();
                if (userId.equals(patient.get("id"))) {
                    return patient;
                }
            }
        }

        return null;
    }

    private void sendError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json");
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}

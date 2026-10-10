package com.template.account_login_logout.controller;

import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.service.UserManagementService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final UserManagementService userManagementService;

    public AdminUserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers() {
        return ResponseEntity.ok(userManagementService.findAllAccounts().stream()
                .map(this::buildUserStatusResponse)
                .toList());
    }

    @PutMapping("/users/{id}/enable")
    public ResponseEntity<Map<String, Object>> enableUser(@PathVariable Long id) {
        Account account = userManagementService.updateAccountStatus(id, true);
        return ResponseEntity.ok(buildUserStatusResponse(account));
    }

    @PutMapping("/users/{id}/disable")
    public ResponseEntity<Map<String, Object>> disableUser(@PathVariable Long id) {
        Account account = userManagementService.updateAccountStatus(id, false);
        return ResponseEntity.ok(buildUserStatusResponse(account));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<Map<String, Object>> updateUserRole(@PathVariable Long id,
                                                               @RequestParam String role) {
        Account account = userManagementService.updateAccountRole(id, role.toUpperCase());
        return ResponseEntity.ok(buildUserStatusResponse(account));
    }

    private Map<String, Object> buildUserStatusResponse(Account account) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", account.getId());
        body.put("username", account.getUsername());
        body.put("email", account.getEmail());
        body.put("enabled", account.isEnabled());
        body.put("emailVerified", account.isEmailVerified());
        body.put("role", account.getRole());
        return body;
    }
}

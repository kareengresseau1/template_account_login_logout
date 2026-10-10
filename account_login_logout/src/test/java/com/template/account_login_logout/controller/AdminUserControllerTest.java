package com.template.account_login_logout.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.service.UserManagementService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private UserManagementService userManagementService;

    @Test
    void userListingReturnsAccountSummariesWithoutCredentialOrRecoverySecrets() {
        Account account = new Account();
        account.setId(12L);
        account.setUsername("sam");
        account.setEmail("sam@example.com");
        account.setPassword("password-hash");
        account.setPasswordResetToken("reset-hash");
        account.setEmailVerificationToken("verification-hash");
        account.setRole("ADMIN");
        when(userManagementService.findAllAccounts()).thenReturn(List.of(account));
        AdminUserController controller = new AdminUserController(userManagementService);

        ResponseEntity<List<Map<String, Object>>> response = controller.listUsers();

        Map<String, Object> summary = response.getBody().getFirst();
        assertThat(summary).containsEntry("id", 12L)
                .containsEntry("username", "sam")
                .containsEntry("email", "sam@example.com")
                .containsEntry("enabled", true)
                .containsEntry("role", "ADMIN");
        assertThat(summary).doesNotContainKeys("password", "passwordResetToken", "emailVerificationToken");
    }

    @Test
    void roleUpdateNormalizesRequestedRole() {
        Account account = new Account();
        account.setUsername("sam");
        account.setRole("ADMIN");
        when(userManagementService.updateAccountRole(12L, "ADMIN")).thenReturn(account);
        AdminUserController controller = new AdminUserController(userManagementService);

        ResponseEntity<Map<String, Object>> response = controller.updateUserRole(12L, "admin");

        assertThat(response.getBody()).containsEntry("role", "ADMIN");
        verify(userManagementService).updateAccountRole(12L, "ADMIN");
    }
}

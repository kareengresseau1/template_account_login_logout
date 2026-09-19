package com.template.account_login_logout.authentication.dto;

import lombok.Data;

@Data
public class LogoutRequest {

    private String refreshToken;

    // Optional: also pass the Access Token if the client can't send the Authorization header
    private String accessToken;
}

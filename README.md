# Account Login and Logout

## Repository Goal

This repository provides a backend REST API template for account authentication and session management. It demonstrates registration, password login, signed JWT access and refresh tokens, logout, password reset, email verification, per-device sessions, administrative user management, audit events, rate limiting, and metrics.

The application is built with Java 21, Spring Boot, Spring Security, Spring Data JPA, JJWT, and Gradle. The default profile uses an in-memory H2 database so the API can be tested locally without installing a database. Redis, SMTP, Prometheus, and OTLP settings are optional for local development; the `shared-infrastructure` profile requires the shared services while retaining H2 for this test phase.

The API is backend-only. A separate client can call its HTTP endpoints; no web UI is included here.

## Architecture at a Glance

- `AccountController` exposes public authentication and account-recovery endpoints under `/api/auth`.
- `AdminUserController` exposes administrative account endpoints under `/api/admin`.
- `AccountService`, `RefreshTokenService`, `JwtService`, `TokenBlacklistService`, and `RateLimitService` implement the main authentication workflows.
- `CustomUserDetailsService` loads accounts for Spring Security. `JwtAuthenticationFilter` authenticates protected requests.
- `Account`, `RefreshToken`, `RevokedToken`, and `AuditLog` are JPA entities. Their repositories provide database access.
- `ApiExceptionHandler` maps validation and application exceptions to API error responses.
- `AuthMetricsService` records authentication metrics. `AuditLogService` stores security events.
- `EmailService` sends reset and verification messages through the configured SMTP server.

## Local Setup: H2

From the repository root:

```bash
cd account_login_logout
./gradlew test
./gradlew bootRun
```

The default configuration in `src/main/resources/application.properties` uses `jdbc:h2:mem:accounts`. It is an in-memory database: its data is lost when the application stops. Hibernate creates/updates the local schema automatically. `DataInitializer` seeds `jane` and `alex` with password `password123` when `DEMO_SEED_ENABLED` is true (the default).

For local testing, the H2 console is available at `http://localhost:8080/h2-console` with JDBC URL `jdbc:h2:mem:accounts`, username `sa`, and a blank password. It is a development tool; do not expose it outside a local environment.

### Persistent local demo database

To keep test accounts between restarts, activate the `demo` profile. It uses a local H2 file at `account_login_logout/data/accounts` (ignored by Git), and seeds `jane` and `alex` with password `password123`. To also seed your own reset-test account, set its username, email, and initial password on first startup:

```bash
cd account_login_logout
SPRING_PROFILES_ACTIVE=demo \
DEMO_ACCOUNT_USERNAME=krgresseau \
DEMO_ACCOUNT_EMAIL=your-email@example.com \
DEMO_ACCOUNT_PASSWORD='choose-a-temporary-password' \
./gradlew bootRun
```

The configured account is created only if its username is not already in the database. Its password is stored as a BCrypt hash; the raw password is not written to the repository. You can omit the three `DEMO_ACCOUNT_*` variables on later starts. Use the seeded usernames or emails as the `loginId` for login. For password-reset tests, configure a working SMTP sender in the local environment; reset requests send the one-time token by email.

The default JWT secret is for development only. Replace it before exposing the API beyond local testing. SMTP delivery also requires a reachable SMTP server; without one, reset and verification requests that need to send mail will fail rather than returning a false success.

## Processes and Execution Steps

### 1. Register an account

Endpoint: `POST /api/auth/register`

Example body:

```json
{
  "username": "sam",
  "email": "sam@example.com",
  "password": "a-long-password"
}
```

Execution flow:

1. `AccountController.register` validates `RegisterRequest`.
2. `AccountService.register` trims the username/email, checks duplicates, and hashes the password with Spring Security's `PasswordEncoder` (BCrypt).
3. `AccountRepository` persists the `Account` entity. New accounts have the default `USER` role and are not yet email-verified.

Classes: `RegisterRequest`, `AccountController`, `AccountService`, `SecurityConfig`, `Account`, `AccountRepository`.

### 2. Log in

Endpoint: `POST /api/auth/login`

Example body:

```json
{
  "loginId": "sam@example.com",
  "password": "a-long-password"
}
```

Execution flow:

1. `AccountController.login` applies a login-attempt limit using `RateLimitService` (5 attempts per five-minute window, keyed by login ID and client IP).
2. The controller passes credentials to Spring Security's `AuthenticationManager`.
3. `CustomUserDetailsService` finds the account by username, then email, and supplies its BCrypt hash and `ROLE_USER`/`ROLE_ADMIN` authority.
4. If credentials are accepted, `JwtService` creates an access-token/refresh-token pair with a shared session ID.
5. `RefreshTokenService` stores the refresh token and session metadata (session ID, expiry, IP address, user agent, and timestamps) in `RefreshTokenRepository`.
6. `AuditLogService` records the login event and `AuthMetricsService` records its result and duration.

The response contains `accessToken`, `refreshToken`, `tokenType`, and `expiresIn`. Send the access token on protected requests as `Authorization: Bearer <accessToken>`.

Classes: `LoginRequest`, `AccountController`, `AuthenticationManager`, `CustomUserDetailsService`, `JwtService`, `RefreshTokenService`, `RefreshToken`, `RefreshTokenRepository`, `RateLimitService`, `AuditLogService`, `AuthMetricsService`.

### 3. Call a protected endpoint

Execution flow for requests carrying a bearer token:

1. `SecurityConfig` routes the request through `JwtAuthenticationFilter`.
2. `TokenBlacklistService` checks whether the access token's hash has been revoked.
3. `JwtService` validates the token signature and expiration.
4. `RefreshTokenService` confirms the token's session ID still exists for the account.
5. `CustomUserDetailsService` reloads the account; disabled accounts are rejected.
6. Spring Security puts the authenticated principal and authorities in the security context. The controller can then use `@AuthenticationPrincipal`.

A deleted/revoked session makes its access token unusable immediately, not only after its JWT expires.

Classes: `SecurityConfig`, `JwtAuthenticationFilter`, `JwtService`, `TokenBlacklistService`, `RefreshTokenService`, `CustomUserDetailsService`.

### 4. Refresh a token pair

Endpoint: `POST /api/auth/refresh`

Example body:

```json
{
  "refreshToken": "<refresh-token>"
}
```

Execution flow:

1. `AccountController.refresh` looks up the supplied token through `RefreshTokenService` and `RefreshTokenRepository`.
2. `RefreshTokenService.verifyExpiration` rejects and deletes expired refresh tokens.
3. The controller reloads the account and rejects disabled accounts.
4. `JwtService` generates a new access token and refresh token with the same session ID.
5. `RefreshTokenService.rotate` replaces the stored refresh token and updates its expiry/last-use time. The old refresh token cannot be reused.

Classes: `RefreshTokenRequest`, `AccountController`, `RefreshTokenService`, `RefreshTokenRepository`, `JwtService`, `CustomUserDetailsService`.

### 5. Log out or manage device sessions

Logout endpoint: `POST /api/auth/logout`

The usual request sends the access token in the Authorization header. Logout blacklists that access token and removes only the refresh-token session with the matching session ID. Other devices stay logged in. The optional `LogoutRequest` body accepts an access token and/or refresh token; refresh-token deletion is scoped to that token.

To list the signed-in account's sessions, call `GET /api/auth/sessions`. To revoke one session, call `DELETE /api/auth/sessions/{id}`. Both endpoints require a valid access token, and session lookup/revocation is scoped to the authenticated username.

Classes: `AccountController`, `LogoutRequest`, `JwtService`, `TokenBlacklistService`, `RefreshTokenService`, `RefreshTokenRepository`, `RefreshToken`.

### 6. Request a password reset

Endpoint: `POST /api/auth/password-reset/request`

Example body:

```json
{
  "email": "sam@example.com"
}
```

Execution flow:

1. `AccountController` limits requests to 3 per 15 minutes per normalized email and client IP.
2. `AccountService` generates a random one-time token, stores only its SHA-256 hash and expiry on the `Account`, and asks `EmailService` to send the raw token to the account email. The response does not reveal whether that email belongs to an account.
3. The user submits the emailed token to `POST /api/auth/password-reset/confirm` with `email`, `token`, and `newPassword`.
4. `AccountService` hashes the submitted token and compares it to the stored hash, checks expiry, hashes the new password with BCrypt, clears the reset token, and revokes all of the account's refresh sessions.

Classes: `PasswordResetRequest`, `PasswordResetConfirmRequest`, `AccountController`, `AccountService`, `EmailService`, `RateLimitService`, `Account`, `AccountRepository`, `RefreshTokenService`.

### 7. Verify an email address

Endpoints: `POST /api/auth/email-verification/request` and `POST /api/auth/email-verification/confirm`.

The request endpoint accepts an `email`, applies the same per-email/IP rate limit, creates a hashed, expiring verification token, and sends the raw token using `EmailService`. The confirmation body contains `email` and `token`; `AccountService` verifies the hash/expiry and marks the account's `emailVerified` field true. The current login flow does not require `emailVerified` to be true.

Classes: `EmailVerificationRequest`, `EmailVerificationConfirmRequest`, `AccountController`, `AccountService`, `EmailService`, `RateLimitService`, `Account`, `AccountRepository`.

### 8. Manage users as an administrator

All `/api/admin/**` routes require the `ROLE_ADMIN` authority. `AdminUserController` supports:

- `GET /api/admin/users` to list account summaries.
- `PUT /api/admin/users/{id}/enable` and `/disable` to change account status. Disabling an account also deletes its refresh sessions.
- `PUT /api/admin/users/{id}/role?role=USER` or `role=ADMIN` to change its role.

`UserManagementService` applies these changes through `AccountRepository`. Responses contain safe account-summary fields, not password or recovery-token values.

To bootstrap an administrator at startup, set `ADMIN_USERNAME`, `ADMIN_EMAIL`, and `ADMIN_PASSWORD`. If that username exists, it is promoted to `ADMIN`; otherwise, a new account is created with the configured password hashed by BCrypt. If no admin settings are supplied, no new admin is created.

Classes: `AdminUserController`, `UserManagementService`, `SecurityConfig`, `CustomUserDetailsService`, `DataInitializer`, `Account`, `AccountRepository`, `RefreshTokenService`.

### 9. Handle revoked-token records and legacy sessions

`TokenBlacklistService` stores SHA-256 hashes of revoked access tokens in `RevokedTokenRepository`, considers only unexpired records active, and periodically deletes expired rows. Retention is based on the configured access-token lifetime.

At startup, `DataInitializer` deletes refresh-token records with no session ID. This intentionally requires users with sessions created before session IDs were added to sign in again.

Classes: `TokenBlacklistService`, `RevokedToken`, `RevokedTokenRepository`, `DemoApplication`, `DataInitializer`, `RefreshTokenRepository`.

## API and Error Handling

`SecurityConfig` permits registration, login, refresh, logout, password-reset, email-verification, local H2 console, health, and Prometheus endpoints. Other API endpoints require authentication; `/api/admin/**` additionally requires `ROLE_ADMIN`.

Request DTOs under `authentication/dto` use Jakarta Bean Validation. `ApiExceptionHandler` converts validation failures, bad credentials, invalid refresh tokens, rate-limit rejections, and invalid requests into consistent API error responses.

Audit records are written by `AuditLogService` to `AuditLogRepository`. Core login, logout, refresh, and rate-limit events are instrumented by `AuthMetricsService`; Spring Actuator exposes metrics and health endpoints.

## Shared Infrastructure and Monitoring

The default profile is for local testing. To require shared Redis, SMTP, and OTLP endpoints, activate the `shared-infrastructure` profile:

```bash
cd account_login_logout
SPRING_PROFILES_ACTIVE=shared-infrastructure ./gradlew bootRun
```

Configure these environment variables first:

- Redis: `REDIS_HOST`, optional `REDIS_PORT`.
- SMTP: `MAIL_HOST`, optional `MAIL_PORT` (default `587`), `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`.
- OpenTelemetry: `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`, `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT`; optional `TRACING_SAMPLING_PROBABILITY`.

This profile requires Redis for rate limiting (fails closed if Redis is unavailable), enables SMTP authentication/STARTTLS, and enables OTLP metrics/traces. The datasource intentionally remains in-memory H2 for this test phase.

Prometheus metrics are exposed at `/actuator/prometheus`. The application currently permits this path in Spring Security; restrict it to the monitoring network at the local proxy/firewall before deployment. Example Prometheus scraping and alert rules are in `monitoring/prometheus.yml` and `monitoring/prometheus-alerts.yml`. `monitoring/alertmanager.yml.template` is an email receiver template; render it with secret environment values before starting Alertmanager, for example:

```bash
envsubst < monitoring/alertmanager.yml.template > /tmp/alertmanager.yml
```

Prometheus scraping and alerting require Prometheus and Alertmanager processes to be deployed separately; these configuration files do not start those services.

## Configuration Notes

- `DEMO_SEED_ENABLED` defaults to `true`; set it to `false` when you do not want the local `jane` demo account seeded.
- The default H2 database is in-memory; use `SPRING_PROFILES_ACTIVE=demo` for a local file-backed database.
- Demo accounts use the shared password `password123`; use them only for local testing.
- Replace the development `app.jwt.secret` before using the service outside local testing.
- `RATE_LIMIT_REDIS_REQUIRED=true` can also be used without the profile to fail closed when Redis is unavailable. Without it, the local in-memory limiter is the fallback.
- SMTP, Redis, OTLP, Prometheus, and Alertmanager need to be reachable/configured in the environment where the related process runs.

### Test the login 

- Go to the class account_login_logout/src/main/java/com/template/account_login_logout/DataInitializer.java 
- In the run method, add a user with an existing real email address.

```java  
    @Override
    public void run(ApplicationArguments args) {
        refreshTokenRepository.deleteBySessionKeyIsNull();

        if (seedDemoAccount) {
            createDemoAccountIfMissing("jane", "jane@example.com", "password123");
            createDemoAccountIfMissing("alex", "alex@example.com", "password123"); 
            createDemoAccountIfMissing("kareen", "krgresseau@gmail.com", "password456");    <— Create an account here with a real email address
            seedConfiguredDemoAccount();
        }

``` 
- In a new terminal, execute the following command. Make sure to use a username and password defined in the class DataInitializer.java

```shell 
curl -i -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"loginId”:”your_username,”password”:”your_password”}’ 
``` 

This command should return a 200 HTTP response code with an access token. Copy the access token somewhere safe because it’ll be needed to test the logout.  


### Test the logout 
- In a terminal, execute the following command. Replace “YOUR_ACCESS_TOKEN” by the access token that was generated with the login command without the quotation marks.

```shell 
curl -i -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
``` 

### Test the password reset 
- In a terminal, execute the following command. Replace “your_email_address” with the email address for the account you want to reset the password for. It has to be a real email address

```shell 
curl -i -X POST http://localhost:8080/api/auth/password-reset/request \
  -H 'Content-Type: application/json' \
  -d '{"email”:”your_email”_address}’ 
```

- Go to your email inbox and look for the password reset email. In this email you will be given a new token. 
- In the terminal, execute the following command. Replace “PASTE_TOKEN_HERE” with the token you received in the email. Replace “your-new-password” by a new password of your choice.

```shell  
curl -i -X POST http://localhost:8080/api/auth/password-reset/confirm \
  -H 'Content-Type: application/json' \
  -d '{"email":"krgresseau@gmail.com","token":"PASTE_TOKEN_HERE","newPassword":"your-new-password"}'
``` 
- Try to login with the new password to see if the reset was successful. 


## Build and Test

```bash
cd account_login_logout
./gradlew test --no-daemon
```

Run the service locally with `./gradlew bootRun --no-daemon`. The application listens on port `8080` by default.

# Account Login and Logout Template

This project is a Spring Boot REST API template for account authentication. It
supports logging in with a username or email address, issuing short-lived JWT
access tokens, refreshing those tokens with a persisted refresh token, and
logging out by revoking the current session tokens.

The application is intentionally split into controllers, services, security
components, repositories, entities, and request/response DTOs. Each layer has a
small responsibility so the authentication flow can be extended without
putting database, token, and HTTP concerns in the same class.

## Main Components

### Web layer

`AccountController` exposes the authentication API under `/api/auth`:

- `POST /api/auth/login` authenticates credentials and returns access and
	refresh tokens.
- `POST /api/auth/refresh` validates and rotates a refresh token, returning a
	new token pair.
- `POST /api/auth/logout` revokes the access token from the `Authorization`
	header and removes the user's persisted refresh token. It can also accept
	tokens in a `LogoutRequest` body when a header is unavailable.

The controller does not check passwords or construct JWT claims itself. It
delegates authentication to Spring Security's `AuthenticationManager`, token
creation to `JwtService`, and refresh-token persistence to
`RefreshTokenService`.

The DTOs in `authentication/dto` define the JSON contract:

- `LoginRequest` contains `loginId` and `password`.
- `AuthResponse` contains `accessToken`, `refreshToken`, `tokenType`, and
	`expiresIn`.
- `RefreshTokenRequest` contains the refresh token to exchange.
- `LogoutRequest` optionally contains an access token and/or refresh token.

Jakarta Bean Validation rejects blank login credentials and blank refresh
tokens before the controller method runs.

### Account model and data access

`Account` is the JPA entity mapped to the `ACCOUNT` table. It stores the
account's generated ID, unique username, unique email, and password. Passwords
should be stored as BCrypt hashes, never as plain text.

`AccountRepository` extends Spring Data's `JpaRepository<Account, Long>`.
Its `findByUsername` and `findByEmail` methods return `Optional<Account>` so
callers must handle accounts that do not exist.

`RefreshToken` is another JPA entity. It stores the refresh token string,
expiration time, and a many-to-one relationship to its `Account` owner.
`RefreshTokenRepository` provides lookup by token and deletion of all tokens for
a username.

`AccountService` contains basic account lookup methods and is available as a
place to add registration, account updates, or account-management rules.

### User lookup and password authentication

`CustomUserDetailsService` implements Spring Security's `UserDetailsService`.
When Spring receives a login attempt, it calls `loadUserByUsername`. The method
first searches by username and then by email. It converts the resulting
`Account` into Spring Security's `UserDetails` object and assigns the
`ROLE_USER` authority.

`SecurityConfig` exposes a BCrypt `PasswordEncoder` and an
`AuthenticationManager`. During login, the controller creates a
`UsernamePasswordAuthenticationToken` from the request credentials and passes
it to that manager. The manager uses `CustomUserDetailsService` to load the
account and the password encoder to compare the supplied password with the
stored hash. Invalid credentials result in an authentication failure instead
of tokens being issued.

### JWT access tokens

`JwtService` creates and validates signed JWTs using the JJWT library. Each
token contains the account username as its subject, a token type claim, an
issued-at time, and an expiration time.

Access tokens are short-lived and are sent by clients on protected requests:

```http
Authorization: Bearer <access-token>
```

`JwtAuthenticationFilter` runs before Spring Security's normal username/
password filter. For each request it:

1. Extracts the bearer token from the `Authorization` header.
2. Rejects it if `TokenBlacklistService` says it was revoked.
3. Verifies its signature and expiration through `JwtService`.
4. Loads the account through `CustomUserDetailsService`.
5. Places an authenticated `UsernamePasswordAuthenticationToken` in
	 `SecurityContextHolder`.

After that, controllers can rely on Spring Security's authenticated context
instead of parsing JWTs themselves. The filter is a Spring component and is
registered before `UsernamePasswordAuthenticationFilter` by `SecurityConfig`.

### Refresh tokens and logout

`RefreshTokenService` manages the longer-lived refresh token stored in the
database. On login it associates the generated token with the account. On
refresh it:

1. Finds the token in `RefreshTokenRepository`.
2. Checks its database expiration time.
3. Loads the associated account.
4. Creates a new access token and refresh token.
5. Replaces the stored token through rotation.

This means an old refresh token cannot be reused after a successful refresh.
Missing or expired refresh tokens produce `TokenRefreshException`, which is
returned as an unauthorized response.

`TokenBlacklistService` keeps revoked access tokens in memory. Logging out
with an access-token header adds that token to the blacklist and deletes the
user's refresh token. The blacklist is deliberately simple for this template;
production deployments should use a shared store such as Redis and should
expire blacklist entries when the corresponding JWT expires.

## Request Examples

### Login

```http
POST /api/auth/login
Content-Type: application/json

{
	"loginId": "jane@example.com",
	"password": "correct-password"
}
```

Example response:

```json
{
	"accessToken": "<jwt>",
	"refreshToken": "<jwt>",
	"tokenType": "Bearer",
	"expiresIn": 900
}
```

### Refresh

```http
POST /api/auth/refresh
Content-Type: application/json

{
	"refreshToken": "<refresh-jwt>"
}
```

### Logout

The usual form sends the access token in the header:

```http
POST /api/auth/logout
Authorization: Bearer <access-jwt>
```

If a client cannot send the header, it can send either token in the body:

```json
{
	"accessToken": "<access-jwt>",
	"refreshToken": "<refresh-jwt>"
}
```

## Security Configuration

The application is stateless: `SecurityConfig` disables CSRF for this token-
based API, uses `SessionCreationPolicy.STATELESS`, permits the three auth
endpoints, and requires authentication for every other endpoint. The H2
console is also permitted for local development and configured to allow its
same-origin frame.

The local configuration in `application.properties` uses an in-memory H2
database and creates/updates the schema automatically. JWT settings include:

- `app.jwt.secret`: Base64-encoded signing key.
- `app.jwt.access-token-expiration`: access-token lifetime in milliseconds.
- `app.jwt.refresh-token-expiration`: refresh-token lifetime in milliseconds.

Replace the development secret with a strong secret supplied through a secure
environment-specific configuration before deploying.

## Running the Template

From the `account_login_logout` directory:

```bash
./gradlew bootRun
```

To run the tests and build the project:

```bash
./gradlew build
```

The template currently does not include a registration endpoint or seed
account. Add an account with a BCrypt-encoded password before testing login,
or add a registration/data-initialization flow that uses the configured
`PasswordEncoder`.

# UserAuthService API

Spring Boot 3.3 / Java 17 authentication service: registration, JWT login, profile management, password
reset, logout, and token introspection for other microservices. MySQL via Spring Data JPA + Flyway.

## Run

```bash
# MySQL must be reachable (defaults: localhost:3306/user_auth_service, root/password;
# override with DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD)
./mvnw spring-boot:run          # http://localhost:8082
```

Set a real signing key outside local dev — tokens are only verifiable while this value is stable:

```bash
export JWT_SECRET=$(openssl rand -base64 32)   # must decode to >= 32 bytes
```

## Test

```bash
./mvnw test                                                  # unit + web-slice tests, no DB needed
./mvnw test -DexcludedGroups=none -Dgroups=integration       # context test against live MySQL
```

## Endpoints

Send the token from `/auth/login` as `Authorization: Bearer <token>`.

| Method | Path | Auth | Request body | Response | Status |
|---|---|---|---|---|---|
| POST | `/auth/signup` | – | `{name, email, password, phoneNumber?}` | user | 201 · 400 · 409 |
| POST | `/auth/login` | – | `{email, password}` | `{token, tokenType, expiresAt, user}` | 200 · 400 · 401 |
| POST | `/auth/validateToken` | – | `{token}` | `{valid, userId?, email?, roles, expiresAt?}` | 200 (always) · 400 |
| POST | `/auth/logout` | Bearer | – | – | 204 · 401 |
| POST | `/auth/forgot-password` | – | `{email}` | `{message}` | 200 (always) · 400 |
| POST | `/auth/reset-password` | – | `{token, newPassword}` | `{message}` | 200 · 400 |
| GET | `/users/me` | Bearer | – | user | 200 · 401 · 404 |
| PATCH | `/users/me` | Bearer | `{name?, phoneNumber?}` | user | 200 · 400 · 401 · 409 |
| GET | `/actuator/health` | – | – | `{status}` | 200 |

Errors share one shape: `{status, error, message, timestamp, fieldErrors?}`.

### Token validation from other services

`POST /auth/validateToken` is the introspection endpoint: the signing key never leaves this service. A valid
token returns the user's id, email and roles; any malformed, forged, expired or revoked token returns
`{"valid": false}` with HTTP 200.

### Sessions

Each login creates a `user_session` row holding a SHA-256 of the token and its expiry. Logout, expiry and
password reset mark the session `INACTIVE`, so a token can be revoked server-side before its `exp`. Session
lifetime is `jwt.expiration` (default 24h).

### Password reset

`/auth/forgot-password` always answers 200. Email delivery is a logging stub (`LoggingEmailService`) — the
reset link is printed to the application log. Provide an SMTP-backed `IEmailService` before deploying.

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `jwt.secret` | dev value (`JWT_SECRET`) | Base64 HS256 key, >= 32 bytes |
| `jwt.expiration` | `24h` (`JWT_EXPIRATION`) | token + session lifetime |
| `jwt.issuer` | `user-auth-service` | `iss` claim |
| `session.cleanup-interval` | `PT1H` | sweep of expired sessions |
| `password-reset.expiration` | `15m` | reset-token lifetime |
| `password-reset.link-base-url` | `http://localhost:3000/reset-password?token=` | prefix for the emailed link |

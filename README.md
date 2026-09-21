# UserAuthService

Spring Boot authentication service for a microservice setup: registration, JWT login, profile management,
password reset, logout, and a token-introspection endpoint that other services call to verify tokens.
Backed by MySQL via Spring Data JPA, with Flyway managing the schema.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java (JDK) | 17 | e.g. `sdk install java 17.0.20-tem` with [SDKMAN](https://sdkman.io) |
| MySQL | 8.x | running locally on `3306`, or reachable via the `DB_*` env vars below |
| Maven | – | not required; the project ships the wrapper (`./mvnw`) |
| `openssl` | any | only to generate a signing key for non-dev environments |

## First-time setup

1. **Create the database** (Flyway creates all tables on first start; nothing else to run by hand):

   ```sql
   CREATE DATABASE user_auth_service;
   ```

2. **Point the app at it.** Local defaults work out of the box (`localhost:3306`, `root`/`password`).
   Anything else goes through environment variables:

   | Env var | Default |
   |---|---|
   | `DB_HOST` | `localhost` |
   | `DB_PORT` | `3306` |
   | `DB_NAME` | `user_auth_service` |
   | `DB_USERNAME` | `root` |
   | `DB_PASSWORD` | `password` |

3. **Set a signing key outside local dev.** A dev key is committed in `application.properties`; anywhere
   shared, override it. Tokens are only verifiable while this value stays the same across restarts:

   ```bash
   export JWT_SECRET=$(openssl rand -base64 32)   # must decode to >= 32 bytes; the app refuses weaker keys
   ```

## Run

```bash
./mvnw spring-boot:run        # starts on http://localhost:8082
curl localhost:8082/actuator/health   # {"status":"UP"}
```

To use another port: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8083`.

## Test

```bash
./mvnw test                                              # unit + web-slice tests; no database needed
./mvnw test -DexcludedGroups=none -Dgroups=integration   # Spring context test against your MySQL
```

## Endpoints

Every response is JSON. Authenticated routes expect the token from `/auth/login` as
`Authorization: Bearer <token>`.

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

`user` is `{id, name, email, phoneNumber, roles}`. Errors share one shape:
`{status, error, message, timestamp, fieldErrors?}` — `fieldErrors` is a `field -> message` map on validation
failures. Login returns the same `401 "Invalid email or password"` for an unknown email and a wrong password.

### Quick walkthrough

```bash
B=http://localhost:8082

# 1. register
curl -s $B/auth/signup -H 'Content-Type: application/json' \
  -d '{"name":"Alice","email":"alice@example.com","password":"secret123"}'

# 2. log in and keep the token
TOKEN=$(curl -s $B/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"secret123"}' | jq -r .token)

# 3. read / update the profile
curl -s $B/users/me -H "Authorization: Bearer $TOKEN"
curl -s -X PATCH $B/users/me -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Alicia"}'

# 4. what another microservice does to check a token
curl -s $B/auth/validateToken -H 'Content-Type: application/json' -d "{\"token\":\"$TOKEN\"}"

# 5. log out; the token is now rejected everywhere
curl -s -X POST $B/auth/logout -H "Authorization: Bearer $TOKEN"
```

### Password reset

`POST /auth/forgot-password` always answers 200 with the same message, whether or not the email exists.
Email delivery is currently a stub: `LoggingEmailService` prints the reset link to the application log
(`[EMAIL STUB] Password reset link for ...`). Copy the token from there and call `/auth/reset-password`.
Provide an SMTP-backed `IEmailService` before deploying anywhere real.

## Architecture

Classic layered Spring Boot app, package `com.azneotech.userauthservice`:

```
controllers   AuthController (/auth/*), UserController (/users/me), ControllerAdvisor (error JSON)
     |
services      IAuthService, IUserService, IPasswordResetService, IJwtService, IEmailService
     |         (interface + impl pairs; all business rules live here)
repos         UserRepo, RoleRepo, SessionRepo, PasswordResetTokenRepo  (Spring Data JPA)
     |
models        User, Role, UserSession, PasswordResetToken  (all extend BaseModel: id, audit dates, status)

security      JwtAuthenticationFilter + AuthenticatedUser principal + JSON 401 entry point
configs       AuthConfig (security chain), AppConfig (properties, Clock), JpaAuditingConfig, SchedulingConfig
dtos          request/response shapes with jakarta.validation annotations
jobs          SessionCleanupJob (hourly sweep of expired sessions)
```

### How a request is authenticated

```
client ── Authorization: Bearer <jwt> ──> JwtAuthenticationFilter
                                              │  IAuthService.validateToken(token)
                                              │    1. verify signature / expiry / issuer   (JwtService)
                                              │    2. look up user_session by SHA-256(token)
                                              │    3. require session ACTIVE and user ACTIVE
                                              ▼
                              SecurityContext principal = AuthenticatedUser(userId, email, sessionId)
                                              ▼
                              controller reads it via @AuthenticationPrincipal
```

No token, or a bad one, means the request stays anonymous and the security rules return a 401 JSON body.
Only signup, login, validateToken, the two password-reset routes and `/actuator/health` are public.

### Tokens and sessions

- JWTs are HS256, signed with `jwt.secret`, and carry `jti`, `sub` (user id), `iss`, `iat`, `exp`, `email`,
  `roles`. Default lifetime 24h.
- Every login inserts a `user_session` row storing a **SHA-256 of the token** (never the raw token) and its
  expiry. Logout, expiry and password reset set the row to `INACTIVE`, so a token can be revoked before its
  `exp`. Rows are never deleted.
- Other services don't need the key: they `POST /auth/validateToken` and get back the user's id, email and
  roles (or `{"valid": false}` for anything malformed, forged, expired or revoked).

### Database

Schema lives in `src/main/resources/migration/V<n>__*.sql` and is applied by Flyway at startup; Hibernate
runs in `validate` mode and never changes the schema itself. Tables: `user`, `role`, `user_roles`,
`user_session`, `password_reset_token`, plus Flyway's `flyway_schema_history`.

Never edit a migration that has already run — add a new `V<n>` file instead.

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `jwt.secret` | dev value (`JWT_SECRET`) | Base64 HS256 key, >= 32 bytes |
| `jwt.expiration` | `24h` (`JWT_EXPIRATION`) | token + session lifetime |
| `jwt.issuer` | `user-auth-service` | `iss` claim |
| `session.cleanup-interval` | `PT1H` | how often expired sessions are swept to `INACTIVE` |
| `password-reset.expiration` | `15m` | reset-token lifetime |
| `password-reset.link-base-url` | `http://localhost:3000/reset-password?token=` | prefix for the emailed link |

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Port 8082 was already in use` | another instance is running (often from the IDE); stop it or pass `--server.port=8083` |
| `Migration checksum mismatch for migration version N` | a migration file that already ran was edited; restore its original content |
| `Cannot drop table 'user' referenced by a foreign key constraint` | drop children first: `DROP TABLE password_reset_token, user_session, user_roles, user, role, flyway_schema_history;` — or simply drop and recreate the database |
| `WeakKeyException` at startup | `JWT_SECRET` decodes to fewer than 32 bytes; regenerate with `openssl rand -base64 32` |
| Tokens stop validating after a restart | `JWT_SECRET` changed between runs; keep it stable |

# UserAuthService

Spring Boot authentication service for a microservice setup: registration, JWT login, profile management,
password reset, logout, and a token-introspection endpoint that other services call to verify tokens.
Backed by MySQL via Spring Data JPA, with Flyway managing the schema.

## Scope

Registration is **email and password only**. Social-media sign-up (Google, Facebook, etc.) is out of scope for now.
Also deferred: change password while logged in, email change and verification, refresh tokens, account
deactivation, general role management (`ROLE_ADMIN` can be granted, nothing else), and login rate limiting.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java (JDK) | 17 | e.g. `sdk install java 17.0.20-tem` with [SDKMAN](https://sdkman.io) |
| MySQL | 8.x | running locally on `3306`, or reachable via the `DB_*` env vars below |
| Kafka | any | `localhost:9092` for welcome and password-reset emails; optional, since publishing is best-effort |
| Docker | any | optional: `docker-compose.yml` starts MySQL and Kafka for you (see First-time setup) |
| Maven | – | not required; the project ships the wrapper (`./mvnw`) |
| `openssl` | any | only to generate a signing key for non-dev environments |

## First-time setup

**Shortcut: let Docker provide the dependencies.** `make infra-up` (or `docker compose up -d --wait`) starts MySQL
with an empty `user_auth_service` database on `localhost:3307`, and Kafka on `localhost:9092` with the `signup` and
`password-reset` topics plus a UI at http://localhost:8090. Then set `DB_PORT=3307` in `.env` (see below) and skip
step 1. `make infra-down` stops it keeping the data; `make infra-reset` also deletes the data. It uses the same
ports as the monorepo's root `docker-compose.yml`, so run only one of them. Eureka is not part of it: start
`ServiceDiscoveryApplication` yourself, or run without it and ignore the registration warnings.

1. **Create the database** (Flyway creates all tables on first start; nothing else to run by hand):

   ```sql
   CREATE DATABASE user_auth_service;
   ```

2. **Point the app at it.** Local defaults work out of the box (`localhost:3306`, `root`/`password`).
   Anything else goes through environment variables or a `.env` file (see Run):

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
./mvnw spring-boot:run        # starts on http://localhost:8001
curl localhost:8001/actuator/health   # {"status":"UP"}
```

Or with the Makefile (`make jar`, then `make run`; `make test`, `make clean`). `SKIP_TESTS=false make jar` runs
the tests during the build.

Both read a gitignored `.env` in the project directory: `application.properties` imports it
(`spring.config.import=optional:file:.env[.properties]`) and the Makefile also sources it, so
`./mvnw spring-boot:run`, `make run` and `java -jar` all pick up the same values. Copy the template and edit it:

```bash
cp .env.example .env   # DB_*, JWT_SECRET, JWT_EXPIRATION, SERVER_PORT
```

Use unquoted values (the file is parsed as `.properties` by Spring). A variable set in the real environment
overrides `.env`, and the defaults in the table above apply to anything it leaves out. Without a `.env` nothing
changes.

| Run | Port | How |
|---|---|---|
| Single instance (default) | `8001` | `./mvnw spring-boot:run` or `make run` |
| Second instance | `9001` | `SERVER_PORT=9001 ./mvnw spring-boot:run` or `SERVER_PORT=9001 make run` |

The service registers with Eureka (`http://localhost:8761/eureka/`, start `ServiceDiscoveryApplication` first)
as `user-auth-service`. A second instance registers under the same name on its own port, and callers such as
`ProductCatalogService` (`http://user-auth-service/users/{id}`) are load-balanced across both.

### Run in Docker

The `Dockerfile` containerizes only this service (multi-stage: JDK build, JRE runtime, non-root). MySQL, Kafka and
Eureka are not in the image; point it at them with environment variables. Inside a container `localhost` is the
container itself, so the host and broker addresses must be overridden. With the compose stack running
(`make infra-up`) and Docker Desktop/OrbStack's `host.docker.internal`:

```bash
docker build -t user-auth-service .
docker run --rm -p 8001:8001 \
  -e DB_HOST=host.docker.internal -e DB_PORT=3307 \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  -e EUREKA_CLIENT_ENABLED=false \
  user-auth-service
```

Other variables: `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (set your own anywhere shared),
`JWT_EXPIRATION`, `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`. `.env` is excluded from the image (`.dockerignore`), so
pass values with `-e` or `--env-file .env` (unquoted values only). The image has a health check on
`/actuator/health`.

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
| GET | `/users/{id}` | – | – | user | 200 · 404 |
| GET | `/users/me` | Bearer | – | user | 200 · 401 · 404 |
| PATCH | `/users/me` | Bearer | `{name?, phoneNumber?}` | user | 200 · 400 · 401 · 409 |
| PUT | `/users/{id}/role/admin` | Bearer (`ROLE_ADMIN`) | – | user | 200 · 401 · 403 · 404 |
| GET | `/actuator/health` | – | – | `{status}` | 200 |

`GET /users/{id}` is deliberately public: other services (e.g. `ProductCatalogService`) call it by Eureka name
(`http://user-auth-service/users/{id}`) to exercise the service-discovery flow.

`PUT /users/{id}/role/admin` grants `ROLE_ADMIN` and is idempotent (an existing admin is returned unchanged).
When the role is newly granted, the user's active sessions are revoked: roles are baked into the token, so they
must log in again to get one that carries `ROLE_ADMIN`.

The first admin comes from migration `V6__seed_admin_user.sql`: `azad.admintest1@yopmail.com` / `password`, with
`ROLE_USER` and `ROLE_ADMIN`. The migration skips the insert if that email already exists. **That is a known
password committed to the repo**, and the mailbox is a public disposable inbox, so anyone can also take the
account over through `/auth/forgot-password`. Fine for local development; before this runs anywhere shared,
change the hash in V6 (if it hasn't run yet) or delete the user and create your own. To make someone else the
first admin by hand instead:

```sql
INSERT INTO user_roles (user_id, roles_id)
SELECT u.id, r.id FROM `user` u, role r WHERE u.email = 'alice@example.com' AND r.value = 'ROLE_ADMIN';
```

`user` is `{id, name, email, phoneNumber, roles}`. Errors share one shape:
`{status, error, message, timestamp, fieldErrors?}` — `fieldErrors` is a `field -> message` map on validation
failures. Login returns the same `401 "Invalid email or password"` for an unknown email and a wrong password.

### Quick walkthrough

```bash
B=http://localhost:8001

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

# 6. as an admin (see "The first admin" above), promote user 2; their sessions are revoked, so they log in again
ADMIN=$(curl -s $B/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"azad.admintest1@yopmail.com","password":"password"}' | jq -r .token)
curl -s -X PUT $B/users/2/role/admin -H "Authorization: Bearer $ADMIN"
```

### Bruno collection

`bruno/` is a [Bruno](https://www.usebruno.com) collection covering every endpoint above, plus the error cases.
Open the folder in Bruno and pick the `Local` (8001) or `Local-9001` environment. Signup creates a fresh user and
the later requests reuse its token and id, so run the folders top to bottom (or the whole collection with
`npx @usebruno/cli run -r --env Local` from `bruno/`). `Password Reset/Reset Password` is the one manual step: paste
the token from the reset email into the `resetToken` environment variable first.

### Password reset

`POST /auth/forgot-password` always answers 200 with the same message, whether or not the email exists.
When it does exist, `KafkaEmailService` publishes the reset email (recipient, subject, and a body containing
the link) to the `password-reset` Kafka topic once the reset token is committed; `NotificationService`
consumes it and sends the mail. Delivery is best-effort: if Kafka is unreachable the failure is logged and
the request still answers 200.

Without Kafka (local runs), set `password-reset.delivery=log` (or `PASSWORD_RESET_DELIVERY=log`).
`LoggingEmailService` then prints the link to the application log (`[EMAIL STUB] Password reset link for ...`),
which you copy into `/auth/reset-password`. It logs a live credential, so never use it in a shared environment.

### Welcome email

Signup saves the user first, then publishes a welcome email to the `signup` Kafka topic after the transaction
commits. Like the reset email, it is best-effort: a Kafka outage is logged and signup still returns 201.
Producer sends wait at most 3s for the broker (`spring.kafka.producer.properties.max.block.ms`).

## Architecture

Classic layered Spring Boot app, package `com.azneotech.userauthservice`:

```
controllers   AuthController (/auth/*), UserController (/users/*, incl. admin promotion), ControllerAdvisor (error JSON)
     |
services      IAuthService, IUserService, IPasswordResetService, IJwtService, IEmailService (Kafka by default)
     |         (interface + impl pairs; all business rules live here)
repos         UserRepo, RoleRepo, SessionRepo, PasswordResetTokenRepo  (Spring Data JPA)
     |
models        User, Role, UserSession, PasswordResetToken  (all extend BaseModel: id, audit dates, status)

security      JwtAuthenticationFilter + AuthenticatedUser principal + JSON 401 entry point and 403 access-denied handler
configs       AuthConfig (security chain and route rules), AppConfig (properties, Clock), JpaAuditingConfig, SchedulingConfig
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
Only signup, login, validateToken, the two password-reset routes, `GET /users/{id}` and `/actuator/health` are
public. `PUT /users/{id}/role/admin` additionally needs `ROLE_ADMIN` in the token and answers a non-admin with a
403 JSON body.

### Tokens and sessions

- JWTs are HS256, signed with `jwt.secret`, and carry `jti`, `sub` (user id), `iss`, `iat`, `exp`, `email`,
  `roles`. Default lifetime 24h.
- Every login inserts a `user_session` row storing a **SHA-256 of the token** (never the raw token) and its
  expiry. Logout, expiry, password reset and being granted `ROLE_ADMIN` set the row to `INACTIVE`, so a token can be
  revoked before its `exp`. Rows are never deleted.
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
| `password-reset.delivery` | `kafka` | `kafka` publishes the reset email to `NotificationService`; `log` only logs the link (dev) |
| `password-reset.expiration` | `15m` | reset-token lifetime |
| `password-reset.link-base-url` | `http://localhost:3000/reset-password?token=` | prefix for the emailed link |

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `port is already allocated` from `make infra-up` | the root `docker-compose.yml` or `NotificationService/docker-compose.yml` (or a host MySQL/Kafka) already holds `3307`, `9092` or `8090`; stop it, or set `MYSQL_PORT` for MySQL |
| `403 Insufficient permissions` on `/users/{id}/role/admin` | the caller is not an admin. Being promoted revokes your old sessions, so after that log in again for a token that carries `ROLE_ADMIN` (the old one now gets 401) |
| `Port 8001 was already in use` | another instance is running (often from the IDE); stop it, or run a second instance on purpose with `SERVER_PORT=9001` |
| `Migration checksum mismatch for migration version N` | a migration file that already ran was edited; restore its original content |
| `Cannot drop table 'user' referenced by a foreign key constraint` | drop children first: `DROP TABLE password_reset_token, user_session, user_roles, user, role, flyway_schema_history;` — or simply drop and recreate the database |
| `WeakKeyException` at startup | `JWT_SECRET` decodes to fewer than 32 bytes; regenerate with `openssl rand -base64 32` |
| Tokens stop validating after a restart | `JWT_SECRET` changed between runs; keep it stable |

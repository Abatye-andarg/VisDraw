# Account API

The backend supports email/password registration and cookie-based login sessions. The frontend currently displays connection status; account operations are available through this HTTP API.

## Endpoints

| Method | Path | Request | Success |
| --- | --- | --- | --- |
| GET | `/api/auth/csrf` | None | `200` with `{ "headerName": "X-CSRF-TOKEN", "token": "..." }` |
| POST | `/api/auth/register` | JSON with `email` and `password` | `201` with `{ "id": "...", "email": "..." }` |
| POST | `/api/auth/login` | Form-encoded `email` and `password` | `204`; session cookie issued/rotated |
| GET | `/api/auth/me` | Authenticated session cookie | `200` with `{ "id": "...", "email": "..." }` |
| POST | `/api/auth/logout` | Session cookie | `204`; session invalidated |

All POST requests require the session cookie and an `X-CSRF-TOKEN` header. Obtain them from `/api/auth/csrf` first. Fetch a fresh token after login and logout: successful authentication clears the previous CSRF token. Browser clients should use the same origin or a configured reverse proxy; cross-origin access is not enabled.

Registration does not automatically sign in. Login uses `application/x-www-form-urlencoded`, not JSON. Other account responses use JSON. There are no redirects or generated login forms.

## Validation and identity

- Emails are trimmed and lowercased before storage and lookup. A database uniqueness constraint prevents duplicates, including simultaneous registration attempts.
- Passwords require 15–128 UTF-16 code units (ordinary Latin characters count as one). Whitespace-only passwords are rejected. Passwords are never trimmed or normalized.
- Passwords are stored as salted PBKDF2-HMAC-SHA256 hashes using 600,000 iterations and 16-byte random salts. A version prefix identifies the encoder configuration. The API never returns password hashes.
- Account identifiers are UUIDs. `/me` takes its identity from the authenticated session, not a supplied account identifier.

Validation failures return `400`, duplicate emails return `409`, invalid credentials or missing authentication return `401`, and missing/invalid CSRF tokens return `403`. Errors use `application/problem+json`; validation errors include an `errors` map keyed by field name. Incorrect passwords and unknown emails receive the same login error message. Duplicate registration responses reveal whether an email is already registered.

## Sessions

Spring Security rotates the session identifier on login and invalidates it on logout. Sessions expire after 30 minutes of inactivity. Cookies are `HttpOnly` and `SameSite=Lax`. They require HTTPS by default; the `local` profile permits HTTP for local development.

Sessions are held in the backend process. Restarting it signs users out but preserves their accounts in MySQL. Multiple backend instances do not share sessions. Email verification, password reset, and application-level authentication rate limiting are not implemented.

## Try the API

These examples require Bash, curl, and jq, with the local backend running on port 8080. They create a demonstration account; use an address that is not already registered.

```sh
base_url=http://127.0.0.1:8080
cookie_file=$(mktemp)

csrf_token=$(curl --fail --silent --show-error -c "$cookie_file" -b "$cookie_file" \
  "$base_url/api/auth/csrf" | jq -r '.token')

curl --fail-with-body -i -c "$cookie_file" -b "$cookie_file" \
  -H "X-CSRF-TOKEN: $csrf_token" -H 'Content-Type: application/json' \
  --data '{"email":"reader@example.com","password":"A demo passphrase only!"}' \
  "$base_url/api/auth/register"

curl --fail-with-body -i -c "$cookie_file" -b "$cookie_file" \
  -H "X-CSRF-TOKEN: $csrf_token" \
  --data-urlencode 'email=reader@example.com' \
  --data-urlencode 'password=A demo passphrase only!' \
  "$base_url/api/auth/login"

curl --fail-with-body -i -b "$cookie_file" "$base_url/api/auth/me"

csrf_token=$(curl --fail --silent --show-error -c "$cookie_file" -b "$cookie_file" \
  "$base_url/api/auth/csrf" | jq -r '.token')

curl --fail-with-body -i -c "$cookie_file" -b "$cookie_file" \
  -H "X-CSRF-TOKEN: $csrf_token" -X POST "$base_url/api/auth/logout"

# After logout this returns 401.
curl -i -b "$cookie_file" "$base_url/api/auth/me"
rm -- "$cookie_file"
```

Use example credentials only for this walkthrough. The cookie file contains session credentials and belongs outside the repository.

## Implementation and tests

Spring Security handles authentication, password hashing, CSRF, and session lifecycle. Registration uses Spring Data JPA repositories, a Hibernate-mapped `Account` entity, service transactions, and Jakarta Bean Validation. Hibernate updates the schema for local development and validates it in other environments. Lombok supplies constructors and entity getters at compile time. Spring Security and Hibernate Validator are maintained Apache-2.0 projects; the security dependency versions are managed by Spring Boot. PBKDF2 uses the Java cryptography implementation without an additional provider.

From `backend/`, run `bash mvnw verify`. Tests use disposable MySQL databases and exercise registration, validation, password hashing, concurrent duplicate registration, independent user sessions, login failures, session rotation, CSRF protection, logout, and management endpoint restrictions. They do not create accounts in the local Compose database.

References: [Spring Security password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html), [Spring Security CSRF handling](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), and [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

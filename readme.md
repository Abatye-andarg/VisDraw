# Visual Notes

Visual Notes is a personal workspace for written notes, freehand boards, and structured diagrams. It uses a React + TypeScript frontend, an independent Java/Spring Boot backend, and MySQL.

The current interface displays backend connection status. The backend provides account registration, sign-in, sign-out, and current-user APIs, with MySQL persistence through Spring Data JPA and Hibernate. See the [account API documentation](docs/auth-api.md) for request formats and examples.

## Project structure

- `backend/` — Spring Web MVC controllers, services, JPA models and repositories, security, and integration tests.
- `frontend/` — React interface and Vite development server.
- `compose.yaml` — local MySQL service with persistent storage.

Backend Java packages under `com.visualnotes` are organized by responsibility:

| Package | Responsibility |
| --- | --- |
| `controller` | HTTP endpoints and API error responses |
| `service` | Business logic and account operations |
| `model` | JPA entities, persistence mappings, and relationships |
| `repository` | Spring Data JPA repository interfaces |
| `dto` | API request and response data |
| `security` | Authentication, CSRF, and session configuration |
| `exception` | Application exceptions handled by the controller layer |

React supplies the user interface; backend controllers return JSON. General application configuration belongs in `config`; there are currently no classes in that package.

## Prerequisites

- Java JDK 21
- Node.js 24.13 or newer within the 24.x line, with npm
- Docker Engine with Docker Compose and permission to access the Docker daemon
- Network access for initial dependency and container-image downloads

With nvm installed, run `nvm install` and `nvm use` from the repository root. The Maven wrapper supplies Maven 3.9.16; a separate Maven installation is unnecessary. Windows users can use `mvnw.cmd` instead of `bash mvnw` and set environment variables using their shell.

## Local setup

From the repository root, create local configuration if `.env` does not already exist:

```sh
cp .env.example .env
```

Set `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` in `.env` to different random values. `openssl rand -hex 24` can generate each value. Use plain, unquoted `KEY=value` entries (hexadecimal passwords work with both Compose and Spring). Avoid shell commands, `export`, quotes, or variable expansion in this shared file. `.env` is ignored by Git; `.env.example` contains no credentials. Compose refuses to start with empty passwords.

Start MySQL:

```sh
docker compose up -d --wait mysql
```

MySQL listens only on `127.0.0.1:3307` by default. The application uses a dedicated `visualnotes` database user, not root. Its permissions are scoped to the `visualnotes` database and include local schema updates performed by Hibernate.

In a terminal at the repository root, start the backend:

```sh
cd backend
bash mvnw spring-boot:run
```

The Maven run goal selects the `local` Spring profile, which imports `../.env` directly. No shell exports are required. Run this command from `backend/`; a missing `.env` produces an explicit configuration error. This default applies only to `spring-boot:run`, not tests or the packaged application. To override it, use `-Dspring-boot.run.profiles=your-profile`. The backend defaults to port 8080 and initializes Hibernate before accepting requests. Database connection or schema validation failures prevent startup.

In a second terminal at the repository root:

```sh
cd frontend
npm ci
npm run dev
```

Open http://127.0.0.1:5173. The page checks backend health on load. **Check again** refreshes the result; requests time out after five seconds. Status is not polled continuously.

Vite uses loopback port 5173 and fails if that port is occupied. Its development proxy forwards `/actuator/health` to the backend. `npm run preview` serves the production bundle but does not provide this proxy; deployed frontend hosting needs a corresponding reverse proxy.

`frontend/.npmrc` disables executable symlinks so installation works on filesystems such as exFAT. npm scripts invoke installed tools directly through Node.

## Configuration

| Variable | Purpose |
| --- | --- |
| `DB_PORT` | Local MySQL host port; defaults to `3307` |
| `DB_USERNAME` | Database user; local default is `visualnotes` |
| `DB_PASSWORD` | Required application database password |
| `MYSQL_ROOT_PASSWORD` | Required by Compose to initialize MySQL; not used by the backend |
| `PORT` | Backend HTTP port; defaults to `8080` |
| `DB_URL` | Required JDBC URL when running without the `local` profile |
| `SPRING_PROFILES_ACTIVE` | Spring profile for direct Java/IDE launches; Maven local runs select it automatically |

The `local` profile constructs a loopback JDBC URL from `DB_PORT` and permits unencrypted local connections. For another environment, omit that profile and supply `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Configure appropriate TLS in that environment's JDBC URL; local connection settings are not deployment defaults.

To change the backend port, export `PORT=8081` before starting it. Copy `frontend/.env.example` to `frontend/.env.local`, set `BACKEND_URL=http://127.0.0.1:8081`, and restart Vite. `BACKEND_URL` configures the development proxy. Never put secrets in browser-exposed environment variables.

MySQL initialization variables only apply when the data volume is empty. Editing passwords in `.env` does not update existing database accounts; change the account password in MySQL as well.

## Database and schema

The Compose service pins MySQL 8.4.11 and stores data in the Docker-managed `mysql-data` volume. The data is not stored in the source directory. Stop the local service with:

```sh
docker compose down
```

This preserves the volume. Starting the service again reuses the same database. Adding `--volumes` to `down` deletes that database permanently.

JPA entity annotations in `backend/src/main/java/com/visualnotes/model/` define tables and relationships. The `accounts` table stores normalized unique email addresses, password hashes, generated UUID identifiers, and creation timestamps.

The `local` profile uses `spring.jpa.hibernate.ddl-auto=update`, so Hibernate creates or adjusts tables during startup and keeps existing rows. Automatic updates do not reliably handle changes such as renaming columns; review those changes explicitly. Other environments use `validate`: Hibernate checks the existing schema and fails startup if it is incompatible. They require the schema to be provisioned separately. Automatic schema creation with data deletion is not enabled.

Open Session in View is disabled (`spring.jpa.open-in-view=false`). Services define transaction boundaries, and controllers return DTOs rather than persistence entities. MySQL 8.4 uses `utf8mb4` by default; the email mapping specifies binary collation for the already-normalized values.

To inspect the local database, use the password from `.env` at the interactive prompt:

```sh
docker compose exec mysql mysql -u visualnotes -p visualnotes
```

Then run:

```sql
SHOW TABLES;
DESCRIBE accounts;
```

## Tests and builds

With Docker running:

```sh
cd backend
bash mvnw verify
```

Tests create a disposable MySQL container on a dynamically assigned port. They do not use the Compose database or require the local `.env` file. The suite verifies HTTP health, restricted management exposure, Hibernate schema creation, JPA persistence, and compatibility with an existing account table. Account tests also cover validation, password hashing, concurrent duplicate registration, session isolation and rotation, CSRF protection, and logout. Missing Docker access fails the suite rather than silently skipping database tests.

Frontend checks:

```sh
cd frontend
npm run lint
npm run build
```

With the backend running:

```sh
curl --fail http://127.0.0.1:8080/actuator/health
```

Expect JSON containing `"status":"UP"`; database health contributes to the result. Component and connection details are hidden. With Vite running, the same endpoint is accessible through `http://127.0.0.1:5173/actuator/health`.

The backend build creates `backend/target/visualnotes-0.0.1-SNAPSHOT.jar`. For a local packaged run, start from the repository root:

```sh
cd backend
java -jar target/visualnotes-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

For an IDE launch, select the `local` Spring profile once in its run configuration and use `backend/` as the working directory. Packaged deployments without that profile use externally supplied database configuration.

## Main dependencies

| Technology | Responsibility |
| --- | --- |
| Spring Boot and Spring Web MVC | Application configuration, REST controllers, and HTTP handling |
| Spring Security | Authentication, session management, password encoding, and CSRF protection |
| Spring Data JPA | Repository implementations and persistence operations |
| Hibernate ORM | JPA entity mapping, database access, and schema handling |
| Lombok | Compile-time getters and constructors |
| Jakarta Bean Validation / Hibernate Validator | Request validation |
| MySQL Connector/J | Communication with MySQL |
| Testcontainers and JUnit | Integration tests using disposable MySQL databases |
| React, TypeScript, and Vite | Browser interface and frontend tooling |

Spring Boot manages compatible backend dependency versions; `frontend/package-lock.json` records frontend dependency versions. Lombok is configured as a Maven annotation processor and excluded from the packaged application. Enable Lombok support and annotation processing in your IDE if it does not detect generated methods.

References: [Spring Boot SQL/JPA support](https://docs.spring.io/spring-boot/reference/data/sql.html), [Spring Security](https://docs.spring.io/spring-security/reference/), and [Lombok Maven setup](https://projectlombok.org/setup/maven).

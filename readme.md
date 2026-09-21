# Visual Notes

Visual Notes is a personal workspace for written notes, freehand boards, and structured diagrams. It uses a React + TypeScript frontend, an independent Java/Spring Boot backend, and MySQL.

The current interface displays backend connection status. The backend connects to MySQL and validates and applies database migrations at startup.

## Project structure

- `backend/` — Spring Boot HTTP service, database configuration, SQL migrations, and integration tests.
- `frontend/` — React interface and Vite development server.
- `compose.yaml` — local MySQL service with persistent storage.

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

MySQL listens only on `127.0.0.1:3307` by default. The application uses a dedicated `visualnotes` database user, not root. Its permissions are scoped to the `visualnotes` database and include the schema changes required by Flyway.

In a terminal at the repository root, start the backend:

```sh
cd backend
bash mvnw spring-boot:run
```

The Maven run goal selects the `local` Spring profile, which imports `../.env` directly. No shell exports are required. Run this command from `backend/`; a missing `.env` produces an explicit configuration error. This default applies only to `spring-boot:run`, not tests or the packaged application. To override it, use `-Dspring-boot.run.profiles=your-profile`. The backend defaults to port 8080 and runs migrations before accepting requests. Database connection or migration validation failures prevent startup.

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

## Database and migrations

The Compose service pins MySQL 8.4.11 and stores data in the Docker-managed `mysql-data` volume. The data is not stored in the source directory. Stop the local service with:

```sh
docker compose down
```

This preserves the volume. Starting the service again reuses the same database. Adding `--volumes` to `down` deletes that database permanently.

Flyway loads versioned SQL files from `backend/src/main/resources/db/migration/`. The initial migration sets `utf8mb4` encoding and `utf8mb4_0900_ai_ci` collation (case- and accent-insensitive defaults). It creates no application tables. Flyway maintains its own `flyway_schema_history` table.

Add schema changes as a new `V<number>__description.sql` file. Applied migrations are immutable: Flyway checks their recorded checksums on startup. Basic Spring SQL initialization is disabled, and Flyway's destructive clean operation is disabled. No ORM schema generation is configured.

To inspect the local database, use the password from `.env` at the interactive prompt:

```sh
docker compose exec mysql mysql -u visualnotes -p visualnotes
```

Then run:

```sql
SELECT version, description, success FROM flyway_schema_history;
```

## Tests and builds

With Docker running:

```sh
cd backend
bash mvnw verify
```

Tests create a disposable MySQL container on a dynamically assigned port. They do not use the Compose database or require the local `.env` file. The suite verifies HTTP health and restricted management exposure, migration application, Unicode defaults, repeat execution without reapplying changes, and rejection of modified migration checksums. Missing Docker access fails the suite rather than silently skipping database tests.

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

[Spring Boot](https://spring.io/projects/spring-boot) supplies the HTTP service and health reporting. [Flyway](https://docs.spring.io/spring-boot/how-to/data-initialization.html) manages SQL migrations through Spring Boot's startup integration. [Testcontainers](https://java.testcontainers.org/modules/databases/mysql/) runs database tests against real MySQL. [React](https://github.com/facebook/react) supplies the interface, and [Vite](https://vite.dev/guide/) supplies development and build tooling.

Spring Boot manages backend dependency versions; `frontend/package-lock.json` records frontend dependency versions.

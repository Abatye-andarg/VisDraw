# Visual Notes

A personal workspace for written notes, freehand boards, and structured diagrams.

## Current milestone

Milestone 1 provides an independent Spring Boot backend and a React + TypeScript frontend with a connection-status page. Accounts, editors, and persistence are not implemented yet. MySQL and schema migrations are planned for milestone 2.

`backend/` owns the HTTP service. `frontend/` owns the browser interface. The backend can run and be accessed without React. `HelpForAI` is external communication material and is not part of this project.

## Prerequisites

- Java JDK 21 (`java -version`)
- Node.js 24.13 or newer within the 24.x line, with npm (`node --version`)
- Network access for the first dependency download

With nvm installed, run `nvm install` and `nvm use` from this directory. The Maven wrapper pins Maven 3.9.16, so a separate Maven installation is unnecessary. Windows users can use `mvnw.cmd` instead of `bash mvnw`.

## Run locally

From the repository root, in one terminal:

```sh
cd backend
bash mvnw spring-boot:run
```

In another terminal, also starting at the repository root:

```sh
cd frontend
npm ci
npm run dev
```

Open http://127.0.0.1:5173. The page checks the backend on load and displays **Connected** when it receives a healthy response. Use **Check again** to refresh the status. The request times out after five seconds; this page does not poll continuously.

The backend defaults to port 8080. Vite listens on loopback port 5173 and fails if that port is occupied, instead of silently choosing another one.

The project currently resides on an exFAT drive. `frontend/.npmrc` disables executable symlinks, and npm scripts invoke the installed tools through Node directly. This also works on ordinary filesystems.

## Configuration

To change the backend port:

```sh
cd backend
PORT=8081 bash mvnw spring-boot:run
```

In `frontend/`, copy `.env.example` to `.env.local` and set `BACKEND_URL=http://127.0.0.1:8081`, then restart Vite. This variable configures the development proxy and is not a browser secret. Never put secrets in frontend environment variables or commit local environment files.

Vite proxies `/actuator/health` to the backend during development, avoiding the need for a permissive CORS policy. Production hosting and reverse-proxy configuration are outside this milestone. `npm run preview` previews static build output; it does not provide this development proxy.

## Verification

Backend integration tests and executable JAR build:

```sh
cd backend
bash mvnw verify
```

Frontend lint, TypeScript checks, and production bundle:

```sh
cd frontend
npm run lint
npm run build
```

While the backend is running:

```sh
curl --fail http://127.0.0.1:8080/actuator/health
curl --fail http://127.0.0.1:5173/actuator/health
```

Both should return JSON with `"status":"UP"` (health-group metadata may also appear); the second also requires Vite. Only the health endpoint is exposed, without component details. Backend integration tests exercise real HTTP requests on a random port and check that `/actuator/env` is unavailable.

In the browser:

1. Start both services and confirm **Connected**.
2. Stop the backend and click **Check again**. Confirm **Unable to connect**, with a retry button.
3. Restart the backend and retry. Confirm **Connected** returns without reloading.

A previously successful status remains until the next check. It is not a continuous availability monitor.

To run the packaged backend independently:

```sh
java -jar backend/target/visualnotes-0.0.1-SNAPSHOT.jar
```

## Tool choices

- [Spring Boot](https://spring.io/projects/spring-boot) 4.1.1 supplies application configuration and the HTTP server. [Actuator](https://docs.spring.io/spring-boot/api/rest/actuator/health.html) supplies health reporting without a custom endpoint. Spring Boot is maintained by the Spring project and licensed under Apache-2.0.
- [React](https://github.com/facebook/react) supplies the interface, and [Vite](https://vite.dev/guide/) supplies the development server and production bundler. Both are actively maintained and MIT licensed. The development proxy is not a production deployment solution.
- TypeScript provides static checking (Apache-2.0); the Vite template includes Oxlint for linting (MIT). npm's lockfile records exact frontend dependency versions.

These dependency licenses do not choose a license for Visual Notes itself. No editor, routing, state-management, or database libraries are introduced in this milestone.

## Git checkpoint

After verification, review `git status` and commit this foundation as:

```text
chore: initialize backend and frontend foundation
```

Push the verified checkpoint to the configured remote when ready. Build outputs, installed dependencies, and local configuration are ignored; project Markdown documentation is tracked normally.

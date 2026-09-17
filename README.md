# Task Management REST API Backend Service

A JWT-secured REST API for managing projects and tasks — ownership-scoped
access, a validated task-status workflow (not just a free-text field),
filtering/sorting/pagination on the task list, and OpenAPI docs.

## Why this design

**Status is a state machine, not a string.** A task's status field could
be set to any value on any request, but that's not how task tracking
actually works — a DONE task shouldn't silently become TODO again by
someone fat-fingering a PATCH request. `TaskService` keeps an explicit
allowed-transitions map (`TODO → IN_PROGRESS/CANCELLED`, `IN_PROGRESS →
DONE/CANCELLED/TODO`, `DONE`/`CANCELLED` terminal) and rejects anything
outside it with `409 Conflict`, not `500` or a silently-accepted bad write.

**Authorization is ownership-based, not just role-based.** Beyond
USER/ADMIN roles, a regular user can only edit projects they own, and only
create tasks under projects they own; task status can be changed by the
task's creator, its assignee, or an admin, but task *details* (title,
description, reassignment) only by the creator or an admin. This is checked
in the service layer (not just hidden behind which endpoints exist), so it's
covered by tests independent of the HTTP layer.

**Optimistic locking on Task** (`@Version`) — two people editing the same
task's status at once get a clean conflict on the losing write instead of
a silent lost update.

## Architecture

```
Client
  |  Authorization: Bearer <JWT>
  v
JwtAuthFilter  -- validates token, loads UserDetails, populates SecurityContext
  |
  v
Controllers (Auth / Project / Task)
  |
  v
Services
  AuthService     -- register (BCrypt-hash password), login (issues JWT)
  ProjectService   -- CRUD + ownership check (reused by TaskService)
  TaskService        -- CRUD, dynamic filtered/paginated search, status
                         workflow validation, ownership/assignment checks
  |
  v
Repositories (Spring Data JPA)
  TaskRepository extends JpaSpecificationExecutor -- dynamic filter combos
  (status/priority/project/assignee) without a repository method per combo
  |
  v
PostgreSQL (H2 for tests)
```

## Tech stack

- Java 17, Spring Boot 3.2 (Web, Data JPA, Security, Validation)
- JWT auth via jjwt
- springdoc-openapi (Swagger UI)
- PostgreSQL (H2 for the test suite)
- JUnit 5, Mockito, Spring Security Test, MockMvc

## Project layout

```
src/main/java/com/jashleen/taskmanagement/
  model/        User (implements UserDetails), Project, Task + status/priority/role enums
  dto/          Request/response records (Auth, Project, Task)
  security/     JwtService, JwtAuthFilter, CustomUserDetailsService
  config/       SecurityConfig (stateless JWT filter chain), OpenApiConfig
  repository/   Spring Data repos + TaskSpecifications (dynamic filtering)
  service/      AuthService, ProjectService, TaskService (all the business rules live here)
  controller/   AuthController, ProjectController, TaskController
  exception/    Domain exceptions + GlobalExceptionHandler (-> proper HTTP status codes)
src/test/java/...
  service/TaskServiceTest              status-transition + authorization unit tests (Mockito)
  controller/TaskFlowIntegrationTest   full-stack test: real Spring context, real H2 DB,
                                        real Spring Security chain, over actual HTTP requests
```

## API

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | public | Create an account, returns a JWT |
| POST | `/api/auth/login` | public | Returns a JWT |
| POST | `/api/projects` | required | Create a project (caller becomes owner) |
| GET | `/api/projects` | required | List your projects (all, if admin) |
| GET/PUT/DELETE | `/api/projects/{id}` | required, owner/admin | |
| POST | `/api/tasks` | required, project owner/admin | Create a task under a project |
| GET | `/api/tasks?status=&priority=&projectId=&assigneeId=&page=&size=&sort=` | required | Filtered, paginated, sortable search |
| GET | `/api/tasks/{id}` | required | |
| PUT | `/api/tasks/{id}` | required, creator/admin | Update task details |
| PATCH | `/api/tasks/{id}/status` | required, creator/assignee/admin | Validated status transition |
| DELETE | `/api/tasks/{id}` | required, creator/admin | |

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Running it

```bash
docker compose up --build
```

Or locally against H2/an existing Postgres — point `application.yml` at it
and `mvn spring-boot:run`.

```bash
# Register + capture the JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"jordan","password":"password123","email":"jordan@example.com"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# Create a project
curl -s -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Q3 Launch","description":"Launch prep"}'

# Create a task, then move it through its lifecycle
curl -s -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"Write announcement","priority":"HIGH","projectId":1}'

curl -s -X PATCH http://localhost:8080/api/tasks/1/status \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"status":"IN_PROGRESS"}'

# This one gets rejected once the task reaches DONE (409 Conflict)
curl -s -X PATCH http://localhost:8080/api/tasks/1/status \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"status":"TODO"}'
```

`mvn test` runs the full suite (unit + full-stack integration) against H2 —
no Postgres or Docker needed for tests.

## Possible extensions

- Multi-user projects (a membership table instead of single-owner) — the
  ownership check in `ProjectService.findOwnedOrAdmin` is the one place
  that would need to grow into a membership check.
- Refresh tokens (current JWTs are access-token-only, 24h expiry).
- Task comments/activity log.
- Rate limiting on `/api/auth/login`.

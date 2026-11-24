# TaskFlow AI — Phase 3.0: Identity foundation

Phase 3.0 turns the single-user application into a multi-user one. It comes before the rest of Phase 3
because user-level authorization (3.3), isolated memory (3.4) and per-user governance (3.5) all need to know
who the caller is, and because evaluations should measure the system in the shape it will keep.

**Rule of the phase:** the user id comes from the validated access token, never from a request body, a path
variable or an argument the LLM generated.

## What changed

| Area | Change |
|---|---|
| Identity provider | Keycloak 26 in `infrastructure/docker-compose.yml`, realm `taskflow` imported from `infrastructure/keycloak/taskflow-realm.json` with demo users **alice/alice** and **bob/bob** |
| Backend security | Spring Security OAuth2 resource server (`SecurityConfig`): every `/api/**` request needs a Keycloak JWT; issuer, signature, expiry and audience (`taskflow-api`) are validated. Stateless, no session, no CSRF (bearer tokens only). Swagger stays public |
| Trusted identity | `CurrentUserProvider` reads the validated token from the security context and maps its `sub` to a TaskFlow user (`UserAccountService`, created just in time on the first request). It fails closed if no identity is present |
| Data ownership | Flyway `V3__add_user_identity.sql`: `app_users`, `tasks.owner_id`, one `user_preferences` row per user, `ai_conversations` |
| Services | `TaskService`, `DashboardService`, `PreferencesService` take the owner id as their first argument; every query is scoped by it (`TaskSpecifications.ownedBy`, `findByIdAndOwnerId`, ...) |
| Not found vs forbidden | Another user's task or conversation returns **404**, the same as one that does not exist, so ids cannot be used to probe other users' data |
| AI tools | `TaskTools` and `TaskQueryTools` take no user id; they ask `CurrentUserProvider`. A task id from another user (for example injected into a prompt) is "not found" for the tool |
| Conversations | The server issues conversation ids (`POST /api/ai/conversations`) and checks ownership before any agent runs. Chat memory is keyed by `ownerId:conversationId` (`ConversationRef.memoryKey()`) as a second line of defence |
| Frontend | Sign-in with Authorization Code + PKCE (`lib/auth/oidc.ts`, no extra dependency), `AuthGate` around the app, bearer token on every API call, user name and sign-out in the top bar |

### Why tools see the right user

`CurrentUserProvider` reads Spring Security's thread-bound context. The Phase 2 supervisor runs its sub-agents
and their tools synchronously on the request thread (the `AgentRunRecorder` already relies on this), so every
tool call sees the caller. If a later phase moves agents or tools to other threads, the provider throws instead
of guessing; the identity must then be passed explicitly (for example in a governance/execution context).

## Running it

```bash
cd infrastructure
docker compose up -d            # PostgreSQL + Keycloak (http://localhost:8180, admin/admin)

cd ../backend
mvn spring-boot:run             # http://localhost:8080

cd ../frontend
cp .env.example .env.local      # includes the NEXT_PUBLIC_KEYCLOAK_* settings
npm run dev                     # http://localhost:3000 -> redirects to the Keycloak sign-in page
```

Sign in as `alice` / `alice` or `bob` / `bob`. Existing tasks and preferences from Phase 0–2 belong to alice
after the migration (her Keycloak user id is fixed in the realm file and in V3).

### Calling the API with curl or Swagger (development only)

The realm has a second client, `taskflow-dev-cli`, with the password grant enabled for local testing:

```bash
TOKEN=$(curl -s -d client_id=taskflow-dev-cli -d grant_type=password \
  -d username=alice -d password=alice \
  http://localhost:8180/realms/taskflow/protocol/openid-connect/token | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/tasks
CONV=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/ai/conversations | jq -r .conversationId)
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"conversationId\":\"$CONV\",\"message\":\"Plan my day\"}" http://localhost:8080/api/ai/supervisor/chat
```

In Swagger UI (`/swagger-ui.html`), use **Authorize** and paste the token. Never enable the password grant
in production.

### Configuration

| Property | Default | Purpose |
|---|---|---|
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/taskflow` | Token issuer; resolved lazily on the first request |
| `KEYCLOAK_API_AUDIENCE` | `taskflow-api` | Required `aud` claim (added by the audience mapper on both clients) |
| `NEXT_PUBLIC_KEYCLOAK_URL` / `_REALM` / `_CLIENT_ID` | `http://localhost:8180` / `taskflow` / `taskflow-web` | Frontend sign-in |

The removed `taskflow.ai.dev-user-id` property is no longer needed.

## API changes

- Every `/api/**` endpoint now returns **401** without a valid token.
- **New:** `POST /api/ai/conversations` → `201 {"conversationId": "<uuid>"}`.
- `POST /api/ai/chat` and `POST /api/ai/supervisor/chat` require a conversation id from that endpoint; an
  unknown, malformed or foreign id returns **404** `Conversation not found` and never reaches the model.
- Responses are unchanged otherwise.

## Tests

| Test | What it proves |
|---|---|
| `IdentityIsolationIntegrationTest` (new) | No token → 401; bob cannot list, read, update, complete or delete alice's task (404); dashboard and preferences are per user; a conversation is usable only by its creator, and invented ids are rejected before the model is called; bob's TaskAgent cannot complete alice's task even when given its id |
| `TaskRepositoryIntegrationTest` | V3 applied; every query is scoped to the owner while another user's rows sit in the same table |
| `TaskFlowSupervisorTest` | Chat memory is keyed by owner: the same conversation id for another user starts with an empty memory |
| `TaskToolsTest` | Tools pass the authenticated user to the services; no tool has a user or owner parameter; with no identity, no data is touched |
| `TaskServiceTest` | New tasks get the caller as owner; another user's task is "not found"; a missing owner fails instead of running an unscoped query |
| `api-client.test.ts` (frontend) | The bearer token is attached; a 401 starts a new sign-in |

The existing tests were updated to run as alice (`TestUsers.alice()` uses Spring Security's `jwt()`
request post-processor, so no Keycloak is needed in tests).

## Known limitations (deliberate for this phase)

- **Tokens in sessionStorage.** Simple for a tutorial; any XSS could read them. A production setup would use a
  backend-for-frontend with HttpOnly cookies.
- **Keycloak in development mode** (H2 store, HTTP, bootstrap admin). Not a production deployment.
- **No roles yet.** Every authenticated user has the same permissions over their own data; role-based checks
  can come with guardrails/governance.
- **Chat memory is still in process.** Conversations are recorded with their owner, but memory is lost on
  restart; persistent, isolated memory is Phase 3.4.
- **The user cache** (subject → user id) is per instance and unbounded; fine for a demo, worth a bounded cache
  in production.
- **The JWT audience check** is configured but not covered by an automated test (tests inject an already
  validated token). Check it manually by calling the API with a token from a client without the audience mapper.

## Merging with Phase 3.1A

This branch starts from `main` (Phase 2). When combined with the 3.1A evaluation harness:

- Its `AUTHZ-01..04` scenarios (marked `NOT_APPLICABLE` until identity existed) can become executable.
- Harness fixtures that call `TaskService`/`PreferencesService` or construct `Task` need the owner id, and
  scenario runs need an authenticated user (for example `TestUsers.alice()` or a security context set
  around the scenario).
- Expect conflicts in `MultiAgentService`, `AbstractIntegrationTest`, `application.yml` and `pom.xml`.

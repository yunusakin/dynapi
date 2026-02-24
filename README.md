# Dynapi

Dynapi is a dynamic schema-driven backend API built with Spring Boot, MongoDB, Kafka, and JWT.

The main idea:
- Define fields and field groups at runtime.
- Accept dynamic form payloads based on those definitions.
- Query stored records with filters, pagination, and sorting.

## API Base URLs

- Base URL: `http://localhost:8080/api`
- OpenAPI JSON: `http://localhost:8080/api/api-docs`
- Swagger UI: `http://localhost:8080/api/swagger-ui.html`

## Prerequisites

- Java 21
- Docker Desktop
- Maven Wrapper (`./mvnw`)

## 1. Start Local Dependencies

MongoDB:

```bash
docker run -d --name dynapi-mongo -p 27017:27017 mongo:7
```

Kafka (KRaft single-node):

```bash
docker run -d --name dynapi-kafka -p 9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
  -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=true \
  -e CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk \
  confluentinc/cp-kafka:7.6.1
```

## 2. Run the App

```bash
./mvnw spring-boot:run
```

Optional smoke check:

```bash
curl -i http://localhost:8080/api/api-docs
```

## 3. Run Tests

```bash
./mvnw -q clean test
```

## 4. Understand Security First

- Admin schema routes (`/api/admin/**`) require JWT with `ADMIN` role.
- Public routes (`/api/form`, `/api/query/**`) are currently open.
- There is no token-issuing endpoint yet.

For local testing you can generate a JWT externally and use:
- `alg`: `HS256`
- `sub`: any username
- `roles`: include `ADMIN`
- signing secret (decoded text): `dynapi-dev-secret-key-change-me-1234567890`

Then export:

```bash
export ADMIN_TOKEN="<your-jwt-token>"
export BASE_URL="http://localhost:8080/api"
```

## 5. Learn by Using It (End-to-End)

### Step A: Create field definitions (admin)

Create `title` field:

```bash
curl -s -X POST "$BASE_URL/admin/schema/field-definitions" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fieldName": "title",
    "type": "STRING",
    "required": true,
    "version": 1
  }'
```

Create `priority` field:

```bash
curl -s -X POST "$BASE_URL/admin/schema/field-definitions" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fieldName": "priority",
    "type": "NUMBER",
    "required": false,
    "version": 1
  }'
```

### Step B: Create a field group (admin)

This group maps to Mongo collection `tasks`:

```bash
curl -s -X POST "$BASE_URL/admin/schema/field-groups" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "task-form",
    "entity": "tasks",
    "fieldNames": ["title", "priority"],
    "version": 1
  }'
```

### Step C: Publish schema snapshot (admin)

Submit and query now use only `PUBLISHED` schema snapshots.

Publish current group schema:

```bash
curl -s -X POST "$BASE_URL/admin/schema/field-groups/task-form/publish" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Check version history:

```bash
curl -s "$BASE_URL/admin/schema/entities/tasks/versions" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### Step D: Submit a form (public)

```bash
curl -s -X POST "$BASE_URL/form" \
  -H "Content-Type: application/json" \
  -d '{
    "group": "task-form",
    "data": {
      "title": "Ship v1",
      "priority": 1
    }
  }'
```

### Step E: Query submitted data (public)

```bash
curl -s -X POST "$BASE_URL/query/tasks" \
  -H "Content-Type: application/json" \
  -d '{
    "filters": [
      { "field": "priority", "operator": "gte", "value": 1 }
    ],
    "page": 0,
    "size": 10,
    "sortBy": "priority",
    "sortDirection": "DESC"
  }'
```

Supported filter operators include:
- `eq`, `ne`, `gt`, `lt`, `gte`, `lte`, `in`, `regex`, `and`, `or`, `not`

## 6. Main Endpoints

- `POST /api/form` submit dynamic data by group
- `POST /api/forms/{groupId}/submit` submit dynamic data with group in path
- `POST /api/query/{entity}` query dynamic records
- `GET/POST/PUT/DELETE /api/admin/schema/field-definitions*` manage fields
- `GET/POST/PUT/DELETE /api/admin/schema/field-groups*` manage groups
- `POST /api/admin/schema/field-groups/{groupId}/publish` publish immutable schema snapshot
- `GET /api/admin/schema/entities/{entity}/versions` list schema versions
- `POST /api/admin/schema/entities/{entity}/deprecate` deprecate latest published schema

## 7. Configuration

Main config file: `src/main/resources/application.yml`

Key settings:
- MongoDB: `spring.data.mongodb.*`
- Kafka: `spring.kafka.*`
- JWT secret: `security.jwt.secret` (base64-encoded key)
- Context path: `server.servlet.context-path=/api`

Test config: `src/test/resources/application-test.yml`

## 8. Collaboration Workflow

Team progress is tracked in GitHub (Issues/Projects), not local AI notes.

- Open work as GitHub Issues (`Feature`, `Bug`, `Task`)
- Move issue status in GitHub Projects
- Every PR must link an issue (`Closes #...`, `Fixes #...`, `Refs #...`)

Enforcement in this repo:
- PR template: `.github/pull_request_template.md`
- Required check: `.github/workflows/require-issue-link.yml`

## 9. Project Layout

- `src/main/java/com/dynapi/controller` active REST controllers
- `src/main/java/com/dynapi/service` application services
- `src/main/java/com/dynapi/domain` domain models and validators
- `src/main/java/com/dynapi/repository` Mongo repositories
- `src/main/java/com/dynapi/infrastructure` messaging integration
- `src/main/resources` app config and messages
- `src/test` integration and context tests

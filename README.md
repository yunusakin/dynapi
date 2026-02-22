# Dynapi

Dynapi is a Spring Boot backend for dynamic field/schema-driven data handling.
Client applications can define fields and submit/query dynamic payloads without backend changes for each new form model.

## Current Status

Sprint 01 progress:
- Messaging migrated from RabbitMQ to Kafka
- HTTP Basic replaced with JWT filter-chain auth for admin routes
- API response and exception handling unified to one contract (`com.dynapi.dto.ApiResponse`)

## Tech Stack

- Java 21
- Spring Boot 3.3
- Spring Web, Spring Security, Spring Validation
- MongoDB 7
- Kafka
- springdoc-openapi

## Prerequisites

- Java 21
- Docker Desktop
- Maven Wrapper (`./mvnw`)

## Quick Start

### 1. Start local dependencies

```bash
docker run -d --name dynapi-mongo -p 27017:27017 mongo:7

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

### 2. Run tests

```bash
./mvnw -q test
```

### 3. Run application

```bash
./mvnw spring-boot:run
```

App default base URL:
- `http://localhost:8080/api`

Swagger UI:
- `http://localhost:8080/api/swagger-ui.html`

## Configuration

Main runtime config: `src/main/resources/application.yml`

Key values:
- MongoDB: `localhost:27017`, database `dynapi`
- Kafka: `localhost:9092`
- Context path: `/api`
- JWT secret property: `security.jwt.secret`

Test profile config: `src/test/resources/application-test.yml`

## Security Model (Current)

- Admin paths are protected by JWT filter-chain rules.
- Non-admin paths are currently permitted.
- JWT validation expects:
  - `sub` claim (username)
  - `roles` claim (string or array), where `ADMIN` maps to `ROLE_ADMIN`

Note:
- There is no token-issuing endpoint yet in the API. Token generation is currently external/dev-only.

## API Contract (Current)

Canonical response envelope:
- `com.dynapi.dto.ApiResponse<T>`
- Fields: `success`, `message`, `data`, optional `errors`, optional `metadata`

Canonical global exception handler:
- `com.dynapi.exception.GlobalExceptionHandler`

## Declared Endpoint Groups

Controllers currently declare routes for:
- Form submission
- Dynamic query
- Admin schema CRUD

Controller files:
- `src/main/java/com/dynapi/controller/FormController.java`
- `src/main/java/com/dynapi/controller/QueryController.java`
- `src/main/java/com/dynapi/controller/SchemaAdminController.java`
- `src/main/java/com/dynapi/interfaces/rest/FormSubmissionController.java`

## Known Issue

Current package layout has mixed roots:
- Main app class package: `com.dynapi.dynapi`
- Most controllers/services package: `com.dynapi.*`

Because of this, component scanning may not load all intended beans/routes without explicit scan configuration.

Suggested fix:
- Update `DynapiApplication` to scan `com.dynapi` (for example via `@SpringBootApplication(scanBasePackages = "com.dynapi")`) and then validate all routes.

## Project Structure

- `src/main/java/com/dynapi/domain` domain models/services/events
- `src/main/java/com/dynapi/application` ports/use-cases
- `src/main/java/com/dynapi/infrastructure` persistence/messaging adapters
- `src/main/java/com/dynapi/controller` REST controllers
- `src/main/resources` runtime config and i18n messages
- `src/test` test profile + tests
- `sdd/memory-bank` planning/spec history

## Project Workflow

This repo includes memory-bank/process files under `sdd/`.
Before major behavior changes:
- update specs in `sdd/memory-bank`
- implement code
- update progress/context files

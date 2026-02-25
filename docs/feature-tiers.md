# Dynapi Feature Tiers

This file maps the current backlog in `sdd/memory-bank/core/backlog.md` to product tiers.

## Tier Definition
- `demo`: Public/open-source core platform capabilities.
- `pro`: Private/commercial advanced security, governance, and enterprise capabilities.

## Detailed Mapping

### EPIC A: Schema Lifecycle Management
| Item | Backlog Item | Tier |
|---|---|---|
| A1 | Add schema status model (`DRAFT`, `PUBLISHED`, `DEPRECATED`) | demo |
| A2 | Prevent edits on published schema versions (immutability rule) | demo |
| A3 | Implement publish-time compatibility checks | demo |
| A4 | Add version rollback endpoint/operation | demo |
| A5 | Add integration tests for create/publish/deprecate/rollback flows | demo |

### EPIC B: Advanced Validation Engine
| Item | Backlog Item | Tier |
|---|---|---|
| B1 | Add field rules (`required`, `min/max`, `regex`, `enum`) | demo |
| B2 | Add conditional rule support (`requiredIf`) | demo |
| B3 | Add nested object/array validation with path-aware errors | demo |
| B4 | Standardize validation error response contract | demo |
| B5 | Add test matrix for valid/invalid payload permutations | demo |

### EPIC C: Query Guardrails
| Item | Backlog Item | Tier |
|---|---|---|
| C1 | Implement type-aware operator allowlist | demo |
| C2 | Enforce max page size and pagination boundaries | demo |
| C3 | Add sortable/filterable field allowlist per entity | demo |
| C4 | Add query complexity guard (depth/rule count threshold) | demo |
| C5 | Add performance-oriented query integration tests | demo |

### EPIC D: Access Control v2
| Item | Backlog Item | Tier |
|---|---|---|
| D1 | Add API key auth mode for service clients | pro |
| D2 | Add scope-based authorization checks | pro |
| D3 | Add entity-level read/write policy enforcement | pro |
| D4 | Add field-level permission enforcement in submit/query | pro |
| D5 | Add security regression tests for privilege boundaries | pro |

### EPIC E: Audit + Change History
| Item | Backlog Item | Tier |
|---|---|---|
| E1 | Persist schema/data change metadata (actor, timestamp, action) | pro |
| E2 | Store before/after snapshots for schema updates | pro |
| E3 | Add audit query endpoint with pagination/filtering | pro |
| E4 | Add tests for traceability completeness | pro |

### EPIC F: Reliable Eventing
| Item | Backlog Item | Tier |
|---|---|---|
| F1 | Implement outbox table/collection and dispatcher | pro |
| F2 | Add retry policy and dead-letter topic handling | pro |
| F3 | Add idempotency key support on submission endpoint | pro |
| F4 | Add failure simulation tests for recoverability | pro |

### EPIC G: Developer Experience Kit
| Item | Backlog Item | Tier |
|---|---|---|
| G1 | Publish Postman collection with environment variables | demo |
| G2 | Add end-to-end sample payloads for schema/submission/query | demo |
| G3 | Add concise error catalog to docs | demo |
| G4 | (Optional) Add lightweight JavaScript client SDK | pro |

### EPIC H: Contract + Operations Quality
| Item | Backlog Item | Tier |
|---|---|---|
| H1 | Complete `/v1` endpoint consistency review and cleanup | demo |
| H2 | Improve OpenAPI examples and schema descriptions | demo |
| H3 | Add health/readiness checks and basic metrics | demo |
| H4 | Add CI quality gates for contract and integration coverage | demo |

### EPIC I: Dynamic Record Lifecycle
| Item | Backlog Item | Tier |
|---|---|---|
| I1 | Add `PATCH /api/records/{entity}/{id}` for partial updates | demo |
| I2 | Add `PUT /api/records/{entity}/{id}` for full replacement | demo |
| I3 | Add `DELETE /api/records/{entity}/{id}` with soft-delete metadata | demo |
| I4 | Ensure updates/deletes validate against latest `PUBLISHED` schema only | demo |
| I5 | Add integration tests for update/replace/delete success and error flows | demo |

### EPIC J: Unique + Index Governance
| Item | Backlog Item | Tier |
|---|---|---|
| J1 | Add `unique` and `indexed` metadata to field definition model | demo |
| J2 | Add `POST /api/admin/schema/entities/{entity}/indexes/sync` | demo |
| J3 | Enforce unique field constraints during submit/update | demo |
| J4 | Add tests for duplicate rejection and index sync behavior | demo |

### EPIC K: Publish Dry-Run + Diff
| Item | Backlog Item | Tier |
|---|---|---|
| K1 | Add `POST /api/admin/schema/field-groups/{groupId}/publish/dry-run` | pro |
| K2 | Return `compatible`, `blockingChanges`, `nonBreakingChanges`, and path-level diff | pro |
| K3 | Ensure dry-run has zero lifecycle state mutation | pro |
| K4 | Add strict compatibility regression tests for dry-run output | pro |

### EPIC L: API Key + Rate Limiting
| Item | Backlog Item | Tier |
|---|---|---|
| L1 | Add admin API key lifecycle endpoints (create/list/delete) | pro |
| L2 | Support `X-API-Key` auth for public submit/query routes | pro |
| L3 | Add rate limiting per API key and IP fallback | pro |
| L4 | Add 401/403/429 contract tests for key and throttle scenarios | pro |

### EPIC M: Field-Level Permissions + Masking
| Item | Backlog Item | Tier |
|---|---|---|
| M1 | Add `readRoles`, `writeRoles`, and `masking` metadata on fields | pro |
| M2 | Enforce write permission checks on submit/update | pro |
| M3 | Enforce read masking/omission on query responses | pro |
| M4 | Add regression tests for mixed-role visibility behavior | pro |

### EPIC N: Webhook Integrations
| Item | Backlog Item | Tier |
|---|---|---|
| N1 | Add webhook management endpoints (create/list/delete) | pro |
| N2 | Dispatch events for schema lifecycle and record CRUD changes | pro |
| N3 | Add signed delivery headers and retry policy | pro |
| N4 | Add tests for delivery success/retry/failure capture | pro |

### EPIC O: Async Export Jobs
| Item | Backlog Item | Tier |
|---|---|---|
| O1 | Add export job endpoints (create/status/download) | pro |
| O2 | Support `CSV` and `JSON` export formats | pro |
| O3 | Implement async job state machine (`QUEUED/RUNNING/COMPLETED/FAILED`) | pro |
| O4 | Add tests for end-to-end export completion and guarded download | pro |

## Split Rationale
- `demo` keeps the full dynamic schema/data core usable end-to-end in public.
- `pro` groups features tied to monetization, stricter governance, enterprise security, and advanced integrations.

## Enforcement
- Public repo must not include `pro` endpoint/capability implementation.
- CI enforces this with `scripts/check-demo-boundary.sh`.

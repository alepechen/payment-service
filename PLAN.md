# Payment Integration – Development Plan

Spring Boot 4.1 · Java 25 · Gradle · PostgreSQL · WireMock

Each phase is done when its tests pass, not when the code compiles.

## Phase 0 – Setup

- [ ] Gradle 9.1+ wrapper, `build.gradle.kts`, `compose.yaml`
- [ ] `application.yml` with `local` and `test` profiles
- [ ] Flyway baseline migration
- [ ] One `@SpringBootTest` using Testcontainers Postgres
- [ ] CI running `./gradlew build`

## Phase 1 – Domain & first endpoint

- [ ] `Payment` entity: UUID id, `client_id`, amount as `long` minor units, currency, status, `version`
- [ ] State machine: CREATED → PROCESSING → SUCCEEDED / FAILED / UNKNOWN; reject illegal transitions
- [ ] `POST /api/payments`, `GET /api/payments/{id}`
- [ ] Basic security: HTTP Basic, in-memory users, `client_id` from principal, permit `/actuator/health` and `/webhooks/**`

## Phase 2 – Idempotency

- [ ] `idempotency_keys` table, PK `(client_id, idempotency_key)`, request hash, stored response
- [ ] Same key + same body → replay response; in progress → 409; different body → 422
- [ ] Test: concurrent requests with the same key create exactly one payment

## Phase 3 – Provider client & error handling

- [ ] Mock provider in WireMock: `POST /v1/charges`, `GET /v1/charges/{id}`
- [ ] `RestClient` HTTP interface with connect/read timeouts
- [ ] Send payment id as the provider's `Idempotency-Key`
- [ ] Classify errors: 4xx permanent, 429/5xx transient, read timeout = unknown outcome
- [ ] `@RestControllerAdvice` returning RFC 9457 `ProblemDetail`
- [ ] Never call the provider inside a DB transaction

## Phase 4 – Resilience

- [ ] Retry: 3 attempts, exponential backoff with jitter, transient errors only, honour `Retry-After`
- [ ] Circuit breaker: count only transient failures; when open, keep payment PROCESSING and return 202
- [ ] Bulkhead to cap concurrent provider calls
- [ ] Tests: 500,500,200 → 3 calls; 400 → 1 call; sustained 503 → breaker opens

## Phase 5 – Webhooks & polling

- [ ] Webhook endpoint: verify HMAC signature on raw body, reject old timestamps
- [ ] Inbox table with unique event id for deduplication
- [ ] Out-of-order events can't move status backwards
- [ ] Poller for PROCESSING/UNKNOWN payments, `FOR UPDATE SKIP LOCKED`
- [ ] Done when payments reach a final status with webhooks turned off

## Phase 6 – Partial failures

- [ ] Transactional outbox for side effects + relay
- [ ] Reconciliation job comparing provider charges with local payments
- [ ] Tests: timeout → UNKNOWN → resolved; DB failure after provider success → repaired

## Phase 7 – Monitoring

- [ ] Prometheus metrics: provider latency/errors, breaker state, retries, stuck payments, webhook results
- [ ] OpenTelemetry tracing, structured JSON logs with `payment_id`
- [ ] Alerts: breaker open, stuck payments, rejected webhooks
- [ ] Provider health stays out of the liveness probe

## Phase 8 – Security hardening

- [ ] JWT resource server with Keycloak, scopes `payments:read` / `payments:write`
- [ ] Separate filter chain for `/webhooks/**` (signature only)
- [ ] Secrets from environment variables; lock down actuator
- [ ] Tests: 401 no token, 403 wrong scope, 404 other client's payment

## Phase 9 – FX transfers with bank responses

- [ ] Mock provider: `POST /v1/fx/quotes`, `POST /v1/transfers`, `GET /v1/transfers/{id}`
- [ ] `fx_quotes` table: rate as `BigDecimal` string, sell/buy amounts in minor units, fee, `expires_at`, status
- [ ] `POST /api/fx/quotes`; expired quote → 422 `quote_expired`, never re-price silently
- [ ] `transfers` table with unique `quote_id` (a quote is spent once) and beneficiary (name, IBAN, BIC)
- [ ] `POST /api/transfers` with `Idempotency-Key`; returns 202 SUBMITTED
- [ ] States: SUBMITTING → SUBMITTED → ACCEPTED → IN_TRANSIT → COMPLETED; REJECTED; COMPLETED → RETURNED is legal
- [ ] Webhooks `transfer.accepted` / `.sent` / `.completed` / `.rejected` / `.returned` through the existing receiver
- [ ] Map bank reason codes (AC01, AM04, …) to your own error codes
- [ ] Currency-aware minor units (JPY 0, CAD 2, KWD 3) and explicit rounding mode
- [ ] Submit timeout: retry with the same key or query by transfer id, never resubmit with a new key
- [ ] Slower poller (minutes) and time-in-state alert (e.g. SUBMITTED > 2 business days)
- [ ] Outbox event `transfer.completed` / `transfer.returned` to notify the client
- [ ] Tests: expired quote, quote reused, full webhook sequence, out-of-order events, rejection, return after completion

## Stretch

- Second provider with failover · refunds · Toxiproxy chaos tests · load testing

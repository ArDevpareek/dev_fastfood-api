# Milestone 3 — Progress Notes (in-progress, not committed)

Nothing has been committed or pushed. This file is just a resume-point.

## ▶ NEXT STEPS (stopped 2026-09-27 afternoon — start here)

State when stopped: all Milestone 3 work below is done and verified,
14/14 tests pass, README fully updated, app on port 8080 stopped.
Still: **don't commit until the user says so.**

1. **Bring the infra files into the repo.** `C:\Projects\fast-food-api`
   (docker-compose.yml + init.sql) is NOT a git repo, so its Milestone 3
   changes (KRaft Kafka, `pending_payload`, `published_at`) aren't
   versioned anywhere. Copy both into the `fastfood` repo (e.g. repo root
   or a `docker/` folder), update the README "How to run this" steps
   (currently `cd fast-food-api` → `docker compose up -d`), and check
   docker-compose's init.sql volume path still resolves from the new location.
   Ask the user before deleting/retiring the old folder.
2. **Fix the Kafka-down hang and test it.** When the broker is unreachable,
   `KafkaTemplate.send()` can block up to `max.block.ms` (60s default)
   waiting for metadata — inside the after-commit hook on the request
   thread, so POST /order could hang ~60s. Plan: set short producer
   timeouts in `KafkaConfig.orderEventProducerFactory()` (e.g.
   `max.block.ms` a few seconds; review `delivery.timeout.ms` /
   `request.timeout.ms` together — delivery.timeout.ms must be ≥
   linger.ms + request.timeout.ms). Then test live: `docker stop
   fast-food-kafka`, time a POST /order (should return quickly, order
   saved with `published_at` NULL), `docker start fast-food-kafka`, confirm
   reconciliation re-publishes it after ~20–30s and it ends CONFIRMED.
   Update README Known gaps (currently lists this as "not tested").
3. **Remove the hand-made test orders.** The user asked to remove "the 4
   dddddddd test orders", but there are actually **6** (checked
   2026-09-27): `dddddddd-0000-4000-8000-00000000000{1..6}` —
   0001 FAILED (0 items), 0002 CONFIRMED (1), 0003 FAILED (0),
   0004 CONFIRMED (1), 0005 CONFIRMED (1), 0006 FAILED (0).
   Confirm with the user that all 6 should go, then delete their
   `order_items` rows first (foreign key), then the orders.
   Also note in the README that these are no longer in the data (DLT still
   holds 0001's message and the 3 poison-pill messages — fine to leave).
4. **Optional: listener concurrency = 3.** Set `factory.setConcurrency(3)`
   on `orderEventKafkaListenerContainerFactory` (one thread per partition),
   re-run the same K6 test (`k6 run -e EXPECTED_STATUS=202
   k6-tests/order-create.js`) and compare consumer drain time against the
   final run (271s, ~105 orders/s). Expect limited gain on this test: every
   order hits the same restaurant row lock (`FOR UPDATE`).

K6 run recipe used so far: start app with `--spring.jpa.show-sql=false`,
20-order warm-up, wait for PENDING = 0, then run K6; time the drain by
polling `select count(*) from orders where status='PENDING'`.

---

(Older notes below, kept for history.)

## Design agreed with the user

- POST /order: full synchronous validation (same 9 checks as before, same
  404/400 contract) → save Order row as PENDING → publish to Kafka →
  return 202 with the order ID.
- Kafka consumer: re-validates authoritatively using the pessimistic-lock
  repository methods (same locking Milestone 1 introduced), then either
  saves order_items + marks CONFIRMED, or marks FAILED (no retry — business
  failures are terminal, not transient).
- New GET /order/{id} so the client can check PENDING/CONFIRMED/FAILED.
- Idempotency key = order status itself: if a redelivered message finds
  the order no longer PENDING, it's skipped.
- Dual-write problem (DB commit vs. Kafka publish not being atomic) is
  handled with: (1) publish only after the DB transaction commits, plus
  (2) a scheduled reconciliation job that re-publishes any order still
  PENDING after 20s, using a JSON payload saved in the same transaction as
  the order row (`orders.pending_payload` — a "poor man's outbox").

## Done

- **`fast-food-api/docker-compose.yml`** — Kafka rewritten for KRaft mode
  (no Zookeeper), correct dual listeners (`kafka:29092` internal,
  `localhost:9092` host-facing), capped JVM heap. Verified working:
  container starts cleanly, test topic create/describe/delete succeeded.
- **`pom.xml`** — added `spring-kafka` + `spring-kafka-test`. Confirmed via
  `dependency:tree` that no classic Jackson leaks into the main
  (compile/runtime) classpath — only appears under `spring-kafka-test`'s
  test-scope embedded-broker utilities.
- **`application.properties`** — trimmed to just `spring.kafka.bootstrap-servers`;
  everything else is now explicit in `KafkaConfig` (see below).
- **`init.sql`** + **live Postgres DB** — added `orders.pending_payload TEXT`
  column (applied via `ALTER TABLE`, no data loss; also added to init.sql
  for future fresh containers).
- **`entity/Order.java`** — added `pendingPayload` field.
- **`repository/OrderRepository.java`** — added `findByStatusAndCreatedAtBefore`
  for the reconciliation job.
- **`messaging/OrderCreatedEvent.java`** — the Kafka message DTO (IDs +
  quantities only, never prices).
- **`messaging/OrderEventSerializer.java`** / **`OrderEventDeserializer.java`** —
  hand-written, backed by `tools.jackson` (Jackson 3), same reasoning as
  the Milestone 2 Redis fix — avoids gambling on what Jackson version
  Spring Kafka's built-in JSON (de)serializer expects internally.
- **`config/KafkaTopicConfig.java`** — declares `order.created`, 3 partitions
  (reasoning in comments: enough to demo 2 consumer instances splitting
  work 2/1, cheap enough for a local single-broker laptop setup).
- **`config/KafkaConfig.java`** — hand-built producer/consumer factories +
  `KafkaTemplate` + listener container factory (manual ack mode), using
  the custom serializer/deserializer instances directly rather than via
  reflection/class-name properties.
- **`service/OrderService.java`** — fully rewritten:
  - `createOrder(...)` — sync path (unlocked reads, saves PENDING order +
    payload, publishes after commit via `TransactionSynchronizationManager`).
  - `processOrderEvent(...)` — consumer path (locked reads, idempotency
    guard, CONFIRMED or FAILED).
  - shared private `validate(...)` used by both (the `withLock` flag picks
    locking vs. non-locking repository methods).
  - `markOrderFailed(...)`, `getOrder(...)`, `republish(...)`.
- **`service/OrderReconciliationScheduler.java`** — `@Scheduled` every 30s,
  finds orders PENDING > 20s, republishes from `pending_payload`.
- **`messaging/OrderConsumer.java`** — `@KafkaListener` + `@RetryableTopic`
  (4 attempts, 2s/4s/8s backoff) + `@DltHandler` (marks the order FAILED
  once retries are exhausted, so it's never left silently stuck).
- **`controller/OrderController.java`** — POST /order now returns 202;
  added GET /order/{orderId}.
- **`FastfoodApplication.java`** — added `@EnableScheduling`.
- Main source compiles cleanly (`./mvnw compile`).
- **`src/test/.../OrderServiceTest.java`** — rewritten: the original 5
  tests adapted to the new sync-only `createOrder` (non-locking repo
  stubs, no more `orderItemRepository.saveAll` expectation since items
  aren't saved synchronously anymore), plus 2 new tests for the consumer
  path (idempotent skip on non-PENDING order, business-rule failure marks
  FAILED without throwing).

## Unit tests — DONE (2026-09-27)

- Last night's `userRepository.findById` stub fix confirmed working.
- One more failure found: `createOrder_happyPath_calculatesCorrectTotals`
  got `status == null` because PENDING was only set by `@PrePersist`
  (never runs with a mocked repository). Fixed by setting
  `order.setStatus("PENDING")` explicitly in `OrderService.createOrder`.
- `./mvnw test` → 8/8 pass (7 OrderServiceTest + context load).
- JaCoCo 0.8.12 prints "Unsupported class file major version 69" noise
  (too old for Java 25) — harmless to tests, suggested version bump to user.

## Live tests — DONE (2026-09-27)

Bugs found and fixed along the way:
1. **Consumer never started.** Boot 4 splits auto-config per technology;
   plain `spring-kafka` gives no `@KafkaListener` processing and no
   `KafkaAdmin` (so the broker auto-created `order.created` with 1
   partition). Fixed: `spring-boot-starter-kafka` + `spring-boot-starter-kafka-test`.
   KafkaAdmin then grew the topic 1 → 3 partitions itself.
2. **Poison pill blocked ALL processing.** Unparseable message → consumer
   re-read the same offset in a tight loop (3,705 error lines in <1 min),
   all 3 partitions stalled. Fixed in KafkaConfig: `ErrorHandlingDeserializer`
   on the consumer + `DelegatingByTypeSerializer` (byte[] passthrough) on the
   producer so raw bad bytes can be forwarded to the DLT.

Results:
- A. Normal: 202 in ~40–85ms warm (1.15s first request = Kafka producer
  connect + JIT warm-up); CONFIRMED ~100ms after the 202.
- B1. Retry/DLT: crafted event with quantity 400,000 → `numeric field overflow`
  → attempts at +0s, +2s, +4s, +8s → DLT → order FAILED, 0 order_items.
- B2. Poison pill (after fix): 3 non-JSON messages went straight to DLT with
  original bytes; good orders behind them CONFIRMED.
- C. Duplicates: same event published twice → 1 CONFIRMED, 1 skipped,
  1 order_item row. Also saw natural redelivery (4× per order after error-
  handler seek) — all skipped correctly.
- D. Scaling: 2 instances → partitions split [2] / [0,1]; 12 orders →
  3 / 9 processed; failover after kill -9 took ~43s (session timeout).

Known gaps observed (not fixed, user not yet asked):
- Legacy order a59041a0 (pre-M3, PENDING meant "placed") has no payload →
  reconciliation logs a WARN every 30s forever. Proposed: one-off
  `UPDATE orders SET status='CONFIRMED' WHERE status='PENDING' AND pending_payload IS NULL`
  — needs user approval (data change).
- POST /order with quantity 400,000 → 500 (no upper bound on quantity;
  Milestone 1 validation gap).
- Test rows left in DB: orders dddddddd-...-0001 (FAILED) and -0002 (CONFIRMED).
- Broker has auto.create.topics enabled (default) — masked bug #1.
## Session 2026-09-27 (afternoon) — user approved and done

- 5,486 old-version test orders + legacy a59041a0 → CONFIRMED (5,487 rows).
- JaCoCo 0.8.12 → 0.8.15; "major version 69" noise gone. Coverage 50%.
- Reconciliation give-up limit: 10 min by age (`fastfood.reconciliation.give-up-after`),
  conditional `markFailedIfPending` UPDATE + consumer locks order row
  (`findByIdForUpdate`). 2 new scheduler tests; 10/10 tests pass. Verified live.
- New K6: 23,533 reqs, 588.3 req/s, avg 63.7ms, p95 121.6ms, 0% fail.
  22,633 PENDING at end; all CONFIRMED ~7 min later.
- Worktree old-sync removed. README filled in.

## Acknowledgement-aware reconciliation — DONE (2026-09-27, user approved)

- acks=all was already set (KafkaConfig). Caveat documented: 1 broker / RF 1.
- `orders.published_at TIMESTAMPTZ` added to live DB (ALTER) AND init.sql.
  Entity field is insertable/updatable=false (only the targeted UPDATE writes it).
- `PublishAckRecorder`: Kafka callback queues the ID; @Scheduled 500ms flush
  does `UPDATE ... WHERE id IN (<=1000)`. First attempt (1 UPDATE/order on a
  2-thread pool) dropped 16,430 acks under K6 → replaced by batching.
- Reconciliation + 10-min give-up only consider `published_at IS NULL`.
- `spring.task.scheduling.pool.size=2` (flush + reconciliation independent).
- Tests: 14/14 (OrderService 9, scheduler 2, recorder 2, context 1).
- Live: acked 11-min-old order left alone across 2 runs; unacked → FAILED.
- K6 final: 29,478 reqs, 736.9 req/s, avg 50.8ms, p95 77.4ms, 0% fail;
  28,416 PENDING at end, cleared in 271s; 0 republishes, 0 duplicates.
  Consumer ≈ 105 orders/s — the real bottleneck (README Known gaps lists
  options; NOT built per user).
- README fully updated. Nothing committed.

## (superseded) OPEN — needs user decision

- **Reconciliation can't tell lost from queued.** Under K6 it sent 123,664
  duplicate republishes; the 10-min give-up could FAIL valid queued orders
  if a backlog exceeds 10 min. Proposed: mark publish-acknowledged (e.g.
  clear pending_payload in the send callback, off the producer thread) and
  only republish/give up on unacknowledged orders. Changes the agreed design → ask.
- ~103k duplicate messages were still in the topic at 12:34 (harmless skips).

## K6 — (earlier notes)

- No POST /order K6 script existed anywhere → created `k6-tests/order-create.js`.
- Baseline (old sync code, git worktree of 4d33b08 at
  `<scratchpad>/old-sync`): 5,466 reqs, 136.6 req/s, avg 276ms, p95 439ms, 0% fail.
- **Blocker:** the old code saved its 5,486 orders (20 warm-up + 5,466) as
  PENDING with no payload (old meaning of PENDING). The new app's
  reconciliation job then scans all of them every 30s → would skew the
  new-version run. My UPDATE marking just those rows CONFIRMED (created
  2026-09-27 06:26:32–06:27:23 UTC) was blocked by the permission system,
  and so was stopping the new app afterwards. Waiting on the user.
- Still to do: new-version K6 run (`-e EXPECTED_STATUS=202`), measure
  backlog drain time, fill the *pending* cells in README.
- Worktree `<scratchpad>/old-sync` still exists — ask before removing
  (`git worktree remove`).

## README — DRAFTED

Milestone 3 section written (mermaid diagram, partitions, retry/DLT/poison
table, idempotency, dual-write, live test table, bugs found, K6 table with
new-version cells marked *pending*), plus known gaps. Top-of-file sections
updated for 202 / GET /order/{id} / Kafka / 7 tests.
- Nothing has been committed. `git status` will show the modified/new
  files listed above.

## Housekeeping note

Earlier in this session an untracked file (`commit_message.txt`) at the
repo root was accidentally deleted via an unnecessary `rm` command. The
user was told immediately; they confirmed it wasn't important ("0").
Flagging here only so it isn't a surprise if it's missed — no action
needed unless the user says otherwise.

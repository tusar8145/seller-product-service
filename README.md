# Seller Product Service

Enterprise-grade product microservice built to handle **high-traffic, high-concurrency,
long-running workloads** without overselling, without data loss, and without slowing
down the request path.

---

## 1. Tech Stack

| Layer            | Technology                                        |
|------------------|---------------------------------------------------|
| Runtime          | Java 21, Spring Boot 3.2.5                        |
| Web              | Spring Web MVC, Jakarta Validation, SpringDoc OpenAPI |
| Persistence      | Spring Data JPA, Hibernate, PostgreSQL 16         |
| Database scaling | Master + Read Replica (routing DataSource)        |
| Cache / Locking  | Redis 7 (Lettuce), Redisson (distributed locks)   |
| Messaging        | Apache Kafka (idempotent producer, manual-ack consumer, DLQ) |
| Bulk I/O         | Apache POI (streaming XLSX reader/writer)         |
| Object storage   | AWS S3 (SDK v2, presigned URLs)                   |
| Schema           | Flyway migrations                                 |
| Mapping          | MapStruct                                         |
| Observability    | Spring Boot Actuator, Micrometer → Prometheus     |
| Build            | Maven, Spring Boot Maven Plugin                   |

---

## 2. Why This Is Production-Grade

- **Redis-first reads** — every product read hits Redis; DB is only a fallback and backfill.
- **Master / Read-Replica routing** — writes go to master, reads go to replica automatically
  via a custom `RoutingDataSource` + read-only transaction awareness.
- **Kafka for async work** — CRUD events, bulk jobs, and stock-update pipelines all
  flow through Kafka topics with dead-letter queues.
- **Distributed locking + atomic Lua** — stock decrements use Redisson locks and
  Redis Lua scripts to guarantee `stock >= 0`, no oversell, no race conditions.
- **Idempotency everywhere** — every stock mutation carries a `referenceId`; the DB
  worker deduplicates via a UNIQUE constraint on `stock_update_history.reference_id`.
- **Optimistic locking** — `Product.version` + conditional `UPDATE ... WHERE version = ?`
  prevents lost updates even under parallel workers.
- **Backpressure & retries** — bounded worker queue, fixed backoff, DLQ for poison
  messages, per-row fallback when a batch insert fails.
- **Graceful startup logging** — DB / Redis / Kafka connectivity reported at boot.

---

## 3. High-Traffic Design

### Redis is the fast lane
  Client → Controller → Redis (reserve/validate atomically)
  → Enqueue async DB sync
  → Return immediately
  
- Product reads: `GET product:{id}` from Redis (TTL 15 min). Miss → DB replica → backfill.
- Stock mutations: atomic `DECRBY` / `INCRBY` via Lua, guarded by a per-product lock.

### Kafka is the async spine

| Topic                        | Purpose                              |
|------------------------------|--------------------------------------|
| `product.events`             | CRUD events for downstream consumers |
| `product.bulk.import`        | Bulk create + bulk stock update      |
| `product.bulk.import.dlq`    | Poison-message DLQ                   |
| `product.stock.update`       | (Optional) externalized DB sync      |
| `product.stock.update.dlq`   | Stock DLQ                            |

- **Producer**: `acks=all`, `enable.idempotence=true`, `retries=10`, `snappy` compression,
  `linger.ms=20`, `batch.size=32 KB` — tuned for throughput without sacrificing durability.
- **Consumer**: `enable.auto.commit=false`, `AUTO_OFFSET_RESET=earliest`, `max.poll.records=500`,
  manual `AckMode.MANUAL_IMMEDIATE`, `DefaultErrorHandler` with `FixedBackOff` + DLQ.

### Configurable parallelism

- `STOCK_WORKER_PARALLELISM` (default **8**) controls the DB-sync thread count.
- `STOCK_WORKER_QUEUE_CAPACITY` (default **10,000**) bounds the in-memory queue.
- Business logic is agnostic to worker count — change the env var, restart, done.

### Long-running background jobs

- Excel uploads stream to S3, then a Kafka message kicks off a **streaming XLSX parser**
  that processes rows in batches of 1,000 without loading the whole file into memory.
- Failures are collected and written back to S3 as a `failures.xlsx` report; a
  presigned URL is exposed so the client can download it.
- Job status, success/failure counts, and error details are persisted in `bulk_job`.

---

## 4. Anti-Oversell Guarantees (Defense in Depth)

1. **Redisson distributed lock** per product — serializes critical sections.
2. **Redis Lua script** — atomic check + decrement; returns `-1` on insufficient stock.
3. **Postgres CHECK constraint** `stock >= 0` — last line of defense.
4. **Optimistic version** on `Product` — prevents lost updates across parallel workers.
5. **UNIQUE `reference_id`** on `stock_update_history` — replay-safe.
6. **Idempotency key** in Redis (`product:idem:{refId}`, 24 h TTL) — client replays
   are silently deduplicated.

Result: **Available Stock >= 0** is enforced at every layer, even if one layer fails.

---

## 10. Run
```bash
chmod +x run.sh
./run.sh              # dev mode, DevTools hot reload


# Doodle Scheduling Mini-Service

A high-performance, production-grade microservice for managing calendar availability time slots and scheduling meetings. This solution is built from the ground up using **Domain-Driven Design (DDD)** principles, **Hexagonal (Ports & Adapters) Architecture**, and advanced concurrency guardrails for high-contention distributed systems.

## Key Architectural & Technical Decisions

### 1. High-Performance Time-Slot Filtering (PostgreSQL + GiST + `tstzrange`)
Instead of pulling thousands of raw availability records into JVM memory and parsing overlaps using CPU-heavy Java loops, the service delegates range math directly to the **PostgreSQL** engine.
* We utilize the native PostgreSQL **`TSTZRANGE`** (Timestamp with Time Zone Range) data type. This guarantees absolute temporal accuracy across different geographic time zones (e.g., matching users from Warsaw and New York seamlessly without data drift).
* We implement a composite **`GiST` (Generalized Search Tree)** index over `(user_id, duration_range)`.
* Conflicting or overlapping window queries execute in sub-millisecond time directly on the database shards using the spatial overlap operator (`&&`).
* Integration is managed cleanly using **`hypersistence-utils`**, preserving pure object-oriented mapping without exposing raw SQL string parsing to the domain.

### 2. Distributed Race Condition Protection (Redis + Redisson)
To support multi-user concurrent scheduling and prevent **Double Booking** under heavy load (e.g., multiple participants trying to book the exact same slot at the exact same millisecond), the system implements a robust **Distributed Lock Manager (DLM)**.
* We utilize **Redisson** to orchestrate distributed locks over a **Redis** cluster.
* To avoid the catastrophic *Self-Invocation* trap of Spring AOP proxies, the transaction boundaries are programmatically managed via **`TransactionTemplate`**.
* **Crucial Edge-Case Security:** The distributed lock is acquired *before* the database transaction opens and is released in a `finally` block *strictly after* the PostgreSQL transaction is fully committed. This completely eliminates any data visibility race conditions between concurrent app threads.
* Implements the Redisson **Watchdog** mechanism to safely auto-extend lock leases during heavy processing, mitigating deadlock risks if a cluster node crashes.

![img.png](img.png)

### 3. Multi-Tenant Data Isolation (Tenant-per-Header Pattern)
To simulate modern, zero-trust infrastructure operating behind an API Gateway (such as Kong or Envoy), the service implements horizontal tenant isolation using the **`X-User-Id`** HTTP Header.
* The system treats this header as a cryptographically verified tenant identity. All queries, modifications, deletions, and bookings are strictly scoped via this UUID on the repository layer, preventing any cross-tenant data leakage.

### 4. Hexagonal Architecture (Screaming DDD)
The codebase strictly decouples pure core business rules from infrastructure details:
* **`domain/`**: Pure Java models (`Calendar`, `TimeSlot`, `Meeting`) and outbound repository port contracts. Completely independent of Spring, Hibernate, or database types.
* **`application/`**: Use-case orchestration layer coordinating distributed locks and transactions.
* **`infrastructure/`**: Low-level adapters (REST Controllers, Jackson DTOs, Spring Data repositories, and the Global Exception Handler).

---

## Advanced Global Scalability & Concurrency Strategy (Target State Architecture)

To scale this service to handle millions of active users across multiple geographic regions (Doodle global scale), the architecture is designed to evolve from a single-node database model into a highly distributed, event-driven, and split-I/O ecosystem.

### 1. Distributed Cache-Aside Strategy (Redis)
To protect the primary relational database from heavy read traffic (since availability queries `GET /slots` naturally outnumber booking mutations `POST /book` by an 80:20 ratio), a distributed **Cache-Aside (Lazy Loading)** layer via Redis is introduced.
* **Read Path Optimization:** When a client queries available slots, the application first checks the Redis Cluster using a structured key pattern: `tenant:calendar:slots:{user_id}:{from_date}_{to_date}`. If a **Cache Hit** occurs, data is returned in sub-milliseconds, completely bypassing PostgreSQL. Upon a **Cache Miss**, the system queries the database, populates the Redis cache, and sets a strict Time-To-Live (TTL) of 5 minutes.
* **Reactive Cache Invalidation:** The moment a transaction successfully commits a meeting reservation (`POST /book`), an application event triggers an atomic cache eviction (`DEL`) for that specific user's calendar key, preventing users from seeing stale data while maintaining data accuracy.

### 2. CQRS Pattern & Database Read/Write Splitting
To completely isolate heavy data mutations from intensive analytical and search queries, the system implements the **CQRS (Command Query Responsibility Segregation)** pattern at the infrastructure tier:
* **Write Tier (Commands):** All slot creations, modifications, and bookings are routed exclusively to the Primary (Master) PostgreSQL instance. This node enforces transactional integrity, handles distributed locks, and manages optimistic locking validations.
* **Read Tier (Queries):** A pool of horizontally scaled **PostgreSQL Read Replicas** handles all search queries (`GET /slots`). This completely unburdens the primary database from CPU-intensive spatial range scans over the GiST indexes.

### 3. Managing Replication Lag via User/Tenant Pinning
Because the PostgreSQL Master streams changes to Read Replicas using **Asynchronous Replication**, a temporary synchronization delay (typically 10ms to 500ms, known as *Replication Lag*) exists.
* **The Race Condition Risk:** If a user modifies their availability slot (`PUT /slots`) and immediately refreshes the page (`GET /slots`), the read query might hit a replica that hasn't received the update yet. The user would confusingly see their old data, leading to a poor user experience.
* **The Solution: Tenant/User Pinning (Sticky Reads):** To solve replication lag without resorting to expensive synchronous replication, the routing layer implements a **Time-Based Pinning Pattern**:
  1. Whenever a write mutation (Command) is successfully processed for a specific `X-User-Id`, the application sets a short-lived marker in a fast local or distributed cache (e.g., a Redis key `pinning:user:{user_id}` expiring in 2 seconds).
  2. For any subsequent read query (`GET /slots`) from that same `X-User-Id`, the routing routing engine checks for the presence of this pinning marker.
  3. If the marker is active, the system **pins (routes) the read request directly to the Primary Master database**, bypassing the replicas and guaranteeing strict *Read-Your-Own-Writes* consistency.
  4. Once the pinning key expires (after 2 seconds, safely exceeding the replication lag window), the routing slips back to utilizing the high-throughput Read Replicas.

---

## How to Run

### Prerequisites
* Docker & Docker Compose installed.

### Start the Full Environment
Execute the following command in the root directory to spin up the multi-container stack:
```bash
docker compose down -v && docker compose up --build
```
*The application utilizes native Docker `healthcheck` probes (`pg_isready` for Postgres and `redis-cli ping` for Redis). The Spring Boot application container will block and wait until both infrastructure nodes are fully operational before executing Liquibase migrations and starting on port `8080`.*

---

## API Consumption Guide & Endpoint Documentation

The service strictly validates the `X-User-Id` HTTP header as a Tenant Identifier for all mutative and query endpoints to enforce multi-tenant calendar isolation. All request and response formats utilize standardized JSON and ISO-8601 UTC timestamps.

### 1. Create a New Availability Time-Slot
Registers a new open, bookable time-window for the requesting tenant.
* **HTTP Method:** `POST`
* **URL:** `/api/v1/slots`
* **Headers:**
    * `X-User-Id: <UUID>` (Owner of the calendar)
    * `Content-Type: application/json`
* **Request Body Pattern:**
```json
{
  "startTime": "2026-06-10T12:00:00Z",
  "endTime": "2026-06-10T13:00:00Z"
}
```
* **Success Response (201 Created):**
```json
{
  "id": "7ead02ed-d661-4441-a043-25705b473af7",
  "userId": "44c4b69c-29b1-4b3d-9d7a-115df6000001",
  "startTime": "2026-06-10T12:00:00Z",
  "endTime": "2026-06-10T13:00:00Z",
  "isFree": true
}
```
* **Error Responses:**
    * `400 Bad Request` – If fields are missing, times are in the past, chronological order is broken, or a time-range overlap conflict occurs with an existing slot owned by the user.

---

### 2. Fetch Available Slots (Time-Window Query)
Aggregates and queries all availability blocks within a strict date range exclusively for the authenticated tenant.
* **HTTP Method:** `GET`
* **URL:** `/api/v1/slots?from={ISO_8601}&to={ISO_8601}`
* **Headers:**
    * `X-User-Id: <UUID>`
* **Example Request:**
```bash
curl -X GET "http://localhost:8080/api/v1/slots?from=2026-06-10T00:00:00Z&to=2026-06-11T00:00:00Z" \
  -H "X-User-Id: 44c4b69c-29b1-4b3d-9d7a-115df6000001"
```
* **Success Response (200 OK):**
```json
[
  {
    "id": "7ead02ed-d661-4441-a043-25705b473af7",
    "userId": "44c4b69c-29b1-4b3d-9d7a-115df6000001",
    "startTime": "2026-06-10T12:00:00Z",
    "endTime": "2026-06-10T13:00:00Z",
    "isFree": true
  }
]
```

---

### 3. Update an Existing Time-Slot
Modifies the duration boundaries or forces a manual availability status override (`isFree`) for a targeted slot.
* **HTTP Method:** `PUT`
* **URL:** `/api/v1/slots/{id}`
* **Headers:**
    * `X-User-Id: <UUID>` (Must strictly match the owner of the target slot)
    * `Content-Type: application/json`
* **Request Body Pattern:**
```json
{
  "startTime": "2026-06-10T12:30:00Z",
  "endTime": "2026-06-10T13:30:00Z",
  "isFree": false
}
```
* **Success Response (200 OK):** Returns the modified `TimeSlotResponse` JSON payload.
* **Error Responses:**
    * `403 Forbidden` – Attempting to modify a slot belonging to a different tenant.
    * `400 Bad Request` – Range constraint violations or overlaps.

---

### 4. Delete a Time-Slot
Permanently drops an availability block from the database.
* **HTTP Method:** `DELETE`
* **URL:** `/api/v1/slots/{id}`
* **Headers:**
    * `X-User-Id: <UUID>` (Must match slot owner)
* **Success Response (204 No Content):** Empty body on successful purge.
* **Error Responses:**
    * `403 Forbidden` – Cross-tenant deletion attempt.

---

### 5. Convert Free Slot into a Meeting (Book Appointment)
Triggers the core distributed transaction flow. Transforms an open slot into an absolute committed appointment, binding metadata and a passenger list.
* **HTTP Method:** `POST`
* **URL:** `/api/v1/slots/{id}/book`
* **Headers:**
    * `X-User-Id: <UUID>`
    * `Content-Type: application/json`
* **Request Body Pattern:**
```json
{
  "title": "Technical Architecture Review",
  "description": "Deep dive into distributed locking, Redisson Watchdog, and high-performance spatial range indexing.",
  "participants": ["tejumoluwa@doodle.com", "mateusz@doodle.com"]
}
```
* **Success Response (201 Created):** Empty body. The associated time-slot atomicity flips to `isFree = false` in the database.
* **Error Responses:**
    * `409 Conflict` – Discovered a concurrent acquisition attempt on the Redisson distributed lock.
    * `400 Bad Request` – Target slot is already booked or doesn't exist.

---

## Production Telemetry, Metrics & Monitoring

To keep the application cloud-native and production-ready without introducing heavy, over-engineered infrastructure layers like Prometheus or Grafana agents into a code-challenge codebase, the service leverages the native **Spring Boot Actuator** core framework coupled with **Micrometer**.

### 1. Actuator Operational Endpoints
Basic service monitoring, health check aggregation, and configuration profiling are accessible out-of-the-box:
* **Liveness & Readiness Probes (K8s Standard):** `GET http://localhost:8080/actuator/health`
    * Automatically aggregates downstream infrastructure health status (PostgreSQL connection and Redis cluster node visibility).
* **Application Metadata Profile:** `GET http://localhost:8080/actuator/info`

### 2. Standardized Application Metrics
The application exposes raw system performance vectors natively via JMX and the HTTP endpoints under `http://localhost:8080/actuator/metrics`:
* **JVM Performance:** Tracks CPU usage, thread counts, and active Garbage Collection metrics (`jvm.gc.memory.allocated`, `jvm.threads.live`).
* **Connection Pools:** Monitors real-time HikariCP status for database connections (`hikaricp.connections.active`) and active Redisson connection pool latency.

### 3. Application-Level Metric Annotations (`@Timed`)
To observe critical performance constraints around core scheduling bottlenecks, the service infrastructure is prepared for granular latency profiling:
* By decorating the controller layer with Micrometer's **`@Timed`** annotation (e.g., on `bookMeeting`), the application computes the **P95 / P99 latency distribution** and request count metrics (`http.server.requests`).
* This enables operations teams to instantly spot processing delays inside the Redisson lock or the PostgreSQL GiST index scan, providing absolute visibility into the product's runtime scalability.

---

## Automated Test Suite
The project features a comprehensive integration testing layer built with **Spock Framework** and **Testcontainers**. It dynamically boots real isolated instances of PostgreSQL 18 and Redis 7 inside Docker during build time to execute end-to-end scenarios.

To run the test suite locally, execute:
```bash
./gradlew test
```
*Includes an advanced, multi-threaded high-contention test (`should prevent race conditions and double booking using distributed lock under concurrent load`) simulating 5 threads attacking a single slot booking simultaneously to prove the atomic precision of the Redisson distributed lock framework.*

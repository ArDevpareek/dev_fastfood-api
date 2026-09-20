# Fastfood API — Milestones 1 & 2

Backend Engineering Project — Impactis × TCS
High-Performance API Systems, Phase 1–2: RESTful Application + Caching

---

## What this is

A Spring Boot backend for a food delivery app simulation.

- **Milestone 1** covers the core RESTful application — data model, all 7 required endpoints, validation, error handling, and testing.
- **Milestone 2** adds Redis caching on `GET /restaurants` and `GET /menu` — see [Milestone 2 — Caching (Redis)](#milestone-2--caching-redis) below.

Query optimization is intentionally deferred to a later phase.

## Tech stack

- **Java 21**, **Spring Boot 4.1.1**
- **PostgreSQL 16** (via Docker)
- **Redis 7** (via Docker) — response caching, Milestone 2
- **Maven** for build and dependency management
- **Spring Data JPA / Hibernate** for database access
- **Spring Security (BCrypt only)** — used purely for password hashing, not authentication
- **JUnit 5 + Mockito** for testing
- **JaCoCo** for coverage reporting
- **K6** for load testing

## How to run this

**1. Start the database and cache**

```
cd fast-food-api
docker compose up -d
```

This starts Postgres and Redis in containers; Postgres automatically creates all 5 tables from `init.sql`.

**2. Run the application**

Open the `fastfood` folder in IntelliJ and run `FastfoodApplication`, or from the terminal:

```
cd fastfood
.\mvnw spring-boot:run
```

The app starts on `http://localhost:8080`.

**3. Run the tests**

```
.\mvnw clean test
```

Coverage report is generated at `target/site/jacoco/index.html`.

---

## Endpoints

### `GET /restaurants`
Returns all restaurants.

**Response 200:**
```json
[
  {
    "id": "909176a9-6dc9-4832-81f5-dbd9f71ac759",
    "name": "Sagar Ratna",
    "description": "North Indian comfort food",
    "cuisine": "North Indian",
    "deliveryFee": 30.00,
    "minOrderAmount": 150.00,
    "isActive": true,
    "createdAt": "2026-09-01T19:04:35.660535Z",
    "updatedAt": "2026-09-01T19:04:35.660535Z"
  }
]
```

### `GET /restaurants/{restaurantId}`
Returns one restaurant by ID. Returns **404** if not found.

### `POST /restaurants`
Creates a new restaurant.

**Request:**
```json
{
  "name": "Sagar Ratna",
  "description": "North Indian comfort food",
  "cuisine": "North Indian",
  "deliveryFee": 30.00,
  "minOrderAmount": 150.00
}
```
**Response:** 201, restaurant object with generated `id`.

### `POST /restaurants/{restaurantId}/menu-items`
Adds a menu item to a restaurant. Returns **404** if the restaurant doesn't exist.

**Request:**
```json
{
  "name": "Butter Chicken",
  "description": "Rich, creamy tomato-based curry",
  "price": 320.00,
  "calories": 550
}
```
**Response:** 201, menu item object including `restaurantId`.

### `GET /menu?restaurantId=`
Returns all menu items for a restaurant.

### `POST /users`
Creates a new user. Password is hashed with BCrypt before storage — the raw password is never saved. Returns **409** if the email is already registered.

**Request:**
```json
{
  "email": "priya@example.com",
  "password": "mysecretpass123",
  "firstName": "Priya",
  "lastName": "Sharma",
  "phone": "9876543210"
}
```
**Response:** 201, user object — no password field of any kind in the response.

### `POST /order`
Creates an order. This is the core business-logic endpoint. Validates, in order:

1. User exists (404)
2. Restaurant exists (404)
3. Restaurant is active (400)
4. At least one item was ordered (400)
5. Every menu item ID exists (404)
6. Every menu item belongs to the requested restaurant (400)
7. Every menu item is currently available (400)
8. Every quantity is at least 1 (400)
9. Order subtotal meets the restaurant's minimum (400)

**Prices are always read from the database, never trusted from the request** — the request DTO has no price field at all, by design. This closes the obvious vulnerability where a client could set their own price.

**Request:**
```json
{
  "userId": "09a85a76-9458-418c-bcc1-a1e942da5825",
  "restaurantId": "909176a9-6dc9-4832-81f5-dbd9f71ac759",
  "deliveryAddress": "12 Civil Lines, Roorkee",
  "deliveryNotes": "Ring the bell twice",
  "items": [
    { "menuItemId": "925c610a-ea5d-4b14-ab24-67e76f116c65", "quantity": 2, "specialInstructions": "extra spicy" }
  ]
}
```
**Response 201:**
```json
{
  "id": "af303706-5718-44e3-907d-9a3a937e0cf9",
  "userId": "09a85a76-9458-418c-bcc1-a1e942da5825",
  "restaurantId": "909176a9-6dc9-4832-81f5-dbd9f71ac759",
  "subtotal": 640.00,
  "deliveryFee": 30.00,
  "total": 670.00,
  "status": "PENDING",
  "items": [
    {
      "menuItemId": "925c610a-ea5d-4b14-ab24-67e76f116c65",
      "menuItemName": "Butter Chicken",
      "quantity": 2,
      "unitPrice": 320.00,
      "totalPrice": 640.00
    }
  ]
}
```

---

## Error handling

Every error returns a consistent shape via a global exception handler:

```json
{
  "timestamp": "2026-09-01T19:07:07.0...",
  "status": 404,
  "error": "Not Found",
  "message": "Restaurant not found: 00000000-0000-0000-0000-000000000000",
  "path": "/restaurants/00000000-0000-0000-0000-000000000000"
}
```

| Situation | Status |
|---|---|
| Resource doesn't exist (bad ID) | 404 |
| Business rule broken (inactive restaurant, below minimum, mismatched menu item) | 400 |
| Duplicate resource (email already registered) | 409 |
| Anything unexpected | 500, generic message (no internal details leaked) |

---

## Testing

`OrderServiceTest` covers the core order logic with 5 tests, using Mockito to fake all repository dependencies — no real database is touched, tests run in under a second.

- Happy path — correct subtotal, delivery fee, and total calculation
- User not found → `ResourceNotFoundException`
- Inactive restaurant → `BusinessRuleException`
- Order below minimum → `BusinessRuleException`
- Price is taken from the database entity, not the request — proves the security fix works

**Coverage (JaCoCo):**

| Package | Coverage | Notes |
|---|---|---|
| `service` | 65% | Core business logic — where testing effort was concentrated |
| `config` | 100% | Small file |
| `dto` | 39% | Mostly data-holding classes |
| `entity` | 0% | Data classes only, no logic to test |
| `exception` | 10% | Not yet covered |
| `controller` | 27% | Not yet covered |
| **Total** | **44%** | |

Testing effort was deliberately focused on `OrderService`, since that's where the actual business rules and calculations live. Entity and DTO classes are mostly generated getters/setters with no logic worth testing directly.

---

## Load testing (K6)

Burst test against `GET /restaurants`: ramped to 50 concurrent virtual users over 10s, held for 20s, ramped down over 10s.

| Metric | Result |
|---|---|
| Total requests | 102,583 |
| Throughput | 2,564 req/s |
| p95 latency | 21.7ms |
| Failure rate | 0.00% |

Both configured thresholds passed (`p95 < 1000ms`, `failure rate < 5%`) with wide margin. This is the Phase 1 baseline — Phase 2/3 caching and query optimization will be measured against these numbers.

**Known limitation:** load testing was run on a local development laptop (Acer Nitro V 15, i5-13420H, 16GB RAM) with Docker, the JVM, and K6 all competing for the same resources. Numbers may differ on dedicated hardware.

---

## Milestone 2 — Caching (Redis)

### What's cached

| Endpoint | Cache name | Key | TTL |
|---|---|---|---|
| `GET /restaurants` | `restaurants` | (none — one shared entry, the full list) | 10 minutes |
| `GET /menu?restaurantId=` | `menus` | `restaurantId` | 2 minutes |

**Why these TTLs:** restaurants rarely change (nothing currently writes to the `restaurants` cache — see below), so a longer window is safe and cuts DB load further. Menus change more often — new items, availability toggles — so a shorter window caps how stale a menu can ever get at 2 minutes.

An empty menu (a restaurant with zero items) still gets cached correctly — `@Cacheable` only skips caching a `null` return value, and an empty list isn't `null`.

### Cache invalidation on writes

`POST /restaurants/{restaurantId}/menu-items` evicts that restaurant's `menus` entry (`@CacheEvict(value = "menus", key = "#restaurantId")`) so a newly added item appears on the very next `GET /menu` call instead of waiting up to 2 minutes. Only that restaurant's entry is evicted — every other restaurant's cached menu is untouched.

**Design decision:** there's currently no working `POST /restaurants` endpoint in the codebase to evict the `restaurants` cache from (see *Known gaps* below), so no eviction was added for it. If restaurant creation is implemented later, it should evict the `restaurants` cache the same way, or that cache's 10-minute TTL becomes the only thing standing between a new restaurant and it actually showing up.

### Redis-down resilience

If Redis is unreachable, `GET /restaurants` and `GET /menu` still return correct data from Postgres — they don't fail. This comes from two changes:

- **`CacheConfig.errorHandler()`** — a custom `CacheErrorHandler` that logs a warning instead of letting a Redis exception propagate on a cache get/put/evict/clear failure. Spring's cache interceptor treats a swallowed get-error as a miss, so the real (database) method just runs as if nothing were cached.
- **`spring.data.redis.timeout=1s` / `connect-timeout=1s`** in `application.properties` — without this, Lettuce's default 60-second command timeout means a request would hang for a full minute before the error handler ever got a chance to fall back. Bounding it to 1s makes the fallback actually feel graceful instead of like a hang.

Verified by killing the Redis container while the app was running: the very next request still returned `200` with correct data in ~1 second, with a `WARN` logged. The app also starts up fine if Redis is already down when it boots.

### Hit/miss observability

`ObservableRedisCache` / `ObservableRedisCacheManager` (in `config/`) log every lookup as `Cache HIT` or `Cache MISS`, with the cache name and key. This exists because `@Cacheable`'s method body only ever runs on a miss — there's no place inside `RestaurantService` or `MenuItemService` themselves to log a hit. Sample log output:

```
Cache MISS [restaurants] key=SimpleKey [] — loading from the database
Hibernate: select r1_0.id, ... from restaurants r1_0
Cache HIT  [restaurants] key=SimpleKey []
```
(note: no `Hibernate:` line before the HIT — confirming Postgres wasn't touched.)

### Bugs found and fixed while wiring this up

- `@Cacheable("restaurants")` was on the `RestaurantService` **class**, not on `getAllRestaurants()` — meaning it silently applied to every public method in the class, including `getRestaurantById`. Moved to the one method it was meant for.
- `MenuItemRepository.findByRestaurantId` returned `MenuItem`s holding a lazy Hibernate proxy for `restaurant`. Caching that proxy embedded an internal `hibernateLazyInitializer` field in the JSON that Jackson couldn't read back — every cache HIT for `/menu` returned a 500. Fixed with a `JOIN FETCH` so `restaurant` is a real, already-loaded object by the time it gets cached.
- `GenericJacksonJsonRedisSerializer.builder().build()` doesn't embed type information by default, so a cache HIT deserialized entities into plain `LinkedHashMap`s instead of `MenuItem`/`Restaurant` objects — also a 500. Fixed by enabling default typing, scoped to a `PolymorphicTypeValidator` that only allows `com.dev.fastfood.*` plus the specific JDK value-type packages actually in use (`java.util`, `java.math`, `java.time`, `java.lang`) — not "allow any class," since this cache only ever needs to hold our own types.

All three were only visible by actually hitting the endpoint twice and reading the response body, not from compiling or starting the app — the first request (a cache miss) always looked fine either way.

---

## What's not in this milestone (by design)

- Authentication / login / JWT — not in the spec. Spring Security is used only for password hashing.
- Kafka / async processing — Phase 2/3
- Query optimization, indexing, N+1 fixes — Phase 3
- Update/delete endpoints — only the 7 specified endpoints were built
- Pagination — not required at this scale yet

---

## Known gaps

- Bean validation (`@Valid`, `@NotBlank` etc.) is not yet applied to request DTOs — planned as a follow-up before Phase 2.
- Controller-layer tests (`@WebMvcTest`) not yet written — service-layer tests were prioritized since that's where the business logic lives.
- `RestaurantService` and `UserService` currently have lighter test coverage than `OrderService`.
- `POST /restaurants` is documented above (Milestone 1) but isn't actually implemented — `RestaurantController` only has the two `GET` endpoints. Noticed while adding Milestone 2 caching (it's the reason the `restaurants` cache has no eviction path); not fixed here since restaurant creation is Milestone 1 scope.

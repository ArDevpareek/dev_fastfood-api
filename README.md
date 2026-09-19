# Fastfood API — Milestone 1

Backend Engineering Project — Impactis × TCS
High-Performance API Systems, Phase 1: Outline of the RESTful Application

---

## What this is

A Spring Boot backend for a food delivery app simulation. This milestone covers the core RESTful application — data model, all 7 required endpoints, validation, error handling, and testing. Caching (Redis) and query optimization are intentionally deferred to later phases.

## Tech stack

- **Java 21**, **Spring Boot 4.1.1**
- **PostgreSQL 16** (via Docker)
- **Maven** for build and dependency management
- **Spring Data JPA / Hibernate** for database access
- **Spring Security (BCrypt only)** — used purely for password hashing, not authentication
- **JUnit 5 + Mockito** for testing
- **JaCoCo** for coverage reporting
- **K6** for load testing

## How to run this

**1. Start the database**

```
cd fast-food-api
docker compose up -d
```

This starts Postgres in a container and automatically creates all 5 tables from `init.sql`.

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

## What's not in this milestone (by design)

- Authentication / login / JWT — not in the spec. Spring Security is used only for password hashing.
- Redis caching — Phase 2
- Kafka / async processing — Phase 2/3
- Query optimization, indexing, N+1 fixes — Phase 3
- Update/delete endpoints — only the 7 specified endpoints were built
- Pagination — not required at this scale yet

---

## Known gaps

- Bean validation (`@Valid`, `@NotBlank` etc.) is not yet applied to request DTOs — planned as a follow-up before Phase 2.
- Controller-layer tests (`@WebMvcTest`) not yet written — service-layer tests were prioritized since that's where the business logic lives.
- `RestaurantService` and `UserService` currently have lighter test coverage than `OrderService`.

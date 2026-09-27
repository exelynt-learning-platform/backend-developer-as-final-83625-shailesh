# Resource Booking System

A RESTful booking API built with **Spring Boot 3, Java 17, Spring Security, JWT and PostgreSQL**.
Users can browse resources and manage their own reservations. Administrators have full access.

## Features

- JWT login (`POST /auth/login`) with BCrypt password hashing and stateless authentication
- Role-based access control: `ADMIN` and `USER`
- Resource CRUD (USER read-only, ADMIN full access)
- Reservation CRUD with ownership rules (USER sees only their own, ADMIN sees all)
- Reservation identity is taken from the JWT, never from the request body
- Reservation statuses: `PENDING`, `CONFIRMED`, `CANCELLED`
- Filtering by status, minimum price and maximum price
- Pagination (`page`, `size`) and optional sorting (`sortBy`, `direction`)
- Validation and consistent JSON error responses
- Double-booking protection (overlapping active reservations return 409)
- Swagger UI / OpenAPI documentation
- Unit and integration tests, focused on security and authorization

## Tech stack

Java 17 · Spring Boot 3.3.5 · Spring Security · Spring Data JPA (Hibernate) · PostgreSQL ·
JJWT 0.12.6 · springdoc-openapi · JUnit 5 · MockMvc · H2 (tests only) · Maven

## Prerequisites

- JDK 17 or newer
- Maven 3.8+
- PostgreSQL 13+ (or MySQL 8, see below)

## Setup

### 1. Create the database

```sql
CREATE DATABASE booking_db;
```

Tables are created automatically on first start (`spring.jpa.hibernate.ddl-auto=update`).

### 2. Configure environment variables

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/booking_db` | JDBC URL |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `JWT_SECRET` | *(development default)* | HMAC secret, **at least 32 characters**. Set your own outside development |
| `JWT_EXPIRATION_MS` | `3600000` | Token lifetime in milliseconds (1 hour) |
| `SERVER_PORT` | `8080` | HTTP port |

Example (Linux/macOS):

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export JWT_SECRET=$(openssl rand -base64 48)
```

Example (Windows PowerShell):

```powershell
$env:DB_PASSWORD = "your_password"
$env:JWT_SECRET  = "a-long-random-secret-of-at-least-32-characters"
```

### 3. Run

```bash
mvn spring-boot:run
```

The API is available at `http://localhost:8080`.

### Using MySQL instead

1. In `pom.xml`, replace the `postgresql` dependency with:
```xml
   <dependency>
       <groupId>com.mysql</groupId>
       <artifactId>mysql-connector-j</artifactId>
       <scope>runtime</scope>
   </dependency>
```
2. Set the environment variables:
```bash
   export DB_URL="jdbc:mysql://localhost:3306/booking_db?createDatabaseIfNotExist=true"
   export DB_USERNAME=root
   export DB_PASSWORD=your_password
```

## Seed data

Created on first start:

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `user1` | `user123` | USER |
| `user2` | `user123` | USER |

Three sample resources are also created (Conference Room A, Company Van, Portable Projector).

> These credentials are for development and testing only. Change or remove `DataSeeder` before deploying.

## API documentation

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs (importable into Postman)

To try protected endpoints in Swagger: call `POST /auth/login`, copy the `token`, click **Authorize**
and paste it (without the `Bearer ` prefix).

## Endpoints

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/auth/login` | Public | Log in, receive a JWT |
| GET | `/resources` | ADMIN, USER | List resources |
| GET | `/resources/{id}` | ADMIN, USER | Get a resource |
| POST | `/resources` | ADMIN | Create a resource |
| PUT | `/resources/{id}` | ADMIN | Update a resource |
| DELETE | `/resources/{id}` | ADMIN | Delete a resource (409 if it has reservations) |
| POST | `/reservations` | ADMIN, USER | Create a reservation (owner = authenticated user) |
| GET | `/reservations` | ADMIN (all), USER (own) | List with filters, pagination, sorting |
| GET | `/reservations/{id}` | ADMIN (any), USER (own) | Get a reservation |
| PUT | `/reservations/{id}` | ADMIN | Update a reservation, including status |
| DELETE | `/reservations/{id}` | ADMIN | Delete a reservation |

### Listing reservations

```
GET /reservations?status=&minPrice=&maxPrice=&page=&size=&sortBy=&direction=
```

| Parameter | Default | Notes |
|---|---|---|
| `status` | none | `PENDING`, `CONFIRMED`, `CANCELLED` |
| `minPrice`, `maxPrice` | none | Inclusive, not negative, `minPrice <= maxPrice` |
| `page` | `0` | 0-based |
| `size` | `10` | 1 to 100 |
| `sortBy` | `id` | `id`, `price`, `startTime`, `endTime`, `status` |
| `direction` | `asc` | `asc` or `desc` (case-insensitive) |

### Example session

```bash
# 1. Log in
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"user123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

# 2. Create a reservation (owner comes from the token)
curl -X POST http://localhost:8080/reservations \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"resourceId":1,"startTime":"2026-10-05T10:00:00","endTime":"2026-10-05T12:00:00","price":150.50}'

# 3. List my reservations, pending only, price 100-400, highest first
curl "http://localhost:8080/reservations?status=PENDING&minPrice=100&maxPrice=400&sortBy=price&direction=desc" \
  -H "Authorization: Bearer $TOKEN"
```

## Error format

All errors use the same JSON shape:

```json
{
  "timestamp": "2026-09-24T10:15:30.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/resources",
  "errors": { "name": "name is required" }
}
```

`errors` is present only for validation failures.

| Status | When |
|---|---|
| 400 | Validation failure, malformed JSON, invalid enum/date/parameter |
| 401 | Missing, invalid or expired token; wrong login credentials |
| 403 | Authenticated but not allowed (role or ownership) |
| 404 | Resource, reservation or endpoint not found |
| 409 | Overlapping reservation, or deleting a resource that has reservations |
| 415 | Wrong `Content-Type` |
| 500 | Unexpected error (details are logged, not returned) |

## Business rules

- New reservations always start as `PENDING`. Only an ADMIN can change the status.
- `endTime` must be after `startTime`; `startTime` cannot be in the past when creating.
- `price` must be positive with at most 2 decimal places (stored as `DECIMAL(10,2)`).
- A resource cannot have overlapping reservations unless the earlier one is `CANCELLED`.

## Security design

- **Stateless JWT (HS256).** No sessions, no cookies, CSRF disabled accordingly.
- **BCrypt** password hashing.
- **RBAC** through `@PreAuthorize` (method security), plus a default of "authenticated" for every endpoint except login and the API docs.
- **Ownership** is enforced in the service layer and inside the list query, so filters and sorting can never expose another user's data.
- **Identity from the token:** the create-reservation DTO has no user field; unknown JSON properties are ignored.
- Login failures return the same message for unknown users and wrong passwords.
- `sortBy` is whitelisted, so clients cannot sort by arbitrary entity paths.

## Project structure

```
src/main/java/com/example/booking
├── config/        SecurityConfig, OpenApiConfig, DataSeeder
├── controller/    AuthController, ResourceController, ReservationController
├── dto/           Request/response records, PageResponse, ErrorResponse
├── entity/        User, Resource, Reservation, Role, ReservationStatus
├── exception/     Custom exceptions, GlobalExceptionHandler
├── repository/    Spring Data repositories, ReservationSpecifications
├── security/      JwtService, JwtAuthenticationFilter, UserDetailsService, 401/403 handlers
└── service/       AuthService, ResourceService, ReservationService
```

## Running the tests

```bash
mvn test
```

Tests use an in-memory H2 database (profile `test`), so PostgreSQL is not required.

| Test class | Focus |
|---|---|
| `JwtServiceTest` | Token generation, tampering, expiry, wrong key, weak secret |
| `AuthIntegrationTest` | Login, BCrypt storage, rejection of bad/expired/missing tokens |
| `ResourceSecurityTest` | USER read-only, ADMIN full CRUD, validation |
| `ReservationSecurityTest` | Owner from JWT, ownership, RBAC, validation, double booking |
| `ReservationListTest` | Data scoping, filters, pagination, sorting, invalid parameters |

## Possible improvements

- Flyway/Liquibase migrations instead of `ddl-auto=update`
- Refresh tokens and token revocation
- User registration endpoint
- Rate limiting on `/auth/login`

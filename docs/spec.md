# Mini URL Shortener

## Introduction

Your task is to implement a small **URL shortener service** in Java: a REST API that accepts long URLs, issues unique short codes, redirects visitors to the original URLs, and tracks per-link visit statistics.

This is a backend-focused exercise. It is designed to be completed in **3–4 hours of focused work**; please do not spend more than one working day on it. A minimal frontend page is welcome but optional.

Wherever this document leaves room for choice, you are free to make reasonable design decisions — but every such decision must be **documented and justified** in your README (see *Design Decisions*).

## Problem Statement

Build an HTTP service with the following core capabilities:

1. **Shorten:** submit a long URL and receive a short code.
2. **Redirect:** visiting `/{code}` redirects the client to the original URL.
3. **Inspect:** query a short code's details (original URL, creation time, total visits).
4. **Analyze:** query daily visit counts for the last 7 days.

Visit counting must be accurate under concurrent access.

## API Specification

Base URL: `http://localhost:8080`. All request and response bodies are JSON (`application/json`) unless noted otherwise.

### 1. Create a short link

**Request**

```
POST /api/links
Content-Type: application/json

{
  "url": "https://example.com/some/very/long/path?query=1"
}
```

**Response** — `201 Created`

```json
{
  "code": "aB3xK9",
  "short_url": "http://localhost:8080/aB3xK9",
  "url": "https://example.com/some/very/long/path?query=1",
  "created_at": "2026-10-15T08:30:00Z"
}
```

**Validation rules** (violations return `400 Bad Request`):

- `url` is present and non-empty
- the scheme is `http` or `https`
- the length is at most 2048 characters

**Duplicate URLs:** you decide whether submitting the same URL twice returns the *same* short code (idempotent) or a *new* one. Either is acceptable; the behavior must be consistent and documented in the README.

### 2. Redirect

```
GET /{code}
```

- If the code exists: respond with a redirect (`301` or `302` — your choice, see *Design Decisions*) whose `Location` header is the original URL, and increment the visit counter.
- If the code does not exist: respond `404 Not Found` with a JSON error body.

### 3. Link details

```
GET /api/links/{code}
```

**Response** — `200 OK`

```json
{
  "code": "aB3xK9",
  "url": "https://example.com/some/very/long/path?query=1",
  "created_at": "2026-10-15T08:30:00Z",
  "visit_count": 128
}
```

Returns `404` with a JSON error body if the code does not exist.

### 4. Daily visit statistics

```
GET /api/links/{code}/stats
```

**Response** — `200 OK`

```json
{
  "code": "aB3xK9",
  "days": [
    { "date": "2026-10-09", "count": 0 },
    { "date": "2026-10-10", "count": 3 },
    { "date": "2026-10-11", "count": 0 },
    { "date": "2026-10-12", "count": 17 },
    { "date": "2026-10-13", "count": 42 },
    { "date": "2026-10-14", "count": 51 },
    { "date": "2026-10-15", "count": 15 }
  ]
}
```

- Covers the **last 7 calendar days** including today.
- Days with zero visits are included with `count: 0`.
- Days are ordered from oldest to newest.
- The timezone used to define a "day" (UTC or local) is your choice and must be documented.

### Error response format

All error responses must share a single, consistent JSON shape, for example:

```json
{
  "status": 400,
  "error": "INVALID_URL",
  "message": "url must be a valid http/https URL"
}
```

The exact field names are up to you; consistency across all endpoints is what counts.

## Technical Requirements

- **Language:** Java 17 or later. **Framework:** Spring Boot 3.x is recommended; other JVM frameworks are acceptable if justified in the README.
- **Storage:** a persistent relational database — MySQL, PostgreSQL, or embedded H2 (file mode). Data must survive an application restart. **Pure in-memory storage (e.g., a `HashMap`) is not acceptable.**
- **Concurrency:** visit counting must be safe under concurrent redirects — no lost updates.
- **Short codes:** 6–8 characters from `[a-zA-Z0-9]`, unique across all links.
- **Code quality:** a clear layered structure (e.g., controller / service / repository), input validation, and consistent error handling.
- **Tests:** at least cover the core paths (create → redirect → details → stats) with unit or integration tests.

## Design Decisions

Document the following in your README, with a short justification for each:

1. **Short code generation** — your strategy (random + collision retry, auto-increment ID + base62, hash + truncation, ...) and how collisions are handled.
2. **Redirect status code** — `301` vs `302`, and the implications for visit statistics (browser caching).
3. **Duplicate-URL policy** — same code or new code, and why.
4. **Daily statistics storage** — visit event log vs. per-day aggregate table; the trade-offs of your choice.
5. **Database indexing** — which indexes you created and why.

There is no single correct answer to any of these; we want to see how you reason about trade-offs.

## Deployment Requirements

- Default port **8080** (configurable).
- The evaluator will clone your repository and start the service by following your README — keep it to **one command**, for example:
  - `./mvnw spring-boot:run`, or
  - `./gradlew bootRun`, or
  - `docker compose up` (if you provide a compose file).
- The service must start **without the evaluator manually installing a database**. Either:
  - use embedded H2 (file mode), or
  - provide a `docker-compose.yml` that brings up both the application and MySQL/PostgreSQL.
- The database schema is created automatically on startup (JPA auto-DDL, Flyway, or equivalent).
- Pin the Java version and all dependency versions in your build file; do not rely on undeclared, system-wide tooling.

## Optional Bonus Features (extra credit, not required)

Any of the following add credit — but only after the required features are complete and solid:

- Custom short codes (caller-chosen alias)
- Link expiry (`expires_at`), after which redirects return `410 Gone` or `404`
- Caching of hot links (with an explanation of cache invalidation)
- Rate limiting on the create endpoint
- A `Dockerfile` / `docker-compose.yml` for one-command startup
- A minimal frontend page to create and list links

## Submission Requirements

Your submission shall be included in a repository in GitHub.

**Required layout:** a standard Maven or Gradle project with a `README.md` at the root. Example:

```
root/
├── README.md
├── pom.xml            (or build.gradle + settings.gradle)
└── src/
    ├── main/java/...
    └── test/java/...
```

**The README must contain:**

1. How to build and start the service (exact commands)
2. A short API usage example (e.g., curl commands)
3. Your design decisions (see *Design Decisions*)

**Submission steps:**

1. Create a **private** GitHub repository containing the submission (with the required layout above).
2. Add the GitHub user **`dolphindbDev`** as a collaborator with **Read** permission.
   - In your repository: **Settings** → **Collaborators** → **Add people** → enter `dolphindbDev` → set permission to **Read**.
3. Email the URL of your GitHub repo to huaxian.tan@dolphindb.com.
4. The evaluator will clone your repository, build, and run your service for review.

**Third-party dependencies:** you may use open-source libraries, provided the use complies with the respective open-source licenses. Every dependency must be declared **with a version** in your build file (`pom.xml` / `build.gradle`); do not rely on libraries that are not declared there.

**Deadline:** please submit within **48 hours** of receiving this document. The expected effort is 3–4 focused hours; polish beyond that is your choice.

## Evaluation Criteria

| Dimension | Weight | What we look at |
|---|---|---|
| Functional completeness | 40% | All four core features work end-to-end; service starts with one command |
| Engineering quality | 20% | Layering, validation, consistent errors, readable code, clear README |
| Concurrency & consistency | 15% | No lost visit counts; unique codes guaranteed |
| Design reasoning | 15% | Documented trade-offs, indexing choices |
| Testing | 10% | Meaningful tests on the core paths |

Bonus features can offset minor deductions elsewhere but cannot compensate for missing core functionality.

**AI-assisted coding:** using AI tools during the exercise is permitted, but you are expected to be able to explain and modify every line of your submission during the follow-up interview.

## Example Walkthrough

```bash
# 1. Create a short link
curl -i -X POST http://localhost:8080/api/links \
     -H "Content-Type: application/json" \
     -d '{"url": "https://example.com/some/very/long/path?query=1"}'
# → 201 Created
# {"code":"aB3xK9","short_url":"http://localhost:8080/aB3xK9", ...}

# 2. Follow the short link (without following the redirect)
curl -i http://localhost:8080/aB3xK9
# → 302 Found, Location: https://example.com/some/very/long/path?query=1

# 3. Query details
curl -i http://localhost:8080/api/links/aB3xK9
# → 200 OK {"code":"aB3xK9","visit_count":1, ...}

# 4. Query daily statistics
curl -i http://localhost:8080/api/links/aB3xK9/stats
# → 200 OK {"code":"aB3xK9","days":[{"date":"2026-10-15","count":1}, ...]}

# 5. Unknown code
curl -i http://localhost:8080/zzzzzz
# → 404 Not Found {"status":404,"error":"NOT_FOUND","message":"..."}

# 6. Invalid input
curl -i -X POST http://localhost:8080/api/links \
     -H "Content-Type: application/json" \
     -d '{"url": "not-a-url"}'
# → 400 Bad Request {"status":400,"error":"INVALID_URL","message":"..."}
```

## Constraints & Assumptions

- A single-instance deployment is assumed; horizontal scaling is out of scope (a note on how you *would* scale is welcome, but not required).
- No authentication, authorization, or multi-tenancy is required.
- Malformed JSON and rule-violating URLs are the only invalid inputs you must handle gracefully; all other input can be assumed well-formed.
- Short-link traffic is read-heavy: assume redirects outnumber link creations by orders of magnitude.

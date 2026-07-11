# Expense Splitter API

A Splitwise-style backend for splitting group expenses, built with Spring Boot.
Users create groups, log shared expenses (equally, by exact amount, or by
percentage), and the API tells them the **minimum number of payments**
needed to settle every debt in the group — instead of everyone paying
everyone back individually.

## Why this project exists

Most "CRUD tutorial" projects just save a form to a database. This one has
two pieces of real engineering behind it:

1. **Debt simplification algorithm** — reduces a tangled web of who-owes-whom
   into the fewest possible transactions using a greedy max-creditor /
   max-debtor matching approach (same idea Splitwise calls "Simplify Debts").
   See `BalanceService.java`.
2. **AI-assisted expense categorization** — instead of a category dropdown,
   expense descriptions are classified by calling the Claude API (e.g.
   "Uber to airport" → `Travel`), with graceful fallback to `Uncategorized`
   if the AI call fails or is disabled. See `AiCategorizationService.java`.

## Tech stack

- Java 17, Spring Boot 3
- Spring Security + JWT (stateless auth)
- Spring Data JPA (H2 for local dev, PostgreSQL for production/Docker)
- Docker + docker-compose
- GitHub Actions CI (build, test, package, Docker build)
- JUnit 5 + Mockito for the algorithm's unit test

## Running it

**Fastest way (zero setup, uses in-memory H2 DB):**
```bash
mvn spring-boot:run
```
API is available at `http://localhost:8080`.

**With Docker + real Postgres:**
```bash
docker-compose up --build
```

**To enable AI categorization**, set an API key before starting:
```bash
export AI_CATEGORIZATION_ENABLED=true
export ANTHROPIC_API_KEY=sk-ant-...
```
Without a key, every expense is simply tagged `Uncategorized` — the app
never breaks because of a missing/failed AI call.

## API walkthrough

```
POST /api/auth/register        { name, email, password }         -> { token, ... }
POST /api/auth/login           { email, password }                -> { token, ... }

POST /api/groups                { name, memberEmails: [...] }     -> Group
GET  /api/groups/{id}

POST /api/groups/{id}/expenses  { description, amount, splitType, ... } -> Expense
GET  /api/groups/{id}/expenses

GET  /api/groups/{id}/balances        -> net balance per user
GET  /api/groups/{id}/simplify-debts  -> minimal list of "who pays whom, how much"
POST /api/groups/{id}/settlements     { payerId, payeeId, amount } -> record a real-world payment
```

All routes except `/api/auth/**` require an `Authorization: Bearer <token>`
header.

### Example: splitting a dinner bill equally
```bash
curl -X POST http://localhost:8080/api/groups/1/expenses \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Dinner at Olive Garden",
    "amount": 90.00,
    "splitType": "EQUAL",
    "participantUserIds": [1, 2, 3]
  }'
```

### Example: getting the simplified settlement plan
```bash
curl http://localhost:8080/api/groups/1/simplify-debts -H "Authorization: Bearer <token>"
```
```json
[
  { "fromUserId": 3, "fromUserName": "Charlie", "toUserId": 1, "toUserName": "Alice", "amount": 45.00 },
  { "fromUserId": 2, "fromUserName": "Bob",     "toUserId": 1, "toUserName": "Alice", "amount": 15.00 }
]
```

## How to talk about this project in an interview

- **"Walk me through the architecture"** — controller → service → repository
  layers, JWT filter running before Spring Security's auth chain, stateless
  sessions so it scales horizontally.
- **"What's the hardest part you built?"** — the debt simplification
  algorithm: explain the greedy max-heap matching approach, why it's bounded
  by `n - 1` transactions, and the trade-off vs. a naive pairwise-ledger
  approach.
- **"How would you scale this?"** — split into services (users, expenses,
  notifications) behind an API gateway, move balance computation to a
  read-optimized cache/materialized view instead of recomputing from all
  expenses every time, add idempotency keys on settlement writes.
- **"What happens if the AI call fails?"** — explain the graceful
  degradation + caching in `AiCategorizationService`.

## Project structure
```
src/main/java/com/example/expensesplitter/
├── config/          # Spring Security config
├── security/         # JWT util + auth filter
├── model/            # JPA entities
├── repository/       # Spring Data repositories
├── service/           # Business logic (incl. BalanceService, AiCategorizationService)
├── controller/        # REST endpoints
├── dto/               # Request/response payloads
└── exception/         # Centralized error handling
```

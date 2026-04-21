# System Design: Deep Dive Interview Guide

> Use this document to prepare for system design discussions about rate limiting in campus placement interviews.

---

## 1. Token Bucket vs Sliding Window — When to Use Which?

### Token Bucket
- **Analogy**: Imagine a bucket that holds 10 coins. Every request takes 1 coin. Every second, 1 coin is added back (up to max 10).
- **Burst Handling**: If the bucket is full (10 coins) and 8 requests arrive simultaneously, all 8 pass. This is intentional — idle time "saves up" capacity.
- **Memory**: O(1) per key — just stores the token count and a timestamp.
- **Best For**: General APIs, user-facing endpoints, CDN rate limiting.
- **Used By**: AWS API Gateway, Stripe.

### Sliding Window Log
- **Analogy**: Imagine a notebook. Every request writes its timestamp. To check the limit, count how many entries are within the last 60 seconds.
- **Burst Handling**: No burst tolerance. If the limit is 10/min, exactly 10 requests pass in any 60-second window — no exceptions.
- **Memory**: O(N) per key — stores every request timestamp in the window.
- **Best For**: Payment APIs, authentication endpoints, security-critical paths.
- **Used By**: GitHub API, Cloudflare.

### Trade-off Summary
| Aspect | Token Bucket | Sliding Window |
|--------|-------------|----------------|
| Burst tolerance | ✅ Yes | ❌ No (strict) |
| Memory usage | O(1) | O(N) |
| Precision | Approximate | Exact |
| Complexity | Medium | Medium |
| Best for | General APIs | Critical APIs |

---

## 2. Why Redis? Why Not In-Memory?

### The Distributed Problem
If your app runs on 3 server instances behind a load balancer, each instance has its own memory. A user sending 10 requests might hit 3 different servers — each thinks the user sent only 3-4 requests. **The limit is never enforced.**

### Redis as Shared State
Redis acts as a **single source of truth**. All 3 instances read/write to the same Redis keys. The user's request count is globally consistent.

```
Server 1 ──┐
Server 2 ──┼──→ Redis (rate_limit:user:alice:/api/orders = 7)
Server 3 ──┘
```

### Why Not a Database?
Databases (PostgreSQL, MySQL) add ~5-50ms latency per query. Rate limiting runs on **every single request** — even 5ms overhead at 10,000 RPS = 50 seconds of cumulative delay per second. Redis operates at ~0.1ms (sub-millisecond) because it lives entirely in memory.

---

## 3. Lua Scripts and Atomicity — The Race Condition Problem

### The Bug Without Lua
```
Thread A: GET tokens → reads "1"
Thread B: GET tokens → reads "1"     ← RACE CONDITION
Thread A: SET tokens = 0, allow
Thread B: SET tokens = 0, allow      ← BOTH allowed, but only 1 token existed!
```

### The Fix With Lua
Redis executes the entire Lua script **atomically** — no other command can interleave. It acts as a critical section without needing explicit locks.

```lua
-- This entire block executes as ONE atomic operation
local tokens = redis.call('get', key)
if tokens >= 1 then
    redis.call('set', key, tokens - 1)
    return 1  -- allowed
end
return 0  -- denied
```

### Why Not Redis Transactions (MULTI/EXEC)?
Redis transactions don't support reading a value and then making a decision based on it within the same transaction. Lua scripts do — they're Turing-complete programs running inside Redis.

---

## 4. TTL Memory Leak Protection

### The Problem
Without cleanup, rate limit keys accumulate forever. A user who visited once 6 months ago still has a key in Redis consuming memory.

### The Solution
Every Lua script sets `EXPIRE` (TTL) on the key:
```lua
redis.call('setex', key, window_seconds * 2, token_count)
```
The key **automatically self-destructs** after the window expires. No background cleanup jobs needed.

### Why `window * 2` for Token Bucket?
If TTL = window exactly, a key could expire right before a request arrives, resetting the token count to full capacity. Using 2x provides a buffer that prevents this edge-case burst.

---

## 5. Fail-Open Architecture

### The Dilemma
If Redis goes down, you have two choices:
1. **Fail-Closed**: Block ALL requests → your entire API goes down
2. **Fail-Open**: Allow ALL requests → temporary no rate limiting

### Our Choice: Fail-Open
```java
try {
    return strategy.isAllowed(key, limit, window);
} catch (Exception e) {
    log.warn("Redis down, fail-open: {}", e.getMessage());
    return new RateLimitResult(true, limit, limit, 0);  // Allow
}
```

### Why?
- Business availability is more important than strict rate limiting during emergencies.
- If Redis is down for 30 seconds, the worst case is 30 seconds without rate limiting.
- If we fail-closed, the worst case is a **complete service outage** affecting all users.

---

## 6. Thread Safety in Dynamic Configuration

### The Problem
The `AdminController` updates the config map on Thread A, while the `RateLimiterFilter` reads it on Thread B, C, D, E (concurrent HTTP request threads).

### HashMap is NOT Thread-Safe
```java
// Thread A (Admin): endpoints.put("/api/test", newRule)
// Thread B (Filter): endpoints.containsKey("/api/test")  ← Can crash!
```
`HashMap` internally uses arrays and linked lists. Concurrent reads during a write can cause:
- `ConcurrentModificationException`
- Infinite loops (in Java 7 with hash collisions)
- Silent data corruption

### ConcurrentHashMap is the Fix
```java
private Map<String, Rule> endpoints = new ConcurrentHashMap<>();
```
Lock-free reads, segment-locked writes. Zero performance penalty for the filter's read path.

---

## 7. Horizontal Scaling (Redis Cluster)

### The CROSSSLOT Problem
Token Bucket uses TWO keys per rate limit (tokens + timestamp). In Redis Cluster, data is sharded across nodes by key hash. If these two keys hash to different slots, the Lua script gets a `CROSSSLOT` error.

### Our Solution: Explicit KEYS[] Declaration
Both keys are passed via the `KEYS[]` array (not constructed inside Lua via string concatenation), allowing Redis Cluster to validate slot assignment.

### For Cluster Deployment: Hash Tags
To guarantee both keys land on the same slot, wrap the shared portion in `{}`:
```
{rate_limit:user:alice:/api/test}         → slot X
{rate_limit:user:alice:/api/test}:ts      → slot X (same!)
```
Redis only hashes the content inside `{}` for slot assignment.

### Sliding Window: Already Cluster-Safe!
Uses a single ZSET key — no multi-key issues.

### Scaling Path
```
Phase 1: Single Redis instance (current setup)
Phase 2: Redis Sentinel (high availability, automatic failover)
Phase 3: Redis Cluster (horizontal sharding, 100K+ RPS)
```
Phase 2 requires zero code changes. Phase 3 requires adding hash tags to key construction (one-line change in `RateLimiterService.buildKey()`).

---

## 8. Key Design Strategy — Why It Matters

### Identity Hierarchy (Priority Order)
1. `rate_limit:user:{userId}:{path}` — Authenticated user (most granular)
2. `rate_limit:role:{role}:{path}` — Role-based (when no user ID)
3. `rate_limit:ip:{ip}:{path}` — IP fallback (unauthenticated, prevents brute-force)
4. `rate_limit:global:anon:{path}` — Absolute fallback

### Why Per-Endpoint Keys?
Without per-endpoint keys, a user hitting `/api/search` 100 times would exhaust their limit for `/api/checkout` too. Per-endpoint isolation ensures fair usage across different API surfaces.

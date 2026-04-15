Your build is not failing randomly. It’s failing for **two very specific reasons**, and both are obvious from the logs.

---

# 1. Core Failure: `Invalid company code` (ROOT CAUSE)

### What’s happening

Every failing test follows this pattern:

1. Test creates tenant

   ```
   Signup: Created tenant id=X name=...
   ```
2. Then tries login with:

   ```
   companyCode = something
   ```
3. Backend throws:

   ```
   EntityNotFoundException: Invalid company code
   ```
4. Test expects **200**, gets **404**

---

### Why this is happening (no guesswork)

Your test data and your DB data **don’t match**.

Look carefully:

```
Signup created tenant → BUT login fails with "Invalid company code"
```

That means:

> Your signup flow is NOT storing the same `companyCode` that your tests are using.

---

### Proof from logs

Example:

```
Signup → Tenant 1
Login → companyCode=t1-auth ❌ NOT FOUND
```

Later:

```
Signup → Order Corp
Login → companyCode=ORDE5482 ✅ WORKS
```

So:

* Some tenants → correct companyCode generation
* Others → mismatch

---

### Brutal truth

Your tests assume:

```
companyCode = predictable string (t1-auth, billing-corp, etc.)
```

Your backend likely generates:

```
random OR formatted code (ORDE5482, etc.)
```

These two are NOT aligned → tests fail.

---

# 2. Secondary Failure: Missing Bean

```
No bean named 'tenantAwareCacheResolver'
```

This causes:

```
500 INTERNAL_ERROR
```

This is separate from login failure.

---

# Fix Strategy (Do this, not guessing)

## Fix 1 — Make companyCode deterministic (BEST FIX)

In your signup service:

```java
tenant.setCompanyCode(generateCompanyCode(...));
```

You need to **control this in tests**.

### Option A (cleanest)

Override in test profile:

```java
@TestConfiguration
public class TestConfig {

    @Bean
    public CompanyCodeGenerator companyCodeGenerator() {
        return name -> name.toLowerCase().replace(" ", "-");
    }
}
```

Now:

```
Tenant 1 → tenant-1
Billing Corp → billing-corp
```

Matches your tests.

---

### Option B (quick dirty fix)

Modify tests to fetch actual companyCode:

```java
String code = tenantRepository.findByName("Tenant 1")
                              .getCompanyCode();
```

Then use that in login.

---

### Option C (worst but fast)

Hardcode companyCode during signup:

```java
tenant.setCompanyCode(request.getCompanyCode());
```

But this breaks real-world logic.

---

## Fix 2 — Verify signup actually persists companyCode

Check:

```java
Tenant tenant = tenantRepository.save(...);
```

Make sure:

* `companyCode` is NOT null
* It is UNIQUE
* It matches login lookup

Also check login logic:

```java
tenantRepository.findByCompanyCode(companyCode)
```

---

## Fix 3 — Fix missing cache resolver

Error:

```
No bean named 'tenantAwareCacheResolver'
```

### You forgot this bean:

```java
@Bean(name = "tenantAwareCacheResolver")
public CacheResolver tenantAwareCacheResolver(CacheManager cacheManager) {
    return new SimpleCacheResolver(cacheManager);
}
```

OR remove usage:

```java
@Cacheable(cacheResolver = "tenantAwareCacheResolver")
```

If you don’t need it → remove it.

---

## Fix 4 — Remove duplicate dependency (warning, not breaking yet)

```
duplicate spring-boot-starter-data-redis
```

Fix your `pom.xml`.

---

# What’s actually broken (summary)

| Problem                | Severity | Why                     |
| ---------------------- | -------- | ----------------------- |
| companyCode mismatch   | CRITICAL | breaks login everywhere |
| missing cache resolver | HIGH     | causes 500 errors       |
| duplicate dependency   | LOW      | future failure risk     |

---

# What you should do right now (ordered)

1. Fix companyCode generation (this alone will fix ~90% failures)
2. Re-run tests
3. Fix cache resolver bean
4. Clean pom.xml

---

# If you still don’t get it working

Show me:

* `SignupService`
* `AuthService.login()`
* One failing test (full)

I’ll pinpoint the exact line breaking it.

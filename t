*   [33m96154bc[m[33m ([m[1;36mHEAD[m[33m -> [m[1;32mfeature/rate-limit-fix[m[33m, [m[1;31morigin/main[m[33m, [m[1;31morigin/feature/rate-limit-fix[m[33m, [m[1;31morigin/HEAD[m[33m, [m[1;32mmain[m[33m)[m Merge pull request #11 from heisenberg-meth/devin/1776408100-rate-limit-error-msg
[31m|[m[32m\[m  
[31m|[m * [33m8f1a3ac[m[33m ([m[1;31morigin/devin/1776408100-rate-limit-error-msg[m[33m)[m refactor(rate-limit): extract requirePositive helper and shared message format
[31m|[m * [33mce3da04[m fix(rate-limit): align config validation error messages with property keys
[31m|[m[31m/[m  
*   [33m3f3d646[m Merge pull request #9 from heisenberg-meth/devin/1776406977-rate-limit-followup
[33m|[m[34m\[m  
[33m|[m * [33md55fbfb[m[33m ([m[1;31morigin/devin/1776406977-rate-limit-followup[m[33m)[m fix: address code review feedback on rate limiting
* [34m|[m   [33m087bba3[m Merge pull request #10 from heisenberg-meth/ralph-loop-okjel
[34m|[m[36m\[m [34m\[m  
[34m|[m [36m|[m[34m/[m  
[34m|[m[34m/[m[36m|[m   
[34m|[m * [33mbec66dd[m refactor: rename TenantContext methods and update integration tests to include user verification.
[34m|[m * [33m096162d[m refactor: migrate integration tests to Testcontainers with PostgreSQL and enforce tenant ID assignment in service layer
[34m|[m * [33m58c6725[m refactor: introduce AuditAction enum, add CategoryResponse DTO, and optimize product database indexes
[34m|[m * [33m97a85e3[m feat: implement platform subscription, payment gateway, webhook, notification, and invite systems with associated database migrations and controllers.
[34m|[m * [33mafee7ea[m refactor: enforce multi-tenancy in services, improve cache resolution, and add default category seeding on signup
[34m|[m * [33mac4f252[m refactor: remove redundant @SuppressWarnings("null") annotations across services and integration tests
* [36m|[m   [33m2f15f37[m Merge pull request #8 from heisenberg-meth/devin/1776406277-rate-limiting
[1;31m|[m[1;32m\[m [36m\[m  
[1;31m|[m * [36m|[m [33m7c42ade[m[33m ([m[1;31morigin/devin/1776406277-rate-limiting[m[33m)[m feat: configure and test tier-based API rate limiting
[1;31m|[m[1;31m/[m [36m/[m  
* [36m|[m [33m64c1927[m Merge pull request #7 from heisenberg-meth/ralph-loop-okjel
[1;33m|[m[36m\[m[36m|[m 
[1;33m|[m * [33m8b836a5[m refactor: update integration tests to use dynamically generated company codes and update nginx upstream configuration
* [1;34m|[m   [33m3ca3f0c[m Merge pull request #6 from heisenberg-meth/heisenberg-meth-patch-1
[1;35m|[m[1;36m\[m [1;34m\[m  
[1;35m|[m * [1;34m|[m [33m9c415c7[m[33m ([m[1;31morigin/heisenberg-meth-patch-1[m[33m)[m Update RateLimitFilter.java
[1;35m|[m[1;35m/[m [1;34m/[m  
* [1;34m|[m [33m6b9e870[m Merge pull request #5 from heisenberg-meth/ralph-loop-okjel
[31m|[m[1;34m\[m[1;34m|[m 
[31m|[m * [33mb7039ca[m feat: implement workspace support, improve Redis resilience, and harden production deployment configuration
* [32m|[m [33md21e62b[m Merge pull request #4 from heisenberg-meth/ralph-loop-okjel
[33m|[m[32m\[m[32m|[m 
[33m|[m * [33m7ef7b3a[m feat: implement multi-tenant enhancements, including company code support and expanded role-based access control configurations
[33m|[m[33m/[m  
* [33mb2b8ccc[m refactor: improve null safety in SupportService and BaseIntegrationTest using explicit null checks and annotations
* [33m2304647[m deleted
* [33mdefe60d[m refactor: standardize integration tests by extending BaseIntegrationTest and utilizing centralized database cleanup methods
* [33me848d66[m refactor: improve integration test cleanup, update user fetch strategy, and add error handling test endpoint
* [33m457d63c[m feat: implement correlation ID tracing, add business details to tenant models, and harden security with rate limiting and error handling.
* [33mc1c8173[m feat: implement RBAC system with custom user permissions, permission-aware role fetching, and audit logging
* [33m5f1f0b1[m feat: implement tenant-scoped authentication, audit logging, and database schema updates for expiry thresholds and transfer orders
*   [33m1a47d1b[m Merge pull request #3 from heisenberg-meth/ralph-loop-jecdr
[35m|[m[36m\[m  
[35m|[m * [33md042679[m feat: implement role-based access control, subscription management, and support ticketing systems
[35m|[m * [33m6efcd30[m Add Swagger annotations to PlatformUserController for proper API documentation
* [36m|[m [33m416acb8[m Merge pull request #2 from heisenberg-meth/ralph-loop-jecdr
[1;31m|[m[36m\[m[36m|[m 
[1;31m|[m * [33mb43b5b5[m Add missing POST /api/tenant/stock/transfer endpoint
[1;31m|[m * [33mff91901[m refactor: add NonNull annotations and null safety checks across service and repository layers
[1;31m|[m * [33mfb679fe[m fix: tenant FK violation on signup - commit tenant before user insert
[1;31m|[m * [33m421afb3[m fix: payment DTO, scope null in mapper, test pagination path
* [1;32m|[m [33m8d849f0[m Merge pull request #1 from heisenberg-meth/ralph-loop-seyes
[1;33m|[m[1;32m\[m[1;32m|[m 
[1;33m|[m * [33m806eaf7[m Implement global system config, feature flags, support mode data masking and enforce user limits
[1;33m|[m * [33m8aa36f1[m feat(sale): implement full sale with billing flow, root discount_total and auto-payment as per TASKS.md
[1;33m|[m * [33m3cc56cf[m feat: implement user scope for governance and tax_rate for categories; add SaleController
[1;33m|[m[1;33m/[m  
* [33maec6cc4[m chore: update docker port mapping, clean up unused imports, and ignore .env files
* [33mefb5977[m fix: Use unfiltered user retrieval methods for authentication and token refresh.
* [33m35ebd8e[m Delete PRD.md
* [33mdc2a8d2[m Delete IMS_PRD_Complete.docx
* [33m3e99c84[m Delete CLAUDE.md
* [33m7c7e0d9[m Delete .env
* [33md571968[m feat(phase-1): finalize project setup and infrastructure
* [33md668be1[m feat(01-01): update Java version to 21 in pom.xml
* [33mddbf529[m feat: Implement payment processing, user signup, category management, and introduce new DTOs for invoice and transfer order status updates.
* [33m37f7877[m docs: initialize project with 11-phase roadmap and setup infrastructure (Phase 1)
* [33mf670b18[m feat: Implement comprehensive reporting, warehouse transfer order management, domain-specific services for pharmacy and supermarket, platform statistics, and update Docker PostgreSQL port.
* [33m01aaff6[m feat: Implement initial multi-tenant inventory management system backend with Docker Compose, PostgreSQL, Redis, and Nginx.
* [33m1286bc9[m feat: initialize Spring Boot Inventory Management System project with PRD and basic application structure.

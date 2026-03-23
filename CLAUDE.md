<!-- GSD:project-start source:PROJECT.md -->
## Project

**IMS — Inventory Management System**

The Inventory Management System (IMS) is a production-grade, multi-tenant SaaS platform serving pharmacies, supermarkets, warehouses, and retail businesses on a single shared backend. It provides a unified inventory engine with domain-specific extensions, strict data isolation, and role-based access control.

**Core Value:** A scalable, multi-tenant inventory platform that provides complete data isolation and specialized business logic for diverse industries (Pharmacy, Supermarket, Warehouse, Retail) on a shared high-performance infrastructure.

### Constraints

- **Security**: `tenant_id` MUST be extracted from JWT only; never from request parameters.
- **Performance**: Use Valkey/Redis for caching products, stock levels, and reports to ensure scalability.
- **Timeline**: 1-week MVP target requires focused, prioritized implementation.
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->
## Technology Stack

## Recommended Stack
### Core Framework
| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **Java** | 21 (LTS) | Core Runtime | Required for Virtual Threads (Project Loom) which significantly improves throughput in I/O heavy multi-tenant systems. |
| **Spring Boot** | 3.4.4 | Application Framework | Latest stable release as of March 2025. Provides native support for Spring Framework 6.2 and Jakarta EE 10. |
| **Maven** | 3.9+ | Build Tool | Standard for Java projects; well-supported by all major IDEs and CI/CD pipelines. |
### Database & Migrations
| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **PostgreSQL** | 17.4 | Primary Data Store | Latest stable version. Features improved `JSON_TABLE` support for flexible domain extensions and optimized `IN` queries for multi-tenant filters. |
| **Flyway** | 11.10.x | Schema Migrations | Industry standard for versioned, reproducible database schema changes. |
| **Hibernate** | 6.6+ | ORM | Included with Spring Boot. Enhanced support for modern PostgreSQL features and better query plan caching. |
### Caching & Rate Limiting
| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **Valkey** | 8.1.0 | Cache & Store | The community-driven, fully open-source fork of Redis. Version 8.1.0 reduces memory overhead by 20% and provides native Bloom filter support for efficient existence checks. |
### Security & Auth
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **Spring Security**| 6.4.x | Auth/RBAC | Core security framework for all API protection. |
| **JJWT** | 0.12.6 | JWT Handling | Used for generating and parsing signed JWTs. Version 0.12 introduced a more fluent API and better security defaults. |
### Supporting Libraries
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **iText Core** | 9.1.0 | PDF Generation | Used for generating high-quality invoices and reports. Version 9 provides superior security and support for modern PDF standards. |
| **Springdoc OpenAPI** | 2.8.5 | API Documentation | Generates Swagger UI and OpenAPI 3.0 specs. Optimized for Spring Boot 3.4.x compatibility. |
| **Lombok** | 1.18.34 | Productivity | Reduces boilerplate code (getters, setters, builders). |
| **Testcontainers** | 1.20.x | Integration Testing | Spins up real PostgreSQL and Valkey containers during tests to ensure tenant isolation works in a production-like environment. |
## Alternatives Considered
| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| **Database** | PostgreSQL | MySQL 8.4 | PostgreSQL has superior JSON handling and better support for the complex indexing needed for multi-tenant filters. |
| **Cache** | Valkey | Redis 7.4 (SSPL) | Valkey is the community-maintained, BSD-licensed alternative after the Redis licensing change. It is 100% API compatible. |
| **Multi-tenancy** | Shared DB | Schema-per-tenant | Schema-per-tenant is harder to scale to thousands of small tenants (migration overhead, connection pooling limits). Column-based isolation is more cost-effective for an MVP. |
| **PDF** | iText 9 | Apache FOP | iText is significantly easier to use for programmatic PDF generation with modern layouts compared to XSL-FO. |
## Installation
### Core Dependencies (Maven pom.xml)
## What NOT to Use and Why
*   **Spring Boot 2.x:** No support for Jakarta EE 10; requires older Java versions. Performance is significantly lower than Boot 3.4 with Virtual Threads.
*   **Plain Redis (Latest):** Unless your organization specifically requires it, Valkey 8.x is the preferred community-maintained choice with a more permissive license and better performance profile for 2025.
*   **JWT in URL or Body:** Never pass `tenant_id` via request parameters. This leads to insecure IDOR (Insecure Direct Object Reference) vulnerabilities. Always extract from the signed JWT payload.
*   **Global Filters for Multitenancy:** Avoid Hibernate `@Filter` or `@TenantId` annotations if they obscure the `tenant_id` requirement. For an MVP, explicit `tenant_id` in repository methods is safer and easier to debug, though Hibernate 6's `@TenantId` is a valid option if used correctly with a `CurrentTenantIdentifierResolver`.
*   **Blocking Database Drivers:** EnsureHikariCP is tuned for the number of available connections; do not use non-blocking drivers (R2DBC) unless the entire stack is reactive, as it adds significant complexity.
## Sources
- [Spring Boot Release Notes (March 2025)](https://spring.io/projects/spring-boot)
- [PostgreSQL 17 Release Notes](https://www.postgresql.org/docs/17/release-17.html)
- [Valkey 8.1.0 Announcement](https://valkey.io/)
- [iText 9 Documentation](https://kb.itextpdf.com/home/it9)
- [Springdoc OpenAPI 2.x Compatibility](https://springdoc.org/v2/)
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd:profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->

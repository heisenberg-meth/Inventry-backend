# IMS - Inventory Management System

A multi-tenant SaaS inventory management platform built with Spring Boot, PostgreSQL, and Valkey.

## Tech Stack
- **Backend**: Java 21, Spring Boot 3.4.4
- **Database**: PostgreSQL 17.4
- **Cache**: Valkey 8.1.0 (Redis-compatible)
- **Proxy**: Nginx
- **API Docs**: SpringDoc OpenAPI (Swagger UI)

## Getting Started

### Prerequisites
- Docker and Docker Compose
- Java 21 (for local development)
- Maven 3.9+

### Environment Setup
1. Create a `.env` file from the provided template (if applicable) or ensure the following variables are set:
   ```env
   DB_PASSWORD=your_db_password
   REDIS_PASSWORD=your_redis_password
   JWT_SECRET=your_jwt_secret_key_at_least_256_bit
   ```

2. Start the infrastructure using Docker Compose:
   ```bash
   docker compose up -d --build
   ```

3. Access the API Documentation:
   - **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
   - **OpenAPI Specs**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Development
- The backend runs on port `8080` (or `80` via Nginx proxy).
- PostgreSQL is accessible on port `5433` (mapped from `5432`).
- Valkey (Redis) is accessible on port `6379`.

## Multi-tenancy
The system uses a shared database with discriminator columns (`tenant_id`). Tenant isolation is enforced at the Hibernate level using the `@TenantId` annotation.

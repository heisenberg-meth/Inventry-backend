# IMS — Inventory Management System

## Product Requirements Document (PRD) v1.0

**Stack:** Java 21 + Spring Boot 3.x | PostgreSQL 16 | Valkey/Redis 7.2 | Docker

**Build Target:** 1 Week | Agent-Driven | Phase-by-Phase

**Business Types:** Pharmacy | Supermarket | Warehouse | Retail

---

## TABLE OF CONTENTS

1. [Project Overview](https://claude.ai/chat/7641a8de-f1a8-4326-a731-902c9ffaf16a#1-project-overview)
2. [System Architecture](https://claude.ai/chat/7641a8de-f1a8-4326-a731-902c9ffaf16a#2-system-architecture)
3. [Role-Based Access Control (RBAC)](https://claude.ai/chat/7641a8de-f1a8-4326-a731-902c9ffaf16a#3-role-based-access-control-rbac)
4. [Database Design](https://claude.ai/chat/7641a8de-f1a8-4326-a731-902c9ffaf16a#4-database-design)
5. [API Design](https://claude.ai/chat/7641a8de-f1a8-4326-a731-902c9ffaf16a#5-api-design)
6. [Docker &amp; Infrastructure](https://claude.ai/chat/7641a8de-f1a8-4326-a731-902c9ffaf16a#6-docker--infrastructure)
7. [Project Structure](https://claude.ai/chat/7641a8de-f1a8-4326-a731-902c9ffaf16a#7-project-structure)
8. [One-Week Development Plan](https://claude.ai/chat/7641a8de-f1a8-4326-a731-902c9ffaf16a#8-one-week-development-plan-agent-driven)
9. [MVP Feature Checklist](https://claude.ai/chat/7641a8de-f1a8-4326-a731-902c9ffaf16a#9-mvp-feature-checklist)
10. [Technology Stack](https://claude.ai/chat/7641a8de-f1a8-4326-a731-902c9ffaf16a#10-technology-stack)
11. [Key Implementation Notes](https://claude.ai/chat/7641a8de-f1a8-4326-a731-902c9ffaf16a#11-key-implementation-notes)

---

## 1. Project Overview

### 1.1 What Are We Building?

The **Inventory Management System (IMS)** is a production-grade, multi-tenant SaaS platform serving pharmacies, supermarkets, warehouses, and retail businesses on a single shared backend.

It provides:

* A unified inventory engine (products, stock, orders, billing, reports)
* Domain-specific extensions per business type
* Strict data isolation — every tenant's data is completely separate
* Role-based access at both platform and tenant level
* Redis-cached, Docker-containerized, horizontally scalable architecture

### 1.2 Problem Statement

Small to medium businesses managing physical inventory rely on spreadsheets, disconnected tools, or expensive enterprise software. IMS bridges this gap with a scalable, affordable SaaS platform that grows with the business.

### 1.3 Core Goals

* **Multi-tenant:** One platform, many independent businesses, full data isolation via `tenant_id`
* **Domain-driven:** Pharmacy, supermarket, warehouse, retail each have unique flows and DB extensions
* **RBAC:** Granular permissions across platform level (Root, Platform Admin) and tenant level (Admin, Manager, Staff)
* **Production-ready:** Docker Compose, Valkey/Redis caching, Flyway migrations, Swagger docs
* **One-week MVP:** Phased, agent-tasked delivery

### 1.4 Supported Business Types

| Business Type         | Key Differentiator                                      | Build Priority |
| --------------------- | ------------------------------------------------------- | -------------- |
| **Pharmacy**    | Expiry tracking, batch numbers, medicine rules, GST/HSN | Phase 1 (MVP)  |
| **Supermarket** | Barcode scanning, fast billing, discount engine         | Phase 2 (v1.1) |
| **Warehouse**   | Storage locations, bulk ops, transfer orders            | Phase 2 (v1.1) |
| **Retail**      | POS-style billing, customer loyalty, returns            | Phase 3 (v1.2) |

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
+------------------------------------------------------------------+
|                        CLIENT LAYER                              |
|      Flutter Web / Mobile App   |   REST API Consumers          |
+------------------------------+-----------------------------------+
                               | HTTPS
+------------------------------v-----------------------------------+
|                    NGINX / API GATEWAY                           |
|       Rate Limiting  |  SSL Termination  |  Routing             |
+----------+-----------------------------------------+-----------+
           |                                         |
+----------v---------------+         +--------------v------------+
|   /api/platform/*        |         |   /api/tenant/*           |
|   Platform Admin APIs    |         |   Business APIs           |
|   Spring Boot Service    |         |   Spring Boot Service     |
+----------+---------------+         +--------------+------------+
           |                                         |
+----------v-----------------------------------------v-----------+
|                   SHARED SERVICES LAYER                         |
|   JWT Auth  |  Tenant Resolver  |  RBAC Filter  |  Audit Log  |
+-------------------------+-----------------------------------------+
                          |
+-------------------------v-----------------------------------------+
|                      DATA LAYER                                    |
|  PostgreSQL 16 (Primary DB)  |  Valkey 7.2 (Cache + Sessions)    |
+-------------------------------------------------------------------+
```

### 2.2 Multi-Tenant Data Architecture

**Approach:** Shared Database — one PostgreSQL instance, every table has a `tenant_id` column.

```
+---------------------------------------------+
|            PostgreSQL Database              |
|                                             |
|  products table:                            |
|  +-------------------------------------+    |
|  | id | tenant_id | name  | price| stock|   |
|  |  1 |   1001    | Med A | 120.0|  50  |   |  <- Tenant 1001
|  |  2 |   1001    | Med B |  80.0|  30  |   |  <- Tenant 1001
|  |  3 |   1002    | Rice  |  60.0| 200  |   |  <- Tenant 1002
|  +-------------------------------------+    |
|                                             |
|  RULE: ALL queries -> WHERE tenant_id = ?  |
+---------------------------------------------+
```

**Tenant Identification — JWT (Most Secure):**

```json
{
  "user_id": 10,
  "tenant_id": 1001,
  "role": "MANAGER",
  "scope": "TENANT",
  "business_type": "PHARMACY"
}
```

> **CRITICAL:** NEVER read `tenant_id` from request params, query strings, or request body. ALWAYS extract from the signed JWT token only.

### 2.3 Domain Layer Architecture

```
+----------------------------------------------------------+
|                INVENTORY CORE (Shared)                   |
|  Product CRUD | Stock In/Out | Orders | Billing | Reports|
+------------+-----------+------------+--------------------+
             |           |            |
+------------v--+  +-----v------+  +-v----------------+
|  PHARMACY EXT |  | SUPERMARKET|  |  WAREHOUSE EXT   |
|  expiry_date  |  | barcode    |  | storage_location |
|  batch_number |  | discounts  |  | bulk_ops         |
|  medicine_    |  | fast_bill  |  | transfer_orders  |
|  rules        |  +------------+  +------------------+
+---------------+
```

### 2.4 Caching Strategy (Valkey / Redis)

| Cache Key                          | TTL   | Use Case              |
| ---------------------------------- | ----- | --------------------- |
| `jwt:blacklist:{token_hash}`     | 24h   | Logout / token revoke |
| `tenant:{tenant_id}:config`      | 1h    | Tenant settings       |
| `product:{tenant_id}:list`       | 15min | Product catalog       |
| `stock:{tenant_id}:{product_id}` | 5min  | Live stock quantity   |
| `report:{tenant_id}:daily`       | 30min | Dashboard KPIs        |
| `rate:{ip}:api`                  | 1min  | Rate limiting counter |
| `session:{user_id}`              | 8h    | User session          |

**Cache Invalidation:** Event-driven via Spring `ApplicationEvents`.

Example: `StockUpdatedEvent` -> evict `stock:{tenantId}:{productId}` from cache.

---

## 3. Role-Based Access Control (RBAC)

### 3.1 Role Definitions

**Platform Level:**

| Role               | Description                                                              |
| ------------------ | ------------------------------------------------------------------------ |
| `ROOT`           | Full system control. Manages platform config, tenant lifecycle, all data |
| `PLATFORM_ADMIN` | Manage tenants, assign subscription plans                                |
| `SUPPORT_ADMIN`  | Read-only view of tenant data for support                                |

**Tenant Level:**

| Role             | Description                                                     |
| ---------------- | --------------------------------------------------------------- |
| `ADMIN`(Owner) | Full control of their business — config, users, all operations |
| `MANAGER`      | Manage inventory, purchases, sales workflows                    |
| `STAFF`        | Limited: billing and stock updates only                         |

### 3.2 RBAC Permission Matrix

| Module / Action     | ROOT | PLATFORM_ADMIN | ADMIN | MANAGER | STAFF |
| ------------------- | ---- | -------------- | ----- | ------- | ----- |
| Tenant CRUD         | YES  | YES            | NO    | NO      | NO    |
| User Management     | YES  | YES            | YES   | NO      | NO    |
| Product CRUD        | YES  | YES            | YES   | YES     | NO    |
| Stock Adjustments   | YES  | YES            | YES   | YES     | YES   |
| Sales Orders        | YES  | YES            | YES   | YES     | YES   |
| Purchase Orders     | YES  | YES            | YES   | YES     | NO    |
| Billing / Invoices  | YES  | YES            | YES   | YES     | YES   |
| Reports & Analytics | YES  | YES            | YES   | YES     | NO    |
| System Config       | YES  | NO             | NO    | NO      | NO    |
| Subscription Plans  | YES  | YES            | NO    | NO      | NO    |

### 3.3 JWT Token Structures

**Platform Token:**

```json
{
  "user_id": 1,
  "role": "ROOT",
  "scope": "PLATFORM",
  "iat": 1709000000,
  "exp": 1709086400
}
```

**Tenant Token:**

```json
{
  "user_id": 10,
  "tenant_id": 1001,
  "role": "MANAGER",
  "scope": "TENANT",
  "business_type": "PHARMACY",
  "iat": 1709000000,
  "exp": 1709086400
}
```

**Security Rules (Non-Negotiable):**

* Always extract `tenant_id` from JWT — NEVER from query params or request body
* ALL repository queries MUST include `WHERE tenant_id = :tenantId`
* RBAC annotation on every controller method
* JWT blacklist stored in Redis on every logout
* Tokens expire in 24h; support refresh token flow

---

## 4. Database Design

### 4.1 Core Tables (Flyway: V1__create_core_tables.sql)

```sql
-- TENANTS
CREATE TABLE tenants (
  id            BIGSERIAL PRIMARY KEY,
  name          VARCHAR(255) NOT NULL,
  domain        VARCHAR(255) UNIQUE,
  business_type VARCHAR(50)  NOT NULL,
  plan          VARCHAR(50)  DEFAULT 'FREE',
  status        VARCHAR(20)  DEFAULT 'ACTIVE',
  created_at    TIMESTAMP    DEFAULT NOW()
);

-- USERS
CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  tenant_id     BIGINT       REFERENCES tenants(id),
  name          VARCHAR(255) NOT NULL,
  email         VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          VARCHAR(50)  NOT NULL,
  is_active     BOOLEAN      DEFAULT TRUE,
  created_at    TIMESTAMP    DEFAULT NOW()
);

-- CATEGORIES
CREATE TABLE categories (
  id            BIGSERIAL PRIMARY KEY,
  tenant_id     BIGINT       NOT NULL REFERENCES tenants(id),
  name          VARCHAR(255) NOT NULL,
  description   TEXT,
  created_at    TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX idx_categories_tenant ON categories(tenant_id);

-- PRODUCTS (Core)
CREATE TABLE products (
  id              BIGSERIAL PRIMARY KEY,
  tenant_id       BIGINT        NOT NULL REFERENCES tenants(id),
  name            VARCHAR(255)  NOT NULL,
  sku             VARCHAR(100),
  barcode         VARCHAR(100),
  category_id     BIGINT        REFERENCES categories(id),
  unit            VARCHAR(50),
  purchase_price  DECIMAL(10,2),
  sale_price      DECIMAL(10,2) NOT NULL,
  stock           INTEGER       DEFAULT 0,
  reorder_level   INTEGER       DEFAULT 10,
  is_active       BOOLEAN       DEFAULT TRUE,
  created_at      TIMESTAMP     DEFAULT NOW(),
  updated_at      TIMESTAMP     DEFAULT NOW()
);
CREATE INDEX idx_products_tenant  ON products(tenant_id);
CREATE INDEX idx_products_sku     ON products(tenant_id, sku);
CREATE INDEX idx_products_barcode ON products(tenant_id, barcode);

-- SUPPLIERS
CREATE TABLE suppliers (
  id            BIGSERIAL PRIMARY KEY,
  tenant_id     BIGINT       NOT NULL REFERENCES tenants(id),
  name          VARCHAR(255) NOT NULL,
  phone         VARCHAR(50),
  email         VARCHAR(255),
  address       TEXT,
  gstin         VARCHAR(20),
  created_at    TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX idx_suppliers_tenant ON suppliers(tenant_id);

-- CUSTOMERS
CREATE TABLE customers (
  id            BIGSERIAL PRIMARY KEY,
  tenant_id     BIGINT       NOT NULL REFERENCES tenants(id),
  name          VARCHAR(255) NOT NULL,
  phone         VARCHAR(50),
  email         VARCHAR(255),
  address       TEXT,
  created_at    TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX idx_customers_tenant ON customers(tenant_id);

-- ORDERS
CREATE TABLE orders (
  id            BIGSERIAL PRIMARY KEY,
  tenant_id     BIGINT        NOT NULL REFERENCES tenants(id),
  type          VARCHAR(20)   NOT NULL,
  status        VARCHAR(30)   DEFAULT 'PENDING',
  customer_id   BIGINT        REFERENCES customers(id),
  supplier_id   BIGINT        REFERENCES suppliers(id),
  total_amount  DECIMAL(12,2),
  tax_amount    DECIMAL(12,2),
  discount      DECIMAL(12,2) DEFAULT 0,
  notes         TEXT,
  created_by    BIGINT        REFERENCES users(id),
  created_at    TIMESTAMP     DEFAULT NOW()
);
CREATE INDEX idx_orders_tenant  ON orders(tenant_id);
CREATE INDEX idx_orders_type    ON orders(tenant_id, type);
CREATE INDEX idx_orders_created ON orders(tenant_id, created_at);

-- ORDER ITEMS
CREATE TABLE order_items (
  id            BIGSERIAL PRIMARY KEY,
  order_id      BIGINT        NOT NULL REFERENCES orders(id),
  product_id    BIGINT        NOT NULL REFERENCES products(id),
  quantity      INTEGER       NOT NULL,
  unit_price    DECIMAL(10,2) NOT NULL,
  discount      DECIMAL(10,2) DEFAULT 0,
  tax_rate      DECIMAL(5,2)  DEFAULT 0,
  total         DECIMAL(10,2) NOT NULL
);

-- INVOICES
CREATE TABLE invoices (
  id              BIGSERIAL PRIMARY KEY,
  tenant_id       BIGINT        NOT NULL REFERENCES tenants(id),
  order_id        BIGINT        REFERENCES orders(id),
  invoice_number  VARCHAR(50)   NOT NULL,
  amount          DECIMAL(12,2) NOT NULL,
  tax_amount      DECIMAL(12,2),
  status          VARCHAR(20)   DEFAULT 'UNPAID',
  due_date        DATE,
  paid_at         TIMESTAMP,
  created_at      TIMESTAMP     DEFAULT NOW()
);
CREATE INDEX idx_invoices_tenant ON invoices(tenant_id);

-- STOCK MOVEMENTS (Audit Trail)
CREATE TABLE stock_movements (
  id              BIGSERIAL PRIMARY KEY,
  tenant_id       BIGINT      NOT NULL,
  product_id      BIGINT      NOT NULL REFERENCES products(id),
  movement_type   VARCHAR(30) NOT NULL,
  quantity        INTEGER     NOT NULL,
  previous_stock  INTEGER,
  new_stock       INTEGER,
  reference_id    BIGINT,
  reference_type  VARCHAR(30),
  notes           TEXT,
  created_by      BIGINT      REFERENCES users(id),
  created_at      TIMESTAMP   DEFAULT NOW()
);
CREATE INDEX idx_stock_movements_tenant  ON stock_movements(tenant_id);
CREATE INDEX idx_stock_movements_product ON stock_movements(tenant_id, product_id);
CREATE INDEX idx_stock_movements_date    ON stock_movements(tenant_id, created_at);
```

### 4.2 Pharmacy Extension (Flyway: V2__pharmacy_extension.sql)

```sql
CREATE TABLE pharmacy_products (
  product_id    BIGINT       PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
  batch_number  VARCHAR(100),
  expiry_date   DATE         NOT NULL,
  manufacturer  VARCHAR(255),
  hsn_code      VARCHAR(50),
  schedule      VARCHAR(10)
);
CREATE INDEX idx_pharmacy_expiry ON pharmacy_products(expiry_date);
```

### 4.3 Warehouse Extension (Flyway: V3__warehouse_extension.sql)

```sql
CREATE TABLE warehouse_products (
  product_id        BIGINT       PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
  storage_location  VARCHAR(100),
  zone              VARCHAR(50),
  rack              VARCHAR(50),
  bin               VARCHAR(50)
);

CREATE TABLE transfer_orders (
  id              BIGSERIAL PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL REFERENCES tenants(id),
  from_location   VARCHAR(100) NOT NULL,
  to_location     VARCHAR(100) NOT NULL,
  status          VARCHAR(30)  DEFAULT 'PENDING',
  notes           TEXT,
  created_by      BIGINT       REFERENCES users(id),
  created_at      TIMESTAMP    DEFAULT NOW()
);
```

### 4.4 Entity Relationship (Core)

```
tenants
  +-- users          (tenant_id FK)
  +-- categories     (tenant_id FK)
  +-- products       (tenant_id FK)
  |     +-- pharmacy_products  (product_id FK)  <- pharmacy only
  |     +-- warehouse_products (product_id FK)  <- warehouse only
  +-- suppliers      (tenant_id FK)
  +-- customers      (tenant_id FK)
  +-- orders         (tenant_id FK)
  |     +-- order_items (order_id FK, product_id FK)
  +-- invoices       (tenant_id FK)
  +-- stock_movements (tenant_id FK, product_id FK)
```

---

## 5. API Design

### 5.1 Base URL Structure

```
/api/platform/*   ->  Platform Admin APIs  (ROOT, PLATFORM_ADMIN)
/api/tenant/*     ->  Business APIs        (All tenant roles, filtered by JWT tenant_id)
/api/auth/*       ->  Public Auth APIs
```

### 5.2 Authentication Endpoints

| Method   | Endpoint              | Auth   | Description                                 |
| -------- | --------------------- | ------ | ------------------------------------------- |
| `POST` | `/api/auth/login`   | Public | Login, returns access + refresh JWT         |
| `POST` | `/api/auth/logout`  | Any    | Blacklists JWT in Redis                     |
| `POST` | `/api/auth/refresh` | Any    | Exchange refresh token for new access token |

**Login Request:**

```json
{
  "email": "admin@pharmacy.com",
  "password": "secret"
}
```

**Login Response:**

```json
{
  "access_token": "eyJ...",
  "refresh_token": "eyJ...",
  "expires_in": 86400,
  "tenant_id": 1001,
  "role": "ADMIN",
  "business_type": "PHARMACY"
}
```

### 5.3 Platform Admin Endpoints

| Method     | Endpoint                       | Roles                | Description                     |
| ---------- | ------------------------------ | -------------------- | ------------------------------- |
| `GET`    | `/api/platform/tenants`      | ROOT, PLATFORM_ADMIN | List all tenants (paginated)    |
| `POST`   | `/api/platform/tenants`      | ROOT, PLATFORM_ADMIN | Create new tenant               |
| `GET`    | `/api/platform/tenants/{id}` | ROOT, PLATFORM_ADMIN | Get tenant details              |
| `PATCH`  | `/api/platform/tenants/{id}` | ROOT                 | Update tenant plan/status       |
| `DELETE` | `/api/platform/tenants/{id}` | ROOT                 | Deactivate tenant (soft delete) |
| `GET`    | `/api/platform/stats`        | ROOT                 | Platform-wide metrics           |

### 5.4 Product Endpoints

| Method     | Endpoint                           | Roles          | Description                           |
| ---------- | ---------------------------------- | -------------- | ------------------------------------- |
| `GET`    | `/api/tenant/products`           | ALL_TENANT     | List products paginated, cached 15min |
| `POST`   | `/api/tenant/products`           | ADMIN, MANAGER | Create product                        |
| `GET`    | `/api/tenant/products/{id}`      | ALL_TENANT     | Get product detail                    |
| `PUT`    | `/api/tenant/products/{id}`      | ADMIN, MANAGER | Update product                        |
| `DELETE` | `/api/tenant/products/{id}`      | ADMIN          | Soft delete product                   |
| `GET`    | `/api/tenant/products/low-stock` | ADMIN, MANAGER | Items below reorder_level             |
| `GET`    | `/api/tenant/products/expiring`  | ADMIN, MANAGER | Pharmacy: expiry within 30 days       |
| `GET`    | `/api/tenant/products/search?q=` | ALL_TENANT     | Search by name/SKU/barcode            |

**Create Product Request:**

```json
{
  "name": "Paracetamol 500mg",
  "sku": "MED-001",
  "barcode": "8901234567890",
  "category_id": 5,
  "unit": "strip",
  "purchase_price": 45.00,
  "sale_price": 65.00,
  "reorder_level": 20,
  "pharmacy_details": {
    "batch_number": "BATCH-2024-A1",
    "expiry_date": "2026-06-30",
    "hsn_code": "30049099",
    "manufacturer": "ABC Pharma"
  }
}
```

### 5.5 Stock Endpoints

| Method   | Endpoint                        | Roles                 | Description                           |
| -------- | ------------------------------- | --------------------- | ------------------------------------- |
| `POST` | `/api/tenant/stock/in`        | ADMIN, MANAGER, STAFF | Record stock received                 |
| `POST` | `/api/tenant/stock/out`       | ADMIN, MANAGER, STAFF | Record stock issued                   |
| `POST` | `/api/tenant/stock/adjust`    | ADMIN, MANAGER        | Manual adjustment with reason         |
| `GET`  | `/api/tenant/stock/movements` | ADMIN, MANAGER        | Full stock movement log               |
| `POST` | `/api/tenant/stock/transfer`  | ADMIN, MANAGER        | Warehouse: transfer between locations |

### 5.6 Order Endpoints

| Method    | Endpoint                           | Roles                 | Description                            |
| --------- | ---------------------------------- | --------------------- | -------------------------------------- |
| `GET`   | `/api/tenant/orders`             | ALL_TENANT            | List all orders (paginated)            |
| `POST`  | `/api/tenant/orders/purchase`    | ADMIN, MANAGER        | Create purchase order, increases stock |
| `POST`  | `/api/tenant/orders/sale`        | ADMIN, MANAGER, STAFF | Create sale order, decrements stock    |
| `GET`   | `/api/tenant/orders/{id}`        | ALL_TENANT            | Get order with all items               |
| `PATCH` | `/api/tenant/orders/{id}/status` | ADMIN, MANAGER        | Update order status                    |
| `POST`  | `/api/tenant/invoices`           | ADMIN, MANAGER, STAFF | Generate invoice from an order         |
| `GET`   | `/api/tenant/invoices/{id}/pdf`  | ALL_TENANT            | Download invoice as PDF                |
| `GET`   | `/api/tenant/invoices`           | ALL_TENANT            | List invoices                          |

### 5.7 Report Endpoints

| Method  | Endpoint                                      | Roles          | Description                     |
| ------- | --------------------------------------------- | -------------- | ------------------------------- |
| `GET` | `/api/tenant/reports/dashboard`             | ADMIN, MANAGER | KPIs: sales today, stock alerts |
| `GET` | `/api/tenant/reports/stock`                 | ADMIN, MANAGER | Stock status report             |
| `GET` | `/api/tenant/reports/sales?from=&to=`       | ADMIN, MANAGER | Sales analytics with date range |
| `GET` | `/api/tenant/reports/profit-loss?from=&to=` | ADMIN          | P&L report                      |
| `GET` | `/api/tenant/audit?page=&size=`             | ADMIN          | Full audit log                  |

**Dashboard Response:**

```json
{
  "total_products": 245,
  "low_stock_count": 12,
  "out_of_stock_count": 3,
  "today_sales_amount": 18500.00,
  "today_sales_count": 42,
  "today_purchases_amount": 55000.00,
  "expiring_soon_count": 8,
  "cached_at": "2024-03-15T08:00:00Z"
}
```

### 5.8 Standard Error Response

```json
{
  "status": 403,
  "error": "FORBIDDEN",
  "message": "Insufficient role: MANAGER required",
  "path": "/api/tenant/products",
  "timestamp": "2024-03-15T10:30:00Z"
}
```

---

## 6. Docker & Infrastructure

### 6.1 docker-compose.yml

```yaml
version: '3.9'

services:

  postgres:
    image: postgres:16-alpine
    container_name: ims_postgres
    environment:
      POSTGRES_DB: ims_db
      POSTGRES_USER: ims_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./db/init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ims_user -d ims_db"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  valkey:
    image: valkey/valkey:7.2-alpine
    container_name: ims_valkey
    command: valkey-server --requirepass ${REDIS_PASSWORD} --save 60 1 --loglevel warning
    volumes:
      - valkey_data:/data
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "valkey-cli", "--pass", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 3
    restart: unless-stopped

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: ims_backend
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/ims_db
      SPRING_DATASOURCE_USERNAME: ims_user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_REDIS_HOST: valkey
      SPRING_REDIS_PORT: 6379
      SPRING_REDIS_PASSWORD: ${REDIS_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      APP_ENV: production
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      valkey:
        condition: service_healthy
    restart: unless-stopped

  nginx:
    image: nginx:alpine
    container_name: ims_nginx
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - backend
    restart: unless-stopped

volumes:
  postgres_data:
  valkey_data:
```

### 6.2 Backend Dockerfile

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S ims && adduser -S ims -G ims
COPY --from=builder /app/target/*.jar app.jar
USER ims
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "-Xmx512m", "-Xms256m", "app.jar"]
```

### 6.3 .env Template

```env
DB_PASSWORD=your_strong_db_password_here
REDIS_PASSWORD=your_strong_redis_password_here
JWT_SECRET=your_256bit_hex_secret_here
JWT_EXPIRY_SECONDS=86400
APP_ENV=production
APP_PORT=8080
```

### 6.4 application.yml

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
  data:
    redis:
      host: ${SPRING_REDIS_HOST:localhost}
      port: ${SPRING_REDIS_PORT:6379}
      password: ${SPRING_REDIS_PASSWORD}
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 10
          max-idle: 5

app:
  jwt:
    secret: ${JWT_SECRET}
    expiry-seconds: ${JWT_EXPIRY_SECONDS:86400}
    refresh-expiry-seconds: 604800
  cache:
    product-ttl-minutes: 15
    stock-ttl-minutes: 5
    report-ttl-minutes: 30
    tenant-ttl-hours: 1
  rate-limit:
    public-rpm: 100
    authenticated-rpm: 500

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

---

## 7. Project Structure

```
ims-backend/
+-- src/
|   +-- main/
|   |   +-- java/com/ims/
|   |   |   +-- ImsApplication.java
|   |   |   +-- config/
|   |   |   |   +-- SecurityConfig.java
|   |   |   |   +-- RedisConfig.java
|   |   |   |   +-- SwaggerConfig.java
|   |   |   +-- platform/
|   |   |   |   +-- controller/TenantController.java
|   |   |   |   +-- service/TenantService.java
|   |   |   |   +-- repository/TenantRepository.java
|   |   |   +-- tenant/
|   |   |   |   +-- controller/
|   |   |   |   |   +-- ProductController.java
|   |   |   |   |   +-- StockController.java
|   |   |   |   |   +-- OrderController.java
|   |   |   |   |   +-- InvoiceController.java
|   |   |   |   |   +-- ReportController.java
|   |   |   |   |   +-- UserController.java
|   |   |   |   +-- service/
|   |   |   |   |   +-- ProductService.java
|   |   |   |   |   +-- StockService.java
|   |   |   |   |   +-- OrderService.java
|   |   |   |   |   +-- InvoiceService.java
|   |   |   |   |   +-- ReportService.java
|   |   |   |   +-- repository/
|   |   |   |   |   +-- ProductRepository.java
|   |   |   |   |   +-- OrderRepository.java
|   |   |   |   |   +-- StockMovementRepository.java
|   |   |   |   |   +-- InvoiceRepository.java
|   |   |   |   +-- domain/
|   |   |   |       +-- pharmacy/
|   |   |   |       |   +-- PharmacyProduct.java
|   |   |   |       |   +-- PharmacyProductRepository.java
|   |   |   |       |   +-- ExpiryAlertService.java
|   |   |   |       +-- warehouse/
|   |   |   |       |   +-- WarehouseProduct.java
|   |   |   |       |   +-- TransferOrderService.java
|   |   |   |       +-- supermarket/
|   |   |   |           +-- BarcodeService.java
|   |   |   +-- shared/
|   |   |   |   +-- auth/
|   |   |   |   |   +-- JwtUtil.java
|   |   |   |   |   +-- JwtFilter.java
|   |   |   |   |   +-- TenantContext.java
|   |   |   |   +-- rbac/
|   |   |   |   |   +-- RequiresRole.java
|   |   |   |   |   +-- RbacAspect.java
|   |   |   |   +-- cache/CacheService.java
|   |   |   |   +-- ratelimit/RateLimitFilter.java
|   |   |   |   +-- audit/AuditLogService.java
|   |   |   |   +-- exception/GlobalExceptionHandler.java
|   |   |   +-- model/
|   |   |   |   +-- Tenant.java
|   |   |   |   +-- User.java
|   |   |   |   +-- Product.java
|   |   |   |   +-- Order.java
|   |   |   |   +-- OrderItem.java
|   |   |   |   +-- Invoice.java
|   |   |   |   +-- StockMovement.java
|   |   |   +-- dto/
|   |   |       +-- request/
|   |   |       +-- response/
|   |   +-- resources/
|   |       +-- application.yml
|   |       +-- db/migration/
|   |           +-- V1__create_core_tables.sql
|   |           +-- V2__pharmacy_extension.sql
|   |           +-- V3__warehouse_extension.sql
|   +-- test/java/com/ims/
|       +-- auth/JwtAuthTest.java
|       +-- tenant/TenantIsolationTest.java
|       +-- stock/StockTransactionTest.java
+-- Dockerfile
+-- pom.xml
+-- .env
```

---

## 8. One-Week Development Plan (Agent-Driven)

> How to use: Copy each AGENT TASK block directly into your coding agent. Each task is self-contained with exact inputs, outputs, and acceptance criteria.

---

### DAY 1 — Foundation & Scaffolding

**Goal:** Running Docker stack + JWT auth working

---

#### AGENT TASK 1 — Project Scaffolding

```
Generate a Java 21 + Spring Boot 3.x Maven project:

Project: com.ims / ims-backend

Dependencies (pom.xml):
  - spring-boot-starter-web
  - spring-boot-starter-data-jpa
  - spring-boot-starter-security
  - spring-boot-starter-data-redis
  - spring-boot-starter-cache
  - spring-boot-starter-validation
  - spring-boot-starter-actuator
  - spring-boot-starter-aop
  - io.jsonwebtoken:jjwt-api:0.12.3
  - io.jsonwebtoken:jjwt-impl:0.12.3
  - io.jsonwebtoken:jjwt-jackson:0.12.3
  - org.flywaydb:flyway-core
  - org.postgresql:postgresql
  - org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0
  - org.projectlombok:lombok
  - com.itextpdf:itext7-core:7.2.5

Create the full package structure as defined in Section 7.
Create application.yml from Section 6.4.
Create .env from Section 6.3.

Acceptance: mvn compile passes with zero errors.
```

---

#### AGENT TASK 2 — Docker Stack

```
Create all Docker files from Section 6:
  - docker-compose.yml (postgres + valkey + backend + nginx)
  - backend/Dockerfile (multi-stage Maven build)
  - nginx/nginx.conf
  - .env template

Run: docker compose up -d postgres valkey

Verify:
  docker compose ps -> both healthy
  psql -h localhost -U ims_user -d ims_db -> connects

Acceptance: postgres and valkey containers show healthy status.
```

---

#### AGENT TASK 3 — Database Schema (Flyway)

```
Create Flyway migration files in src/main/resources/db/migration/:

V1__create_core_tables.sql — All tables from Section 4.1 with all indexes
V2__pharmacy_extension.sql — pharmacy_products table from Section 4.2
V3__warehouse_extension.sql — warehouse_products and transfer_orders from Section 4.3

Create JPA Entity classes in com.ims.model for all tables.
Use @Data, @Entity, @Table, @Column, @ManyToOne, @OneToOne with proper mappings.

Run: mvn spring-boot:run against running postgres

Verify: All 10+ tables created in psql via \dt

Acceptance: Flyway V1, V2, V3 all applied successfully. No migration errors.
```

---

#### AGENT TASK 4 — JWT Authentication

```
Implement JWT authentication in com.ims.shared.auth:

1. JwtUtil.java:
   - generateToken(userId, tenantId, role, scope, businessType) -> String
   - validateToken(token) -> boolean
   - extractTenantId(token) -> Long
   - extractRole(token) -> String
   - extractUserId(token) -> Long
   Use io.jsonwebtoken (jjwt 0.12.3). Read secret from app.jwt.secret.

2. TenantContext.java:
   ThreadLocal<Long> with set(Long), get(), clear() static methods.

3. JwtFilter.java (extends OncePerRequestFilter):
   - Extract Bearer token from Authorization header
   - Validate with JwtUtil
   - Set TenantContext and SecurityContextHolder
   - Always clear TenantContext in finally block

4. SecurityConfig.java:
   - Permit /api/auth/** publicly
   - Require auth for all /api/**
   - Add JwtFilter before UsernamePasswordAuthenticationFilter
   - Disable CSRF, enable CORS

5. AuthController + AuthService:
   POST /api/auth/login:
     - Find user by email, verify BCrypt password
     - Return: { access_token, refresh_token, expires_in, tenant_id, role, business_type }
   POST /api/auth/logout:
     - Store token hash in Redis: jwt:blacklist:{hash} with 24h TTL

Seed data: Insert 1 ROOT user (root@ims.com / root123) via ApplicationRunner.

Acceptance:
  POST /api/auth/login with root@ims.com -> returns JWT
  GET /api/tenant/products without token -> 401
```

---

### DAY 2 — Tenant & User Management

**Goal:** Platform admin can manage tenants. Tenant admin can manage users.

---

#### AGENT TASK 5 — Tenant CRUD (Platform Admin)

```
Build platform-level tenant management:

TenantController -> /api/platform/tenants
TenantService
TenantRepository extends JpaRepository<Tenant, Long>

Endpoints:
  GET    /api/platform/tenants      -> Page<TenantResponse> (ROOT, PLATFORM_ADMIN)
  POST   /api/platform/tenants      -> TenantResponse       (ROOT, PLATFORM_ADMIN)
  GET    /api/platform/tenants/{id} -> TenantResponse
  PATCH  /api/platform/tenants/{id} -> update plan/status   (ROOT only)

CreateTenantRequest: { name, domain, business_type, plan }
TenantResponse: { id, name, domain, business_type, plan, status, created_at }

Role check: scope must be PLATFORM in JWT. Throw 403 for tenant-scoped users.

Seed: Create 1 sample tenant "ABC Pharmacy" with business_type=PHARMACY via ApplicationRunner.

Acceptance: GET /api/platform/tenants returns list. POST creates tenant. MANAGER user gets 403.
```

---

#### AGENT TASK 6 — User Management (Tenant Scoped)

```
Build tenant-level user management:

UserController -> /api/tenant/users
UserService
UserRepository

Endpoints:
  POST   /api/tenant/users             -> Create user (ADMIN only)
  GET    /api/tenant/users             -> List users (ADMIN, MANAGER)
  GET    /api/tenant/users/{id}        -> Get user
  PATCH  /api/tenant/users/{id}/role   -> Update role (ADMIN only)
  DELETE /api/tenant/users/{id}        -> Soft delete (ADMIN only)

CRITICAL: ALL queries filter by TenantContext.get(). Never expose cross-tenant users.

CreateUserRequest: { name, email, password, role }
Validate: role must be ADMIN, MANAGER, or STAFF only (no platform roles).
Hash password with BCryptPasswordEncoder.

Acceptance: Admin creates user. Tenant A's admin cannot see Tenant B's users.
```

---

#### AGENT TASK 7 — Redis Caching Layer

```
Implement Redis caching:

1. RedisConfig.java (@Configuration @EnableCaching):
   Configure RedisCacheManager with GenericJackson2JsonRedisSerializer.
   Define TTLs per cache name:
     products -> 15 minutes
     stock    -> 5 minutes
     reports  -> 30 minutes
     tenant   -> 1 hour

2. CacheService.java:
   Generic get(key), set(key, value, ttl), evict(key), evictByPattern(pattern)

3. Add @Cacheable to TenantService.getTenant(id) with key "tenant:{id}"

4. Verify: Call GET /api/platform/tenants/{id} twice.
   Check Redis: valkey-cli KEYS "*tenant*" shows cached key.

Acceptance: Cache hit logged on second request. TTLs set correctly.
```

---

#### AGENT TASK 8 — RBAC AOP Enforcement

```
Implement AOP-based RBAC:

1. RequiresRole.java annotation:
   @Target(ElementType.METHOD)
   @Retention(RetentionPolicy.RUNTIME)
   public @interface RequiresRole { String[] value(); }

2. RbacAspect.java (@Aspect @Component):
   @Around("@annotation(requiresRole)")
   Extract role from SecurityContextHolder.
   If current role not in requiresRole.value() -> throw AccessDeniedException.

3. Apply @RequiresRole to ALL controller methods using the matrix from Section 3.2.

4. GlobalExceptionHandler.java (@RestControllerAdvice):
   AccessDeniedException        -> 403 JSON
   EntityNotFoundException      -> 404 JSON
   InsufficientStockException   -> 422 JSON
   MethodArgumentNotValidException -> 400 JSON with field errors
   Exception                    -> 500 JSON (no stack trace exposed)

   All responses: { status, error, message, path, timestamp }

Acceptance: MANAGER role gets 403 on ADMIN-only endpoint.
```

---

### DAY 3 — Products & Stock Core

**Goal:** Full product lifecycle + stock tracking live.

---

#### AGENT TASK 9 — Product CRUD with Caching

```
Build complete Product management:

ProductController -> /api/tenant/products
ProductService
ProductRepository

Implement all 8 endpoints from Section 5.4.

EVERY repository call must use: findByIdAndTenantId(id, TenantContext.get())

Caching:
  @Cacheable(value = "products", key = "#tenantId + ':list'")
  on ProductService.getProducts(tenantId, pageable)

  @CacheEvict(value = "products", key = "#tenantId + ':list'")
  on create, update, delete

Pagination: All lists accept ?page=0&size=20&sort=name,asc

Low stock query: SELECT * FROM products WHERE tenant_id=? AND stock <= reorder_level

Validation on CreateProductRequest:
  @NotBlank name, @NotNull @Positive sale_price
  If business_type==PHARMACY, pharmacy_details must be present

Acceptance: Full CRUD works. Cache evicted on update. Low-stock returns correct items.
```

---

#### AGENT TASK 10 — Pharmacy Domain Extension

```
Implement pharmacy-specific features:

1. PharmacyProduct.java (@Entity, @OneToOne with Product via @MapsId)

2. PharmacyProductRepository:
   List<PharmacyProduct> findByExpiryDateBefore(LocalDate date);

3. When business_type==PHARMACY and pharmacy_details present in request:
   Save PharmacyProduct in same @Transactional as Product.

4. Expiry endpoint:
   GET /api/tenant/products/expiring?days=30
   Returns products where pharmacy_products.expiry_date < NOW() + days
   Returns 400 if tenant is not PHARMACY type.

5. ExpiryAlertService.java:
   @Scheduled(cron = "0 0 8 * * *")
   Query all expiring products across all PHARMACY tenants.
   Log: "EXPIRY ALERT: Tenant {id} Product {name} expires {date}"
   (Email in Phase 2)

Acceptance: Pharmacy product saves expiry data. /products/expiring returns correct items.
```

---

#### AGENT TASK 11 — Stock Management

```
Build stock tracking:

StockController -> /api/tenant/stock
StockService
StockMovementRepository

StockService.stockIn(tenantId, productId, qty, notes, userId):
  @Transactional
  1. Find product WHERE id=? AND tenant_id=?
  2. product.stock += qty
  3. Save product
  4. Save StockMovement { type=IN, qty, previousStock, newStock }
  5. @CacheEvict stock key for this product

StockService.stockOut(tenantId, productId, qty, notes, userId):
  @Transactional
  1. Find product with @Lock(PESSIMISTIC_WRITE)
  2. if stock < qty -> throw InsufficientStockException
  3. product.stock -= qty
  4. Save product + StockMovement { type=OUT }
  5. Evict stock cache

GET /api/tenant/stock/movements:
  Paginated, most recent first.
  Join products for product_name, join users for created_by_name.

Acceptance: Concurrent stockOut never goes negative. Movements log queryable.
```

---

#### AGENT TASK 12 — Warehouse Domain Extension

```
Implement warehouse-specific features:

1. WarehouseProduct.java (@Entity linked to products via @OneToOne @MapsId)

2. When business_type==WAREHOUSE, CreateProductRequest can include:
   { storage_location, zone, rack, bin }
   Save to warehouse_products in same @Transactional as product save.

3. Transfer order:
   POST /api/tenant/stock/transfer
   Request: { product_id, from_location, to_location, quantity, notes }

   @Transactional:
   - Create transfer_orders record (status=COMPLETED for MVP)
   - Log StockMovement { type=TRANSFER, notes="from X to Y" }
   - Update warehouse_products.storage_location to to_location

4. GET /api/tenant/stock/by-location?location=
   Returns all products at that location.

Acceptance: Warehouse product saves location. Transfer updates location and logs movement.
```

---

### DAY 4 — Orders & Billing

**Goal:** Full order lifecycle with invoice PDF generation.

---

#### AGENT TASK 13 — Purchase Order Flow

```
Build purchase order system:

POST /api/tenant/orders/purchase
OrderService.createPurchaseOrder(tenantId, request, userId)

CreatePurchaseOrderRequest:
{
  supplier_id: Long,
  items: [{ product_id, quantity, unit_price, discount }],
  notes: String
}

@Transactional:
1. Validate supplier belongs to tenant
2. Calculate total = sum(qty * unit_price * (1 - discount))
3. Save Order { type=PURCHASE, status=RECEIVED, tenant_id, supplier_id, total }
4. Save all OrderItems
5. For each item: stockService.stockIn(productId, qty)

GET /api/tenant/orders?type=PURCHASE -> Paginated
GET /api/tenant/orders/{id} -> Full order with items + product names

Acceptance: Purchase order created -> product stock increases -> movement logged.
```

---

#### AGENT TASK 14 — Sales Order Flow

```
Build sales order system:

POST /api/tenant/orders/sale
OrderService.createSalesOrder(tenantId, request, userId)

CreateSalesOrderRequest:
{
  customer_id: Long (optional for walk-in),
  items: [{ product_id, quantity, unit_price, discount }],
  notes: String
}

@Transactional:
1. Validate all products belong to tenant
2. Check ALL items have sufficient stock BEFORE processing any
   If any product.stock < requested_qty -> throw InsufficientStockException with product details
3. Calculate totals
4. Save Order { type=SALE, status=COMPLETED }
5. Save OrderItems
6. For each item: stockService.stockOut(productId, qty)
7. Auto-call InvoiceService.createFromOrder(order) -> creates invoice automatically
8. Return: { order_id, invoice_id, invoice_number, total }

Acceptance: Sale created -> stock decremented atomically -> invoice auto-generated.
```

---

#### AGENT TASK 15 — Invoice PDF Generation

```
Add iText 7 for PDF:
Dependency: com.itextpdf:itext7-core:7.2.5

InvoiceService.java:
  createFromOrder(Order order) -> Invoice:
    invoice_number = "INV-{tenant_id}-{YYYYMMDD}-{sequence}"
    Save Invoice entity, status=UNPAID

  generatePdf(Long invoiceId, Long tenantId) -> byte[]:
    Fetch invoice + order + order_items + products + tenant
    Build PDF with iText:
      Header: Tenant name, address, invoice number, date
      Table: Item Name | Qty | Unit Price | Discount | Tax | Total
      Footer: Subtotal | Tax Total | Grand Total
    Return byte[]

InvoiceController:
  GET /api/tenant/invoices/{id}/pdf:
    byte[] pdf = invoiceService.generatePdf(id, tenantId);
    return ResponseEntity.ok()
      .header("Content-Disposition", "attachment; filename=invoice-{id}.pdf")
      .contentType(MediaType.APPLICATION_PDF)
      .body(pdf);

  GET /api/tenant/invoices -> Page<InvoiceResponse>

Acceptance: PDF downloads with correct items, quantities, and totals.
```

---

#### AGENT TASK 16 — Supplier & Customer Management

```
Build supplier and customer CRUD (both fully tenant-scoped):

SupplierController -> /api/tenant/suppliers
CustomerController -> /api/tenant/customers

Both follow same pattern:
  GET    /api/tenant/{entity}       -> Page<Response> (ADMIN, MANAGER)
  POST   /api/tenant/{entity}       -> Response       (ADMIN, MANAGER)
  GET    /api/tenant/{entity}/{id}  -> Response
  PUT    /api/tenant/{entity}/{id}  -> Response       (ADMIN, MANAGER)
  DELETE /api/tenant/{entity}/{id}  -> 204            (ADMIN only)

ALL queries: WHERE tenant_id = TenantContext.get()

SupplierRequest: { name, phone, email, address, gstin }
CustomerRequest: { name, phone, email, address }

Validation: @NotBlank name, @Email email (optional)

Acceptance: Suppliers/Customers CRUD work. Cross-tenant access returns empty list.
```

---

### DAY 5 — Reports & Analytics

**Goal:** Dashboard, stock reports, sales analytics — all cached.

---

#### AGENT TASK 17 — Dashboard KPI Endpoint

```
GET /api/tenant/reports/dashboard
ReportService.getDashboard(tenantId)

@Cacheable(value = "reports", key = "#tenantId + ':dashboard'") TTL 30min

Run these JPQL/native queries:

total_products:      COUNT(*) FROM products WHERE tenant_id=? AND is_active=true
low_stock_count:     COUNT(*) FROM products WHERE tenant_id=? AND stock <= reorder_level
out_of_stock_count:  COUNT(*) FROM products WHERE tenant_id=? AND stock = 0
today_sales_amount:  SUM(total_amount) FROM orders WHERE tenant_id=? AND type='SALE' AND DATE(created_at)=CURRENT_DATE
today_sales_count:   COUNT(*) FROM orders WHERE tenant_id=? AND type='SALE' AND DATE(created_at)=CURRENT_DATE
today_purchases:     SUM(total_amount) FROM orders WHERE tenant_id=? AND type='PURCHASE' AND DATE(created_at)=CURRENT_DATE
expiring_soon:       COUNT(*) pharmacy_products WHERE product.tenant_id=? AND expiry_date < NOW()+30days

Response: DashboardResponse with all above fields + cached_at timestamp
Note: expiring_soon only populated if business_type == PHARMACY

Acceptance: All KPIs correct. Second call within 30min served from Redis (log shows cache hit).
```

---

#### AGENT TASK 18 — Stock Report

```
GET /api/tenant/reports/stock?filter=all|low|expiring&page=0&size=50
@Cacheable key "#tenantId + ':stock-report'" TTL 5min

Response per item:
{
  product_id, product_name, sku, category_name,
  current_stock, reorder_level, unit,
  status: "OK" | "LOW" | "OUT_OF_STOCK" | "EXPIRING",
  expiry_date (pharmacy only)
}

Status logic:
  stock == 0 -> OUT_OF_STOCK
  stock <= reorder_level -> LOW
  expiry_date < NOW()+30 -> EXPIRING
  else -> OK

Filters:
  all      -> all active products
  low      -> stock <= reorder_level
  expiring -> pharmacy only: expiry within 30 days

Order: OUT_OF_STOCK first, then LOW, then EXPIRING, then OK

Acceptance: Returns paginated list with correct status labels ordered by urgency.
```

---

#### AGENT TASK 19 — Sales Analytics

```
GET /api/tenant/reports/sales?from=2024-01-01&to=2024-03-31
@Cacheable key "#tenantId + ':sales:' + #from + ':' + #to" TTL 30min

Response:
{
  period: { from, to },
  total_revenue: 245000.00,
  total_orders: 312,
  average_order_value: 785.00,
  daily_breakdown: [
    { date: "2024-01-01", revenue: 8500.00, orders: 12 }
  ],
  top_products: [
    { product_id, name, quantity_sold, revenue }
  ]
}

Daily breakdown native query:
  SELECT DATE(created_at) as date, SUM(total_amount) as revenue, COUNT(*) as orders
  FROM orders
  WHERE tenant_id=? AND type='SALE' AND created_at BETWEEN ? AND ?
  GROUP BY DATE(created_at) ORDER BY date

Top 10 products query:
  SELECT oi.product_id, p.name, SUM(oi.quantity) as qty_sold, SUM(oi.total) as revenue
  FROM order_items oi
  JOIN orders o ON oi.order_id=o.id
  JOIN products p ON oi.product_id=p.id
  WHERE o.tenant_id=? AND o.type='SALE' AND o.created_at BETWEEN ? AND ?
  GROUP BY oi.product_id, p.name
  ORDER BY revenue DESC LIMIT 10

Acceptance: Revenue totals correct. Daily breakdown accurate. Top 10 sorted by revenue.
```

---

#### AGENT TASK 20 — Audit Log

```
GET /api/tenant/audit?page=0&size=20&product_id=&from=&to=
Role: ADMIN only

ReportService.getAuditLog(tenantId, productId, from, to, pageable)

Query:
  SELECT sm.*, p.name as product_name, u.name as user_name
  FROM stock_movements sm
  JOIN products p ON sm.product_id = p.id
  JOIN users u ON sm.created_by = u.id
  WHERE sm.tenant_id = ?
  [AND sm.product_id = ? if provided]
  [AND sm.created_at BETWEEN ? AND ? if provided]
  ORDER BY sm.created_at DESC

Response per item:
{
  id, product_id, product_name,
  movement_type, quantity,
  previous_stock, new_stock,
  reference_id, reference_type,
  notes, created_by_name, created_at
}

Acceptance: Full movement history queryable by product and date range.
```

---

### DAY 6 — Testing, Security Hardening & Rate Limiting

**Goal:** Tenant isolation verified. Rate limiting live. Swagger complete.

---

#### AGENT TASK 21 — Integration Tests (Testcontainers)

```
Add test dependencies:
  org.testcontainers:postgresql
  org.testcontainers:junit-jupiter
  spring-boot-starter-test

Write TenantIsolationTest.java:
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers

Use PostgreSQLContainer and GenericContainer for valkey.

Write these 4 test cases:

TEST 1: testTenantACannotSeeTenantBProducts()
  - Create tenant A and product in tenant A
  - Login as tenant B user
  - GET /api/tenant/products -> must NOT return tenant A's product
  - Assert: response products list is empty or only contains tenant B products

TEST 2: testStockOutDoesNotGoBelowZero()
  - Product with stock=5
  - Request stockOut qty=10 -> expect HTTP 422
  - Verify product.stock still equals 5 in DB

TEST 3: testJwtBlacklistOnLogout()
  - Login -> get token
  - POST /api/auth/logout with that token
  - Use same token for GET request -> expect HTTP 401

TEST 4: testRbacStaffCannotCreateProduct()
  - Login as STAFF user
  - POST /api/tenant/products -> expect HTTP 403

Acceptance: All 4 tests pass green. No test pollutes another (use @Transactional or cleanup).
```

---

#### AGENT TASK 22 — Redis Rate Limiting

```
Implement RateLimitFilter.java (extends OncePerRequestFilter):

Algorithm: Sliding window counter with Redis INCR + EXPIRE

Logic:
  String ip = request.getRemoteAddr();
  boolean authenticated = request.getHeader("Authorization") != null;
  int limit = authenticated ? 500 : 100;
  String key = "rate:" + ip + ":" + (System.currentTimeMillis() / 60000);
  Long count = redisTemplate.opsForValue().increment(key);
  if (count == 1) redisTemplate.expire(key, 60, TimeUnit.SECONDS);
  if (count > limit) {
    response.setStatus(429);
    response.getWriter().write("{\"error\":\"Too Many Requests\",\"retry_after\":60}");
    return;
  }
  chain.doFilter(request, response);

Add response headers to ALL responses:
  X-RateLimit-Limit: {limit}
  X-RateLimit-Remaining: {max(0, limit-count)}
  X-RateLimit-Reset: {epoch_seconds of next window}

Register filter in SecurityConfig before all other filters.

Acceptance: After 100 req/min from same IP, returns 429. Headers visible on every response.
```

---

#### AGENT TASK 23 — Input Validation & Error Handling

```
Harden all inputs:

1. Add @Valid to ALL controller method @RequestBody parameters.

2. All DTOs must have constraints:
   @NotBlank on all string fields that are required
   @NotNull @Positive on price and quantity fields
   @Email on email fields
   @Size(max=255) on varchar fields

3. GlobalExceptionHandler.java (@RestControllerAdvice):

   @ExceptionHandler(MethodArgumentNotValidException.class)
   -> HTTP 400
   -> Response: { status:400, error:"VALIDATION_FAILED", fields:{fieldName:errorMessage}, timestamp }

   @ExceptionHandler(EntityNotFoundException.class)
   -> HTTP 404

   @ExceptionHandler(AccessDeniedException.class)
   -> HTTP 403

   @ExceptionHandler(InsufficientStockException.class)
   -> HTTP 422
   -> Response includes: { available_stock, requested_qty }

   @ExceptionHandler(DataIntegrityViolationException.class)
   -> HTTP 409

   @ExceptionHandler(Exception.class)
   -> HTTP 500
   -> NEVER expose stack trace in response body

4. Set spring.jpa.show-sql=false in production profile.

Acceptance: Bad request returns field-level errors. No stack traces in any response.
```

---

#### AGENT TASK 24 — Swagger / OpenAPI Documentation

```
Complete API documentation:

1. SwaggerConfig.java:
   @Bean OpenAPI:
     title: "IMS — Inventory Management System API"
     version: "1.0.0"
     description: "Multi-tenant SaaS Inventory Platform"
     SecurityScheme: bearerAuth (HTTP Bearer JWT)
     Server: http://localhost:8080

2. Annotate ALL controllers:
   @Tag(name="Products") on class
   @Operation(summary="...", description="...") on each method
   @ApiResponse(responseCode="200") and @ApiResponse(responseCode="403") on each method
   @SecurityRequirement(name="bearerAuth") on all protected endpoints

3. Annotate ALL request/response DTOs:
   @Schema(description="...", example="...") on each field

4. Add to application.yml:
   springdoc:
     api-docs:
       path: /api-docs
     swagger-ui:
       path: /swagger-ui.html

Verify:
  GET http://localhost:8080/swagger-ui.html -> All endpoints visible
  Use Authorize button with JWT -> Execute protected endpoint from Swagger UI

Acceptance: All ~30 endpoints documented. Auth works from Swagger UI.
```

---

### DAY 7 — Integration, Cleanup & Delivery

**Goal:** E2E tested, production Docker, v1.0.0 tagged.

---

#### AGENT TASK 25 — End-to-End Smoke Test (Postman Collection)

```
Create Postman collection: IMS_E2E_Tests.json
Collection variables: baseUrl, accessToken, tenantAdminToken, productId, orderId, invoiceId

Test sequence (must run in order):

STEP 1:  POST {{baseUrl}}/api/auth/login (root@ims.com) -> save to accessToken
STEP 2:  POST {{baseUrl}}/api/platform/tenants -> create "Test Pharmacy" -> save tenant_id
STEP 3:  POST {{baseUrl}}/api/auth/login (as tenant admin) -> save to tenantAdminToken
STEP 4:  POST {{baseUrl}}/api/tenant/products -> create product -> save productId
STEP 5:  POST {{baseUrl}}/api/tenant/stock/in { product_id, quantity: 100 }
STEP 6:  GET  {{baseUrl}}/api/tenant/products/{{productId}} -> assert stock == 100
STEP 7:  POST {{baseUrl}}/api/tenant/orders/sale { items: [{product_id, quantity: 10}] } -> save orderId, invoiceId
STEP 8:  GET  {{baseUrl}}/api/tenant/products/{{productId}} -> assert stock == 90
STEP 9:  GET  {{baseUrl}}/api/tenant/invoices/{{invoiceId}}/pdf -> assert Content-Type: application/pdf
STEP 10: GET  {{baseUrl}}/api/tenant/reports/dashboard -> assert today_sales_count >= 1
STEP 11: POST {{baseUrl}}/api/auth/logout (with tenantAdminToken)
STEP 12: GET  {{baseUrl}}/api/tenant/products (with old tenantAdminToken) -> assert 401

Add Postman test scripts to assert HTTP status at each step.

Acceptance: All 12 steps pass. Export collection as IMS_E2E_Tests.json.
```

---

#### AGENT TASK 26 — Production Docker Hardening

```
Production hardening:

1. Dockerfile:
   Verify HEALTHCHECK is present (wget actuator/health)
   Add JVM flags: -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
   Confirm non-root user ims is used

2. docker-compose.yml additions:
   Resource limits:
     backend: mem_limit: 768m, cpus: '1.0'
     postgres: mem_limit: 512m
     valkey: mem_limit: 256m
   Logging:
     all services: driver: json-file, options: max-size: 100m, max-file: "3"

3. nginx.conf additions:
   add_header X-Content-Type-Options nosniff;
   add_header X-Frame-Options DENY;
   add_header X-XSS-Protection "1; mode=block";
   proxy_read_timeout 60s; (for PDF generation)

4. Create docker-compose.prod.yml override:
   Remove external port mappings for postgres (5432) and valkey (6379)
   Set restart: always for all services

5. Smoke test:
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
   curl http://localhost/actuator/health -> {"status":"UP"}

Acceptance: Stack starts clean. Health returns UP. DB and Redis not exposed externally.
```

---

#### AGENT TASK 27 — README & Docs

```
Write README.md with these sections:

1. Overview (2 sentences max)
2. Architecture diagram (ASCII from Section 2.1)
3. Prerequisites: Docker Desktop, Java 21, Maven 3.9
4. Quick Start:
   git clone ...
   cp .env.example .env
   # Edit .env with your passwords
   docker compose up -d
   curl http://localhost:8080/actuator/health
5. Environment Variables table: variable | description | example
6. API Quick Reference table: method | endpoint | description
7. Swagger UI: http://localhost:8080/swagger-ui.html
8. Default Credentials: root@ims.com / root123 (CHANGE THIS IN PRODUCTION)
9. Running Tests: mvn test
10. Useful Docker Commands (from Section 11 Quick Reference)

Write CHANGELOG.md:
## [1.0.0] - {today's date}
### Added
- Multi-tenant architecture with JWT-based tenant isolation
- Product, Stock, Order, Invoice management
- Pharmacy domain extension (expiry tracking, batch numbers)
- Warehouse domain extension (location mapping, transfer orders)
- Redis/Valkey caching (products 15min, stock 5min, reports 30min)
- Rate limiting via Redis sliding window
- Swagger UI documentation
- Docker Compose full stack (Postgres + Valkey + Backend + Nginx)
- Testcontainers integration tests

Acceptance: README readable by new developer in under 5 minutes.
```

---

#### AGENT TASK 28 — Final Cleanup & Release

```
Final quality pass before v1.0.0:

1. Code audit:
   - Grep codebase for "findAll()" without tenant_id filter -> fix any found
   - Grep for "System.out.println" -> replace with log.info/warn/error
   - Grep for "e.printStackTrace()" -> replace with log.error
   - Verify all @Transactional boundaries are correct

2. Security audit:
   - Confirm all /api/tenant/* endpoints require authenticated JWT
   - Confirm tenant_id never read from request params (grep for getParameter("tenant"))
   - Confirm all passwords are BCrypt hashed (never plain text)

3. Test run:
   mvn test -> must be 100% green
   docker compose up --build -> all 4 containers start healthy

4. Tag release:
   git add .
   git commit -m "feat: IMS MVP v1.0.0 complete"
   git tag -a v1.0.0 -m "MVP Release - Multi-tenant IMS"

Final checklist:
  [ ] All 28 agent tasks complete
  [ ] mvn test green
  [ ] docker compose up -> 4 containers healthy
  [ ] Swagger UI accessible at /swagger-ui.html
  [ ] E2E Postman collection all 12 steps passing
  [ ] README.md complete
  [ ] CHANGELOG.md created
  [ ] v1.0.0 tagged
```

---

## 9. MVP Feature Checklist

| Feature                                            | Phase   | Target   |
| -------------------------------------------------- | ------- | -------- |
| Multi-tenant architecture with JWT isolation       | Phase 1 | MVP v1.0 |
| Tenant CRUD (Platform Admin)                       | Phase 1 | MVP v1.0 |
| User management with RBAC                          | Phase 1 | MVP v1.0 |
| Product CRUD + Categories                          | Phase 1 | MVP v1.0 |
| Stock In/Out tracking + movements audit log        | Phase 1 | MVP v1.0 |
| Purchase Order flow                                | Phase 1 | MVP v1.0 |
| Sales Order flow (atomic stock decrement)          | Phase 1 | MVP v1.0 |
| Invoice PDF generation (iText 7)                   | Phase 1 | MVP v1.0 |
| Pharmacy: expiry tracking + batch numbers          | Phase 1 | MVP v1.0 |
| Pharmacy: expiry alert scheduler (daily 8AM)       | Phase 1 | MVP v1.0 |
| Warehouse: location mapping                        | Phase 2 | v1.1     |
| Warehouse: transfer orders between locations       | Phase 2 | v1.1     |
| Supermarket: barcode fast billing                  | Phase 2 | v1.1     |
| Dashboard KPI report (Redis cached 30min)          | Phase 1 | MVP v1.0 |
| Stock status report with urgency ordering          | Phase 1 | MVP v1.0 |
| Sales analytics with date range + top products     | Phase 1 | MVP v1.0 |
| Full audit log (stock_movements queryable)         | Phase 1 | MVP v1.0 |
| Redis/Valkey caching (products, stock, reports)    | Phase 1 | MVP v1.0 |
| JWT blacklist on logout                            | Phase 1 | MVP v1.0 |
| Rate limiting (Redis sliding window, 429 response) | Phase 1 | MVP v1.0 |
| Input validation + structured error responses      | Phase 1 | MVP v1.0 |
| Swagger / OpenAPI documentation                    | Phase 1 | MVP v1.0 |
| Docker Compose full stack (4 services)             | Phase 1 | MVP v1.0 |
| Integration tests with Testcontainers              | Phase 1 | MVP v1.0 |
| Supplier & Customer management                     | Phase 1 | MVP v1.0 |
| Subscription / plan management                     | Phase 3 | v1.2     |
| Multichannel integrations (Shopify, etc.)          | Phase 3 | v1.2     |
| Customer self-service portal                       | Phase 3 | v1.2     |
| Email / SMS notifications                          | Phase 3 | v1.2     |

---

## 10. Technology Stack

| Layer            | Technology                  | Version    | Purpose                                |
| ---------------- | --------------------------- | ---------- | -------------------------------------- |
| Language         | Java                        | 21 LTS     | Core language                          |
| Framework        | Spring Boot                 | 3.x        | REST, DI, Security                     |
| Database         | PostgreSQL                  | 16-alpine  | Primary relational store               |
| Cache / Sessions | Valkey                      | 7.2-alpine | JWT blacklist, rate limiting, caching  |
| ORM              | Spring Data JPA + Hibernate | 6.x        | DB access layer                        |
| Migrations       | Flyway                      | latest     | Schema versioning                      |
| Security         | Spring Security + JJWT      | 0.12.3     | JWT auth, RBAC                         |
| PDF              | iText 7                     | 7.2.5      | Invoice PDF generation                 |
| Containerization | Docker + Docker Compose     | latest     | Dev and production deployment          |
| Reverse Proxy    | Nginx                       | alpine     | SSL, headers, gzip                     |
| API Docs         | springdoc-openapi           | 2.3.0      | Swagger UI auto-generated              |
| Testing          | JUnit 5 + Testcontainers    | latest     | Integration tests with real Docker DBs |
| Build            | Maven                       | 3.9        | Dependency management                  |
| Monitoring       | Spring Actuator             | included   | Health checks, metrics                 |

> **Why Valkey over Redis?**
> Valkey is the community-maintained, fully open-source Redis fork (post-2024 license change). It is 100% API-compatible. Spring Boot's RedisTemplate and spring-data-redis work with zero code changes. Use image: valkey/valkey:7.2-alpine in Docker.

---

## 11. Key Implementation Notes

### 11.1 Tenant Isolation — The Most Critical Pattern

```java
// TenantContext.java
public class TenantContext {
    private static final ThreadLocal<Long> TENANT = new ThreadLocal<>();
    public static void set(Long tenantId) { TENANT.set(tenantId); }
    public static Long get() { return TENANT.get(); }
    public static void clear() { TENANT.remove(); }
}

// JwtFilter.java — always clear in finally
try {
    Long tenantId = jwtUtil.extractTenantId(token);
    TenantContext.set(tenantId);
    chain.doFilter(request, response);
} finally {
    TenantContext.clear(); // CRITICAL — prevents tenant bleed between requests
}

// ProductRepository.java — always scope by tenant
Optional<Product> findByIdAndTenantId(Long id, Long tenantId);
Page<Product> findByTenantId(Long tenantId, Pageable pageable);

// ProductService.java — get tenant from context, NEVER from request params
Long tenantId = TenantContext.get();        // CORRECT
// Long tenantId = request.getParam(...);   // WRONG — never do this

// NEVER do this:
productRepo.findAll();                           // WRONG — returns all tenants' data
productRepo.findByTenantId(tenantId, pageable);  // CORRECT
```

### 11.2 Stock Update — Transactional + Pessimistic Lock + Cache Eviction

```java
@Transactional
@CacheEvict(value = "stock", key = "#tenantId + ':' + #productId")
public void stockOut(Long tenantId, Long productId, int qty, String notes, Long userId) {

    // Pessimistic write lock prevents concurrent oversell
    Product product = productRepo.findByIdAndTenantIdWithLock(productId, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    if (product.getStock() < qty) {
        throw new InsufficientStockException(
            "Insufficient stock. Requested: " + qty + ", Available: " + product.getStock());
    }

    int previousStock = product.getStock();
    product.setStock(previousStock - qty);
    productRepo.save(product);

    // Mandatory audit trail — never skip this
    stockMovementRepo.save(StockMovement.builder()
        .tenantId(tenantId)
        .productId(productId)
        .movementType(MovementType.OUT)
        .quantity(qty)
        .previousStock(previousStock)
        .newStock(product.getStock())
        .notes(notes)
        .createdBy(userId)
        .build());
}

// Repository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id AND p.tenantId = :tenantId")
Optional<Product> findByIdAndTenantIdWithLock(Long id, Long tenantId);
```

### 11.3 Pharmacy Expiry Alert Scheduler

```java
@Service
@Slf4j
public class ExpiryAlertService {

    @Scheduled(cron = "0 0 8 * * *")
    public void checkExpiryAlerts() {
        LocalDate threshold = LocalDate.now().plusDays(30);
        List<PharmacyProduct> expiring = pharmacyProductRepo.findByExpiryDateBefore(threshold);

        expiring.forEach(pp -> log.warn(
            "EXPIRY ALERT: tenant={} product={} expires={}",
            pp.getProduct().getTenantId(),
            pp.getProduct().getName(),
            pp.getExpiryDate()));

        log.info("Expiry check complete. {} products expiring within 30 days.", expiring.size());
    }
}
```

### 11.4 Redis Rate Limiting

```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                     HttpServletResponse res,
                                     FilterChain chain) throws ServletException, IOException {
        String ip = req.getRemoteAddr();
        boolean authenticated = req.getHeader("Authorization") != null;
        int limit = authenticated ? 500 : 100;

        String key = "rate:" + ip + ":" + (System.currentTimeMillis() / 60000);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) redisTemplate.expire(key, 60, TimeUnit.SECONDS);

        res.addHeader("X-RateLimit-Limit", String.valueOf(limit));
        res.addHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)));

        if (count > limit) {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Too Many Requests\",\"retry_after\":60}");
            return;
        }

        chain.doFilter(req, res);
    }
}
```

### 11.5 RBAC AOP Aspect

```java
@Aspect @Component
public class RbacAspect {

    @Around("@annotation(requiresRole)")
    public Object checkRole(ProceedingJoinPoint pjp, RequiresRole requiresRole) throws Throwable {
        String currentRole = SecurityContextHolder.getContext()
            .getAuthentication().getAuthorities()
            .iterator().next().getAuthority();

        boolean allowed = Arrays.asList(requiresRole.value()).contains(currentRole);
        if (!allowed) throw new AccessDeniedException(
            "Required: " + Arrays.toString(requiresRole.value()) + ", Got: " + currentRole);

        return pjp.proceed();
    }
}

// Usage:
@RequiresRole({"ADMIN"})
public ResponseEntity<?> deleteProduct(@PathVariable Long id) { ... }

@RequiresRole({"ADMIN", "MANAGER"})
public ResponseEntity<?> createProduct(@Valid @RequestBody CreateProductRequest req) { ... }
```

### 11.6 Redis Cache Configuration

```java
@Configuration @EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put("products", ttl(15, TimeUnit.MINUTES));
        configs.put("stock",    ttl(5,  TimeUnit.MINUTES));
        configs.put("reports",  ttl(30, TimeUnit.MINUTES));
        configs.put("tenant",   ttl(1,  TimeUnit.HOURS));

        return RedisCacheManager.builder(factory)
            .withInitialCacheConfigurations(configs)
            .build();
    }

    private RedisCacheConfiguration ttl(long amount, TimeUnit unit) {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.of(amount, unit.toChronoUnit()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}
```

---

## Quick Reference: Docker Commands

```bash
# Start everything
docker compose up -d

# View logs
docker compose logs -f backend
docker compose logs -f postgres

# Check health of all containers
docker compose ps

# Connect to DB
docker exec -it ims_postgres psql -U ims_user -d ims_db

# Connect to Valkey CLI
docker exec -it ims_valkey valkey-cli --pass $REDIS_PASSWORD

# View all cache keys
docker exec -it ims_valkey valkey-cli --pass $REDIS_PASSWORD KEYS "*"

# Rebuild backend only
docker compose up -d --build backend

# Stop all
docker compose down

# Stop and wipe all data
docker compose down -v
```

## Quick Reference: Maven Commands

```bash
mvn test                     # Run all tests
mvn spring-boot:run          # Run locally (needs postgres + valkey running)
mvn package -DskipTests      # Build JAR
mvn dependency:tree          # Check dependency tree
mvn clean install -U         # Force update all dependencies
mvn flyway:info              # Check migration status
```

---

*IMS PRD v1.0 — Built for Java backend engineers building production-grade multi-tenant SaaS*

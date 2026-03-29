## 1. Missing API Route Specifications (JSON/cURL)

**Since your system is stateless, these requests must include the **`<span class="citation-143">Authorization: Bearer <JWT></span>` header^^^^^^^^.

### A. Create Tenant Admin (Platform Level)

This allows the "Creator" or Platform Admin to onboard the first user for a new business.

* **Path:** `POST /api/platform/tenants/{tenantId}/users`
* **Scope:**`<span class="citation-142">PLATFORM</span>`^^^^

**JSON**

```
// POST /api/platform/tenants/123/users
{
  "username": "pharmacy_owner_01",
  "email": "admin@xyzpharmacy.com",
  "password": "SecurePassword123!",
  "role": "ADMIN",
  "scope": "TENANT"
}
```

### B. Register Product Category (Tenant Level)

**Necessary before creating products to satisfy the **`<span class="citation-141">category_id</span>` requirement^^.

* **Path:** `POST /api/tenant/categories`
* **Scope:**`<span class="citation-140">TENANT</span>`^^

**Bash**

```
curl -X POST https://api.ims.com/api/tenant/categories \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Antibiotics",
    "description": "Prescription only medications",
    "tax_rate": 0.05
  }'
```

### C. Record a Sale with Billing (Tenant Level)

**Expands on the "Sales and Billing" requirement**^^.

* **Path:** `POST /api/tenant/sales`

**JSON**

```
{
  "customer_id": "cust_987",
  "payment_method": "CASH",
  "items": [
    {
      "product_id": "prod_001",
      "quantity": 2,
      "unit_price": 15.50
    }
  ],
  "discount_total": 2.00,
  "grand_total": 29.00
}
```

---

## 2. PRD: Administrative Hierarchy & Governance

### **1. Introduction**

**The IMS requires a two-tiered administrative structure to separate system-wide maintenance (Super Admin/Creator) from business-specific operations (Tenant Admin)**^^.

### **2. User Personas**

| **Persona**        | **Scope** | **Primary Responsibility**                                          |
| ------------------------ | --------------- | ------------------------------------------------------------------------- |
| **Root (Creator)** | `PLATFORM`    | **Full system access, DB health, and platform config**^^^^^^^^.     |
| **Platform Admin** | `PLATFORM`    | **Tenant onboarding, subscription management, and support**^^^^^^.  |
| **Tenant Admin**   | `TENANT`      | **Managing their specific business catalog, staff, and billing**^^. |

### **3. Functional Requirements**

#### **3.1 Super Admin (Platform/Root) Capabilities**

* **Tenant Lifecycle:** Create, suspend, or delete tenants (e.g., if a subscription expires)^^.
* **Global Monitoring:** View aggregated audit logs across all tenants for troubleshooting^^^^.
* **Plan Management:** Define limits for different tiers (e.g., "Silver" allows 500 products, "Gold" unlimited)^^.
* **System Config:** Manage global feature flags (e.g., enabling the "Pharmacy" extension for specific users)^^^^^^^^.

#### **3.2 Tenant Admin Capabilities**

* **Staff Management:** Create and manage `<span class="citation-130">Manager</span>` and `<span class="citation-130">Staff</span>` roles within their `<span class="citation-130">tenant_id</span>`^^^^^^^^.
* **Business Settings:** Configure custom domains or subdomains (e.g., `<span class="citation-129">shop.tenant.com</span>`)^^.
* **Inventory Control:** Perform manual stock adjustments and oversee P&L reports^^^^^^^^.

### **4. Security & Isolation Rules**

* **JWT Scope:** Tokens must explicitly state if the user is `<span class="citation-127">PLATFORM</span>` or `<span class="citation-127">TENANT</span>`^^.
* **Tenant Filter:** Every database query for a Tenant Admin MUST include `<span class="citation-126">WHERE tenant_id = ?</span>` derived from the JWT, never from the client request body^^^^^^^^.
* **Data Masking:** Super Admins can see metadata (tenant name, user count) but should be restricted from seeing specific sensitive business data (customer names/prices) unless "Support Mode" is explicitly enabled^^^^^^^^.

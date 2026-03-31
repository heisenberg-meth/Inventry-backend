### The Master Prompt

> **Task** : Implement a Multi-Tenant Authentication System in Spring Boot.
>
> **Context** :
>
> I have a system with two types of users:
>
> 1. **Platform Admins (Super Admins)** : Log in with just `email` and `password`.
> 2. **Tenant Users (Business Accounts)** : Log in with `email`, `password`, and `companyCode`.
>
> **Requirements** :
>
> 1. **Update DTOs** :
>
> * Modify `LoginRequest.java` to include an optional `String companyCode`.
> * Update `LoginResponse.java` to match the required JSON structure: include `accessToken`, `refreshToken`, `expiresIn`, and a `user` object containing `id`, `name`, `email`, `type` (PLATFORM or TENANT), and `platformRole` or `tenantRole`.
>
> 1. **Authentication Logic** :
>
> * Create/Update an `AuthService`.
> * If `companyCode` is null/empty: Search for the user in the global `PlatformUser` table (or Users with a `null` tenantId). Validate credentials for a Super Admin.
> * If `companyCode` is provided:
>   a. Find the Tenant by `domain` or `companyCode`.
>   b. Search for the user specifically within that `tenantId`.
>   c. Validate credentials.
>
> 1. **Security Configuration** :
>
> * Ensure the JWT contains the `tenantId` (if applicable) and `role` as claims.
> * Implement the `/auth/login` endpoint in an `AuthController`.
>
> 1. **Additional Endpoints** :
>
> * Implement stubs/logic for `/auth/refresh`, `/auth/logout`, and `/auth/me`.
>
> **Code Style** :
>
> * Use the provided `com.ims` package structure.
> * Use Lombok `@Data`, `@Builder`, and Jackson `@JsonProperty` for snake_case mapping.
> * Follow strict null safety using `Objects.requireNonNull`.
>
> **Reference Files Provided** : (Inject the file contents of `LoginRequest.java`, `LoginResponse.java`, and `TenantService.java` here).

---

### Step 1: Update your DTOs immediately

Based on your provided files, here are the corrected DTOs to support your specific JSON requirements:

#### Update `LoginRequest.java`

**Java**

```
package com.ims.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    // Optional: Only required for Tenant/Business login
    private String companyCode; 
}
```

#### Update `LoginResponse.java`

**Java**

```
package com.ims.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    @JsonProperty("accessToken")
    private String accessToken;

    @JsonProperty("refreshToken")
    private String refreshToken;

    @JsonProperty("expiresIn")
    private long expiresIn;

    private UserProfile user;

    @Data
    @Builder
    public static class UserProfile {
        private String id;
        private String name;
        private String email;
        private String type; // "PLATFORM" or "TENANT"
        private String platformRole; // Optional
    }
}
```

### Step 2: Logical Flow for the Controller/Service

When you process the login:

1. **Check for `companyCode`** :

* `if (request.getCompanyCode() == null)` **$\rightarrow$** Query `PlatformUserRepository`.
* `else` **$\rightarrow$** Query `TenantRepository` to find the ID, then query `UserRepository` where `email = :email AND tenantId = :tenantId`.

1. **JWT Claims** :

* For Platform users, set `tenantId = null`.
* For Tenant users, set `tenantId = theActualId`.
* This allows your `JwtFilter` to set the correct context for every subsequent request.

import os
import re

def refactor_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    original_content = content

    # Replace @RequiresRole({"A", "B"}) with @PreAuthorize("hasAnyRole('A', 'B')")
    def replace_multiple_roles(m):
        roles = m.group(1).replace('"', "'")
        return f'@PreAuthorize("hasAnyRole({roles})")'

    content = re.sub(
        r'@RequiresRole\s*\(\s*\{\s*([^}]+)\s*\}\s*\)',
        replace_multiple_roles,
        content
    )

    # Replace @RequiresRole("A") with @PreAuthorize("hasRole('A')")
    content = re.sub(
        r'@RequiresRole\s*\(\s*"([^"]+)"\s*\)',
        lambda m: f'@PreAuthorize("hasRole(\'{m.group(1)}\')")',
        content
    )

    # Replace @RequiresPermission("P") with @PreAuthorize("hasAuthority('P')")
    content = re.sub(
        r'@RequiresPermission\s*\(\s*"([^"]+)"\s*\)',
        lambda m: f'@PreAuthorize("hasAuthority(\'{m.group(1)}\')")',
        content
    )

    if content != original_content:
        # Update imports
        content = content.replace('import com.ims.shared.rbac.RequiresRole;', '')
        content = content.replace('import com.ims.shared.rbac.RequiresPermission;', '')
        
        if 'import org.springframework.security.access.prepost.PreAuthorize;' not in content:
            # Add after first package or import
            if 'package ' in content:
                content = re.sub(r'(package [^;]+;)', r'\1\n\nimport org.springframework.security.access.prepost.PreAuthorize;', content, count=1)
            else:
                content = 'import org.springframework.security.access.prepost.PreAuthorize;\n' + content

        # Clean up double newlines that might have been caused by removing imports
        content = re.sub(r'\n\n\n+', '\n\n', content)

        with open(filepath, 'w') as f:
            f.write(content)
        return True
    return False

files_to_process = [
    "src/main/java/com/ims/platform/controller/SubscriptionPlanController.java",
    "src/main/java/com/ims/product/ProductController.java",
    "src/main/java/com/ims/product/ProductService.java",
    "src/main/java/com/ims/tenant/controller/CustomerController.java",
    "src/main/java/com/ims/tenant/controller/InvoiceController.java",
    "src/main/java/com/ims/tenant/controller/NotificationController.java",
    "src/main/java/com/ims/tenant/controller/OrderController.java",
    "src/main/java/com/ims/tenant/controller/PaymentController.java",
    "src/main/java/com/ims/tenant/controller/PaymentGatewayController.java",
    "src/main/java/com/ims/tenant/controller/PermissionController.java",
    "src/main/java/com/ims/tenant/controller/ReportController.java",
    "src/main/java/com/ims/tenant/controller/RoleController.java",
    "src/main/java/com/ims/tenant/controller/SaleController.java",
    "src/main/java/com/ims/tenant/controller/StockController.java",
    "src/main/java/com/ims/tenant/controller/SupplierController.java",
    "src/main/java/com/ims/tenant/controller/TenantAuditController.java",
    "src/main/java/com/ims/tenant/controller/TenantSettingsController.java",
    "src/main/java/com/ims/tenant/controller/TenantSupportController.java",
    "src/main/java/com/ims/tenant/controller/UserController.java",
    "src/main/java/com/ims/tenant/controller/WebhookController.java",
    "src/main/java/com/ims/tenant/service/SupplierService.java"
]

for filepath in files_to_process:
    if os.path.exists(filepath):
        if refactor_file(filepath):
            print(f"Refactored {filepath}")
        else:
            print(f"No changes for {filepath}")
    else:
        print(f"File not found: {filepath}")

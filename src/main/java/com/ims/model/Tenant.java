package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

  /**
   * Default number of days before expiry at which pharmacy tenants raise stock
   * alerts.
   */
  private static final int DEFAULT_EXPIRY_THRESHOLD_DAYS = 30;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  private Long version;

  @Column(nullable = false)
  private String name;

  @Column(name = "workspace_slug", unique = true)
  private String workspaceSlug;

  @Column(name = "company_code", nullable = false, unique = true)
  private String companyCode;

  @Column(name = "business_type", nullable = false)
  private String businessType;

  @Column
  @Builder.Default
  private String plan = "FREE";

  @Column
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private TenantStatus status = TenantStatus.PENDING;

  @Column(name = "invoice_sequence")
  @Builder.Default
  private Integer invoiceSequence = 0;

  @Column(name = "max_products")
  private Integer maxProducts;

  @Column(name = "max_users")
  private Integer maxUsers;

  @Column(name = "expiry_threshold_days")
  @Builder.Default
  private Integer expiryThresholdDays = DEFAULT_EXPIRY_THRESHOLD_DAYS;

  @Column
  private String address;

  @Column
  private String gstin;

  @Column(name = "webhook_secret")
  private String webhookSecret;

  @Column(name = "ip_whitelist", columnDefinition = "TEXT")
  private String ipWhitelist;

  @Column(name = "created_at")
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  public static class TenantBuilder {
    private Long id;
    private Long version;
    private String name;
    private String workspaceSlug;
    private String companyCode;
    private String businessType;
    private String plan;
    private TenantStatus statusValue;
    private boolean statusSet = false;
    private Integer invoiceSequence;
    private Integer maxProducts;
    private Integer maxUsers;
    private Integer expiryThresholdDays;
    private String address;
    private String gstin;
    private String webhookSecret;
    private String ipWhitelist;
    private LocalDateTime createdAt;

    public TenantBuilder status(TenantStatus status) {
      if (this.statusSet) {
        throw new IllegalStateException("Status already set to: " + this.statusValue);
      }
      this.statusValue = status;
      this.statusSet = true;
      return this;
    }

    public Tenant build() {
      TenantStatus finalStatus = statusSet ? statusValue : TenantStatus.PENDING;
      return new Tenant(id, version, name, workspaceSlug, companyCode, businessType,
          plan != null ? plan : "FREE",
          finalStatus,
          invoiceSequence != null ? invoiceSequence : 0,
          maxProducts, maxUsers,
          expiryThresholdDays != null ? expiryThresholdDays : DEFAULT_EXPIRY_THRESHOLD_DAYS,
          address, gstin, webhookSecret, ipWhitelist,
          createdAt != null ? createdAt : LocalDateTime.now());
    }
  }
}

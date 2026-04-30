package com.ims.platform.controller;

import com.ims.dto.request.CreateSubscriptionPlanRequest;
import com.ims.dto.request.UpdateSubscriptionPlanRequest;
import com.ims.model.SubscriptionPlan;
import com.ims.model.SubscriptionPlanStatus;
import com.ims.platform.service.SubscriptionPlanService;
import com.ims.shared.rbac.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/subscription-plans")
@RequiredArgsConstructor
@Tag(name = "Platform - Subscription Plans", description = "Manage subscription plans")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionPlanController {

  private final SubscriptionPlanService planService;

  @PostMapping
  @RequiresRole({ "ROOT" })
  @Operation(summary = "Create subscription plan")
  public ResponseEntity<SubscriptionPlan> create(
      @Valid @RequestBody CreateSubscriptionPlanRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(planService.createPlan(request));
  }

  @GetMapping
  @RequiresRole({ "ROOT", "PLATFORM_ADMIN" })
  @Operation(summary = "List all subscription plans")
  public ResponseEntity<List<SubscriptionPlan>> findAll(
      @RequestParam(required = false) SubscriptionPlanStatus status) {
    return ResponseEntity.ok(planService.findAll(status));
  }

  @GetMapping("/{id}")
  @RequiresRole({ "ROOT", "PLATFORM_ADMIN" })
  @Operation(summary = "Get subscription plan details")
  public ResponseEntity<SubscriptionPlan> findOne(@PathVariable Long id) {
    return ResponseEntity.ok(planService.findOne(id));
  }

  @PatchMapping("/{id}")
  @RequiresRole({ "ROOT" })
  @Operation(summary = "Update subscription plan")
  public ResponseEntity<SubscriptionPlan> update(
      @PathVariable Long id, @RequestBody UpdateSubscriptionPlanRequest request) {
    return ResponseEntity.ok(planService.updatePlan(id, request));
  }

  @DeleteMapping("/{id}")
  @RequiresRole({ "ROOT" })
  @Operation(summary = "Soft-delete subscription plan")
  public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
    return ResponseEntity.ok(planService.deletePlan(id));
  }

  @PostMapping("/{id}/activate")
  @RequiresRole({ "ROOT" })
  @Operation(summary = "Activate subscription plan")
  public ResponseEntity<Map<String, String>> activate(@PathVariable Long id) {
    return ResponseEntity.ok(planService.activatePlan(id));
  }

  @PostMapping("/{id}/deactivate")
  @RequiresRole({ "ROOT" })
  @Operation(summary = "Deactivate subscription plan")
  public ResponseEntity<Map<String, String>> deactivate(@PathVariable Long id) {
    return ResponseEntity.ok(planService.deactivatePlan(id));
  }

  @GetMapping("/usage-summary")
  @RequiresRole({ "ROOT" })
  @Operation(summary = "Get plan usage summary with MRR/ARR metrics")
  public ResponseEntity<Map<String, Object>> usageSummary() {
    return ResponseEntity.ok(planService.getUsageSummary());
  }
}

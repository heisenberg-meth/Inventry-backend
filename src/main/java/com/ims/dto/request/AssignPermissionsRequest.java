package com.ims.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class AssignPermissionsRequest {

  @NotEmpty(message = "Permission IDs are required")
  private List<Long> permissionIds;
}

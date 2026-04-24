package com.ims.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
  private Long id;
  private String name;
  private String description;
  private BigDecimal taxRate;
  private LocalDateTime createdAt;
}

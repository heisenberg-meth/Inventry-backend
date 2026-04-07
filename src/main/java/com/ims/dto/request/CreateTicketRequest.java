package com.ims.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTicketRequest {

  @NotBlank(message = "Title is required")
  @Size(max = 255, message = "Title must be at most 255 characters")
  private String title;

  @NotBlank(message = "Description is required")
  private String description;

  private String priority = "MEDIUM";

  private String category = "GENERAL";
}

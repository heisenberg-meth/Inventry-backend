package com.ims.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic DTO for paginated responses. Replaces Spring Data Page to avoid serialization/casting
 * issues in cache.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {

  private List<T> content;
  private long totalElements;
  private int totalPages;
  private int pageNumber;
  private int pageSize;

  public PagedResponse(List<T> content, long totalElements, int totalPages) {
    this.content = content;
    this.totalElements = totalElements;
    this.totalPages = totalPages;
  }
}

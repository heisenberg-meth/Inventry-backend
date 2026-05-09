package com.ims.category;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.dto.CategoryRequest;
import com.ims.dto.response.CategoryResponse;
import com.ims.shared.auth.JwtFilter;
import com.ims.shared.auth.JwtUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerSliceTest {

  @TestConfiguration
  static class TestConfig {
    @Bean
    public MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private CategoryService categoryService;

  @MockitoBean private JwtUtil jwtUtil;

  @MockitoBean private JwtFilter jwtFilter;

  @MockitoBean private com.ims.shared.ratelimit.RateLimiterService rateLimiterService;

  @BeforeEach
  void setUp() {
    com.ims.shared.auth.TenantContext.setTenantId(1L);
  }

  @Test
  void list_ShouldReturnOk() throws Exception {
    when(categoryService.getCategories(any())).thenReturn(new PageImpl<>(List.of()));

    mockMvc.perform(get("/api/v1/tenant/categories")).andDo(print()).andExpect(status().isOk());
  }

  @Test
  void create_ShouldReturnCreated() throws Exception {
    CategoryRequest request = new CategoryRequest();
    request.setName("New Category");
    request.setTaxRate(BigDecimal.TEN);

    CategoryResponse response =
        CategoryResponse.builder().id(1L).name("New Category").taxRate(BigDecimal.TEN).build();

    when(categoryService.create(any())).thenReturn(new Category());
    when(categoryService.toResponse(any())).thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/tenant/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("New Category"));
  }

  @Test
  void create_ShouldReturnBadRequest_WhenNameIsBlank() throws Exception {
    CategoryRequest request = new CategoryRequest();
    request.setName("");

    mockMvc
        .perform(
            post("/api/v1/tenant/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isBadRequest());
  }
}

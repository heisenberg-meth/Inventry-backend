package com.ims;

import com.ims.shared.auth.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class ImsApplicationTests extends BaseIntegrationTest {

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void contextLoads() {
    }
}
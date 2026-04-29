package com.ims.config;

import com.ims.shared.auth.TenantArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TenantArgumentResolver tenantArgumentResolver;

    public WebConfig(TenantArgumentResolver tenantArgumentResolver) {
        this.tenantArgumentResolver = tenantArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(tenantArgumentResolver);
    }
}

package com.ims.shared.security;

import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantSecurityService {

    private final TenantRepository tenantRepository;

    @Cacheable(value = "tenant", key = "'ip-whitelist:' + #tenantId")
    public String getCachedIpWhitelist(Long tenantId) {
        return tenantRepository.findById(tenantId).map(Tenant::getIpWhitelist).orElse(null);
    }
}

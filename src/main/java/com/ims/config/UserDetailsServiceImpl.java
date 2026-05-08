package com.ims.config;

import com.ims.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service("userDetailsService")
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(u -> {
                    String role = u.getRole();
                    if (role != null && role.startsWith("ROLE_")) {
                        role = role.substring(5);
                    }
                    return User.builder()
                            .username(u.getEmail())
                            .password(u.getPasswordHash())
                            .roles(role)
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}

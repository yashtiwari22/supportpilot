package com.supportpilot.auth;

import com.supportpilot.common.TenantContext;
import com.supportpilot.tenant.Tenant;
import com.supportpilot.tenant.TenantRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service managing user authentication and JWT session creation.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        Tenant tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Associated tenant not found"));

        String token = jwtTokenProvider.generateToken(user);
        UserDto userDto = new UserDto(
                user.getId(),
                user.getTenantId(),
                tenant.getName(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name()
        );

        return new LoginResponse(token, userDto);
    }

    public UserDto getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Tenant tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Associated tenant not found"));

        return new UserDto(
                user.getId(),
                user.getTenantId(),
                tenant.getName(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name()
        );
    }
}

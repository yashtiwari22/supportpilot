package com.supportpilot.auth;

import java.util.UUID;

public record UserDto(
    UUID id,
    UUID tenantId,
    String tenantName,
    String email,
    String fullName,
    String role
) {}

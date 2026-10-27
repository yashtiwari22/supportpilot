package com.supportpilot.auth;

public record LoginResponse(
    String token,
    UserDto user
) {}

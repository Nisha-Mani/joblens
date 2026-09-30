package com.joblens.auth.dto;

import com.joblens.user.UserResponse;
import java.time.Instant;

public record AuthResponse(String token, Instant expiresAt, UserResponse user) { }

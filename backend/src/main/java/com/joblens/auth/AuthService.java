package com.joblens.auth;

import com.joblens.auth.dto.AuthResponse;
import com.joblens.auth.dto.LoginRequest;
import com.joblens.auth.dto.RegisterRequest;
import com.joblens.common.error.ApiException;
import com.joblens.common.error.ConflictException;
import com.joblens.user.Role;
import com.joblens.user.User;
import com.joblens.user.UserRepository;
import com.joblens.user.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        // Compared against when the email is unknown so response time does not reveal it.
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user;
        try {
            user = users.saveAndFlush(
                new User(email, passwordEncoder.encode(request.password()), Role.USER));
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration for the same email.
            throw new ConflictException("An account with this email already exists");
        }
        log.info("User registered: id={}", user.getId());
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(normalize(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            log.warn("Failed login attempt");
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        log.info("User logged in: id={}", user.getId());
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}

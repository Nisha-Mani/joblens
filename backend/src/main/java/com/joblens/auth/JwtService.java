package com.joblens.auth;

import com.joblens.user.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/** Issues signed access tokens. Validation is handled by Spring's resource server. */
@Service
public class JwtService {

    private final JwtProperties properties;
    private final JwtEncoder encoder;

    public JwtService(JwtProperties properties, SecretKey jwtSigningKey) {
        this.properties = properties;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.expiryMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) { }
}

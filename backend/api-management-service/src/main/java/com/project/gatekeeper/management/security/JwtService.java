package com.project.gatekeeper.management.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.project.gatekeeper.management.domain.AdminEntity;
import com.project.gatekeeper.management.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final byte[] secret;
    private final long ttlHours;

    public JwtService(@Value("${gatekeeper.jwt.secret}") String secret,
                      @Value("${gatekeeper.jwt.ttl-hours:24}") long ttlHours) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlHours = ttlHours;
        if (this.secret.length < 32) {
            throw new IllegalStateException("gatekeeper.jwt.secret must be at least 32 bytes");
        }
    }

    public String issue(AdminEntity admin) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(admin.getId())
                .claim("email", admin.getEmail())
                .claim("name", admin.getName())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlHours * 3600)))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "token_error", "Could not issue a login token");
        }
    }

    public JWTClaimsSet parse(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                throw unauthorized();
            }
            Date expires = jwt.getJWTClaimsSet().getExpirationTime();
            if (expires == null || expires.before(new Date())) {
                throw unauthorized();
            }
            return jwt.getJWTClaimsSet();
        } catch (ParseException | JOSEException ex) {
            throw unauthorized();
        }
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Login again");
    }
}

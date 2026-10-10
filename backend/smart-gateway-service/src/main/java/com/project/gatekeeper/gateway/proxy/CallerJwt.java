package com.project.gatekeeper.gateway.proxy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;

@Component
public class CallerJwt {

    private final byte[] secret;

    public CallerJwt(@Value("${gatekeeper.jwt.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalStateException("gatekeeper.jwt.secret must be at least 32 bytes");
        }
    }

    public boolean valid(String authorization) {
        return subject(authorization) != null;
    }

    public String subject(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                return null;
            }
            Date expires = jwt.getJWTClaimsSet().getExpirationTime();
            if (expires == null || !expires.after(new Date())) {
                return null;
            }
            String subject = jwt.getJWTClaimsSet().getSubject();
            return subject == null || subject.isBlank() ? null : subject;
        } catch (ParseException | JOSEException ex) {
            return null;
        }
    }
}

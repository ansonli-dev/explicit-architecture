package com.example.catalog.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class DemoTokens implements AutoCloseable {

    public static final String ISSUER = "http://public.test/realms/bookstore";
    private final HttpServer jwks;
    private final RSAKey key;

    public DemoTokens() {
        try {
            key = signingKey();
            jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            jwks.createContext("/certs", exchange -> {
                byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var stream = exchange.getResponseBody()) {
                    stream.write(body);
                }
            });
            jwks.start();
        } catch (IOException | JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public void properties(DynamicPropertyRegistry properties) {
        properties.add("demo.jwt.issuer", () -> ISSUER);
        properties.add("demo.jwt.jwk-set-uri", () -> "http://127.0.0.1:" + jwks.getAddress().getPort() + "/certs");
    }

    public String admin() {
        return sign("bookstore-bff", "bookstore-catalog", "bookstore-admin");
    }

    public String reader() {
        return sign("bookstore-bff", "bookstore-catalog", "bookstore-reader");
    }

    public String orderService() {
        return sign("bookstore-order", "bookstore-catalog-internal", "bookstore-order-service");
    }

    public String sign(String client, String audience, String role) {
        return sign(ISSUER, client, audience, role, Instant.now().plusSeconds(120), true);
    }

    public String sign(String issuer, String client, String audience, String role, Instant expiry,
                       boolean trustedSignature) {
        try {
            var claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject("00000000-0000-4000-8000-000000000abc")
                    .audience(audience)
                    .claim("azp", client)
                    .claim("realm_access", Map.of("roles", List.of(role)));
            if (expiry != null) {
                claims.expirationTime(Date.from(expiry));
            }
            var jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
                    claims.build());
            jwt.sign(new RSASSASigner(trustedSignature ? key : signingKey()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey signingKey() throws JOSEException {
        return new RSAKeyGenerator(2048).keyID("catalog-test").generate();
    }

    @Override
    public void close() {
        jwks.stop(0);
    }
}

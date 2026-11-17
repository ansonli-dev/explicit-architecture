package com.example.catalog.interfaces.rest.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties("demo.jwt")
public record DemoJwtProperties(URI issuer, URI jwkSetUri) {

    public DemoJwtProperties {
        requireHttpUrl(issuer, "DEMO_JWT_ISSUER");
        requireHttpUrl(jwkSetUri, "DEMO_JWT_JWK_SET_URI");
    }

    private static void requireHttpUrl(URI uri, String name) {
        if (uri == null || uri.getHost() == null
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
            throw new IllegalArgumentException(name + " must be an absolute HTTP(S) URL");
        }
    }
}

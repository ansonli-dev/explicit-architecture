package com.example.order.interfaces.rest.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@Profile("demo-integrated")
@EnableWebSecurity
@EnableConfigurationProperties(DemoJwtProperties.class)
public class DemoSecurityConfiguration {

    private static final String READER = "bookstore-reader";

    @Bean
    SecurityFilterChain demoSecurity(HttpSecurity http, JwtDecoder decoder) throws Exception {
        var bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
        AuthenticationEntryPoint unauthorized = (request, response, cause) -> {
            bearerEntryPoint.commence(request, response, cause);
            writeCode(response, "UNAUTHORIZED");
        };
        var bearerDenied = new BearerTokenAccessDeniedHandler();
        var authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(token ->
                token.getClaims().get("realm_access") instanceof Map<?, ?> realm
                        && realm.get("roles") instanceof Collection<?> roles && roles.contains(READER)
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + READER)) : List.of());

        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/me/order-quotes", "/api/v1/me/order-submissions")
                                .hasRole(READER)
                        .requestMatchers(HttpMethod.GET, "/api/v1/me/order-quotes/{id}", "/api/v1/me/order-submissions/{id}",
                                "/api/v1/me/orders", "/api/v1/me/orders/{id}", "/api/v1/me/orders/{id}/cancellation").hasRole(READER)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/me/orders/{id}/cancellation").hasRole(READER)
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler((request, response, cause) -> {
                            bearerDenied.handle(request, response, cause);
                            writeCode(response, "FORBIDDEN");
                        }))
                .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(unauthorized)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(authentication)))
                .build();
    }

    @Bean
    JwtDecoder demoJwtDecoder(DemoJwtProperties config) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(config.jwkSetUri().toString()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(config.issuer().toString()),
                new JwtTimestampValidator(Duration.ZERO),
                DemoSecurityConfiguration::validateReaderClaims));
        return decoder;
    }

    private static OAuth2TokenValidatorResult validateReaderClaims(Jwt token) {
        if (token.getExpiresAt() != null && token.getAudience() != null
                && token.getAudience().contains("bookstore-order-api")
                && "bookstore-bff".equals(token.getClaims().get("azp"))
                && isCanonicalUuid(token.getSubject())) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }

    private static boolean isCanonicalUuid(String subject) {
        if (subject == null || subject.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(subject).toString().equals(subject);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void writeCode(HttpServletResponse response, String code) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}

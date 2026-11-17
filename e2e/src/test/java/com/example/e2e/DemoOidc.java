package com.example.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DemoOidc {
    private static final URI PUBLIC_ISSUER = URI.create("http://localhost:8180/realms/bookstore");
    private static final String CALLBACK = "http://localhost:3000/api/auth/oauth2/callback/keycloak";
    private static final Pattern LOGIN_FORM = Pattern.compile(
            "<form\\b(?=[^>]*\\bid=\"kc-form-login\")[^>]*\\baction=\"([^\"]+)\"");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI issuer = URI.create(System.getenv("DEMO_OIDC_BASE_URL"));

    String login(String username) throws Exception {
        HttpClient browser = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String verifier = base64(random);
        String challenge = base64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String state = UUID.randomUUID().toString();

        URI authorize = URI.create(endpoint("auth") + "?" + form(Map.of(
                "client_id", "bookstore-bff", "redirect_uri", CALLBACK,
                "response_type", "code", "scope", "openid email profile",
                "state", state, "code_challenge", challenge, "code_challenge_method", "S256")));
        var page = browser.send(HttpRequest.newBuilder(authorize).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode(), "OIDC authorization must show the login page");
        var login = LOGIN_FORM.matcher(page.body());
        assertTrue(login.find(), "OIDC login form is missing");

        URI action = URI.create(login.group(1).replace("&amp;", "&"));
        assertEquals(PUBLIC_ISSUER.getScheme(), action.getScheme(), "OIDC login action scheme");
        assertEquals(PUBLIC_ISSUER.getAuthority(), action.getAuthority(), "OIDC login action authority");
        assertEquals(PUBLIC_ISSUER.getPath() + "/login-actions/authenticate", action.getPath(), "OIDC login action path");
        URI internalAction = new URI(issuer.getScheme(), issuer.getAuthority(), action.getPath(), action.getQuery(), null);
        // Browsers accept Secure cookies on localhost. The fixture sends them only to this realm's login action.
        String cookies = page.headers().allValues("Set-Cookie").stream()
                .map(value -> value.split(";", 2)[0])
                .collect(Collectors.joining("; "));
        assertTrue(!cookies.isEmpty(), "OIDC login session cookie missing");
        var loginRequest = formRequest(internalAction, Map.of("username", username, "password", username + "-local"))
                .header("Cookie", cookies).build();
        var callback = browser.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, callback.statusCode(), "OIDC login must redirect to the registered callback");
        URI location = URI.create(callback.headers().firstValue("Location").orElseThrow());
        assertEquals(CALLBACK, location.getScheme() + "://" + location.getAuthority() + location.getPath());
        var params = java.util.Arrays.stream(location.getRawQuery().split("&"))
                .map(parameter -> parameter.split("=", 2))
                .collect(Collectors.toMap(parameter -> parameter[0], parameter -> parameter[1]));
        assertEquals(state, params.get("state"), "OIDC callback state");
        assertTrue(params.containsKey("code"), "OIDC callback must include an authorization code");

        return token(browser, Map.of(
                "grant_type", "authorization_code", "client_id", "bookstore-bff",
                "client_secret", "bookstore-bff-local", "redirect_uri", CALLBACK,
                "code_verifier", verifier, "code", params.get("code")));
    }

    String serviceToken() throws Exception {
        return token(HttpClient.newHttpClient(), Map.of(
                "grant_type", "client_credentials", "client_id", "bookstore-order",
                "client_secret", "bookstore-order-local"));
    }

    private String token(HttpClient client, Map<String, String> parameters) throws Exception {
        var response = client.send(formRequest(endpoint("token"), parameters).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "OIDC token exchange status");
        var token = JSON.readTree(response.body()).path("access_token");
        assertTrue(token.isTextual() && !token.textValue().isBlank(), "OIDC access token missing");
        return token.textValue();
    }

    private URI endpoint(String name) {
        return URI.create(issuer + "/protocol/openid-connect/" + name);
    }

    private static HttpRequest.Builder formRequest(URI url, Map<String, String> parameters) {
        return HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(parameters)));
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

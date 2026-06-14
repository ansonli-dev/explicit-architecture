package {{BASE_PACKAGE}}.infrastructure.notification;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

class {{PROVIDER}}ClientTest {
    @RegisterExtension
    static WireMockExtension provider = WireMockExtension.newInstance().build();

    @Test
    void sends_expected_provider_request() {
        provider.stubFor(post(urlPathEqualTo("/{{PROVIDER_ENDPOINT}}"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withRequestBody(containing("{{MESSAGE_TYPE}}"))
            .willReturn(created()));

        var client = new {{PROVIDER}}Client(
            RestClient.builder().baseUrl(provider.baseUrl()).build(),
            "test-token"
        );

        client.send("user@example.test", "{{MESSAGE_TYPE}}");

        provider.verify(postRequestedFor(urlPathEqualTo("/{{PROVIDER_ENDPOINT}}"))
            .withRequestBody(containing("user@example.test")));
    }
}

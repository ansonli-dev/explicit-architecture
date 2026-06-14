package {{BASE_PACKAGE}}.infrastructure.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class {{TARGET}}RestClientTest {
    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance().build();

    @Test
    void maps_successful_response() {
        var targetId = UUID.randomUUID();
        server.stubFor(get(urlPathEqualTo("/{{RESOURCE_PATH}}/%s".formatted(targetId)))
            .withHeader("Accept", equalTo("application/json"))
            .willReturn(okJson("""
                {"targetId":"%s","active":true}
                """.formatted(targetId))));

        var client = new {{TARGET}}RestClient(RestClient.builder().baseUrl(server.baseUrl()).build());

        var result = client.getTarget(targetId);

        assertThat(result.targetId()).isEqualTo(targetId);
        assertThat(result.active()).isTrue();
    }

    @Test
    void maps_not_found_to_port_exception() {
        var targetId = UUID.randomUUID();
        server.stubFor(get(urlPathEqualTo("/{{RESOURCE_PATH}}/%s".formatted(targetId)))
            .willReturn(aResponse().withStatus(404)));

        var client = new {{TARGET}}RestClient(RestClient.builder().baseUrl(server.baseUrl()).build());

        assertThatThrownBy(() -> client.getTarget(targetId))
            .isInstanceOf({{TARGET}}NotFoundException.class);
    }
}

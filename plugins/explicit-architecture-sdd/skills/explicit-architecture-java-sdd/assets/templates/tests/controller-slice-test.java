package {{BASE_PACKAGE}}.interfaces.rest;

import {{BASE_PACKAGE}}.application.command.{{FEATURE_PACKAGE}}.{{ACTION}}{{AGGREGATE}}Result;
import {{BASE_PACKAGE}}.application.port.inbound.CommandBus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({{AGGREGATE}}CommandController.class)
class {{AGGREGATE}}CommandControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockBean
    CommandBus commandBus;

    @Test
    void maps_http_request_to_command_and_response() throws Exception {
        var aggregateId = UUID.randomUUID();
        var actorId = UUID.randomUUID();

        when(commandBus.dispatch(any()))
            .thenReturn(new {{ACTION}}{{AGGREGATE}}Result(aggregateId, "{{NEXT_STATUS}}"));

        mockMvc.perform(post("/api/{{RESOURCE_PATH}}/{aggregateId}/{{ACTION_PATH}}", aggregateId)
                .contentType("application/json")
                .content("""
                    {"actorId":"%s"}
                    """.formatted(actorId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aggregateId").value(aggregateId.toString()))
            .andExpect(jsonPath("$.status").value("{{NEXT_STATUS}}"));
    }
}

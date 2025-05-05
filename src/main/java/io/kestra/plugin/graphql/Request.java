package io.kestra.plugin.graphql;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.core.http.AbstractHttp;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Make a GraphQL API request."
)
@Plugin(
    examples = {
        @Example(
            title = "Make a GraphQL query with variables",
            full = true,
            code = """
                id: graphql_request
                namespace: company.team

                tasks:
                  - id: graphql_query
                    type: io.kestra.plugin.graphql.Request
                    uri: https://example.com/graphql
                    query: |
                      query GetUser($userId: ID!) {
                        user(id: $userId) {
                          name
                          email
                        }
                      }
                    variables:
                      userId: "12345"
                """
        ),
        @Example(
            title = "Execute a GraphQL query with authentication",
            full = true,
            code = """
                id: graphql_with_auth
                namespace: company.team

                tasks:
                  - id: get_data
                    type: io.kestra.plugin.graphql.Request
                    uri: https://example.com/graphql
                    headers:
                      Authorization: "Bearer {{ secret('API_TOKEN') }}"
                    query: |
                      query {
                        viewer {
                          name
                          email
                        }
                      }
                """
        ),
        @Example(
            title = "Execute a GraphQL query with operationName",
            full = true,
            code = """
                id: graphql_with_operation_name
                namespace: company.team

                tasks:
                  - id: get_data
                    type: io.kestra.plugin.graphql.Request
                    uri: https://example.com/graphql
                    query: |
                      query GetUser {
                        user(id: "1") {
                          name
                        }
                      }

                      query GetPosts {
                        posts {
                          title
                        }
                      }
                    operationName: "GetUser"
                """
        )
    }
)
public class Request extends AbstractHttp implements RunnableTask<Request.Output> {

    @Builder.Default
    @Schema(
        title = "If true, the GraphQL response body will be automatically encrypted and decrypted in the outputs.",
        description = "If this property is set to `true`, this task will output the request body using the `encryptedBody` output property; otherwise, the request body will be stored in the `body` output property."
    )
    private Property<Boolean> encryptBody = Property.of(false);

    @Schema(
        title = "GraphQL query or mutation to execute."
    )
    @NotNull
    private Property<String> query;

    @Schema(
        title = "Variables used in the query.",
        description = "GraphQL variables can be complex objects with nested structures."
    )
    private Property<Map<String, Object>> variables;

    @Schema(
        title = "Operation name for the GraphQL query",
        description = "Used when multiple operations are defined in the query document."
    )
    private Property<String> operationName;

    @Builder.Default
    protected Property<String> method = Property.of("POST");

    @Builder.Default
    @Schema(
        title = "Fail task on GraphQL errors",
        description = "If true, the task will fail when GraphQL returns errors in the response."
    )
    private Property<Boolean> failOnGraphQLErrors = Property.of(false);

    @Override
    protected HttpRequest request(RunContext runContext) throws IllegalVariableEvaluationException, URISyntaxException, IOException {

        String renderedUri = runContext.render(this.uri).as(String.class).map(s -> s.replace(" ", "%20")).orElseThrow();
        String methodName = runContext.render(this.method).as(String.class).orElse("POST");

        HttpRequest.HttpRequestBuilder requestBuilder = HttpRequest.builder()
            .method(methodName)
            .uri(URI.create(renderedUri));

        String renderedQuery = runContext.render(this.query).as(String.class).orElseThrow();

        Map<String, Object> requestPayload = new HashMap<>();
        requestPayload.put("query", renderedQuery);

        if (variables != null) {
            Object renderedVariables = runContext.render(variables).asMap(String.class, Object.class);
            requestPayload.put("variables", renderedVariables);
        }

        if (operationName != null) {
            String renderedOpName = runContext.render(operationName).as(String.class).orElse(null);
            if (renderedOpName != null && !renderedOpName.isEmpty()) {
                requestPayload.put("operationName", renderedOpName);
            }
        }

        requestBuilder.body(HttpRequest.JsonRequestBody.builder()
            .content(requestPayload)
            .charset(StandardCharsets.UTF_8)
            .build());

        var renderedHeader = runContext.render(this.headers).asMap(CharSequence.class, CharSequence.class);
        if (!renderedHeader.isEmpty()) {
            requestBuilder.headers(HttpHeaders.of(
                renderedHeader
                    .entrySet()
                    .stream()
                    .map(throwFunction(e -> new AbstractMap.SimpleEntry<>(
                            e.getKey().toString(),
                            runContext.render(e.getValue().toString())
                        ))
                    )
                    .collect(Collectors.groupingBy(AbstractMap.SimpleEntry::getKey, Collectors.mapping(AbstractMap.SimpleEntry::getValue, Collectors.toList()))),
                (a, b) -> true)
            );
        }

        return requestBuilder.build();
    }

    @Override
    public Output run(RunContext runContext) throws Exception {

        try (HttpClient client = this.client(runContext)) {
            HttpRequest request = request(runContext);

            HttpResponse<byte[]> response = client.request(request, byte[].class);

            String body = null;

            if (response.getBody() != null) {
                body = IOUtils.toString(ArrayUtils.toPrimitive(response.getBody()), StandardCharsets.UTF_8.name());
            }

            if (body != null) {
                OptionalInt illegalChar = body.chars().filter(c -> !Character.isDefined(c)).findFirst();
                if (illegalChar.isPresent()) {
                    throw new IllegalArgumentException("Illegal unicode code point in response body: " + illegalChar.getAsInt() +
                        ", the Request task only supports valid Unicode strings as the response body.");
                }
            }

            return this.output(runContext, request, response, body);
        }
    }

    @SuppressWarnings("unchecked")
    public Output output(RunContext runContext, HttpRequest request, HttpResponse<Byte[]> response, String body) throws Exception {
        boolean encrypt = runContext.render(this.encryptBody).as(Boolean.class).orElse(false);

        Object errors = null;
        Object data = null;
        Map<String, Object> extractedData = new HashMap<>();

        if (body != null && !body.isEmpty()) {
            Map<String, Object> jsonResponse = JacksonMapper.ofJson().readValue(body, Map.class);

            if (jsonResponse.containsKey("data")) {
                data = jsonResponse.get("data");
            }

            if (jsonResponse.containsKey("errors")) {
                errors = jsonResponse.get("errors");

                if (errors != null && runContext.render(failOnGraphQLErrors).as(Boolean.class).orElse(false)) {
                    throw new Exception("GraphQL query failed with errors: " + errors);
                }
            }
        }

        return Output.builder()
            .code(response.getStatus().getCode())
            .headers(response.getHeaders().map())
            .uri(request.getUri())
            .body(encrypt ? null : data)
            .encryptedBody(encrypt ? EncryptedString.from(body, runContext) : null)
            .error(errors)
            .build();
    }

    @Builder(toBuilder = true)
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "The URL of the current request.")
        private final URI uri;

        @Schema(title = "The status code of the response.")
        private final Integer code;

        @Schema(title = "The headers of the response.")
        private final Map<String, List<String>> headers;

        @Schema(
            title = "The GraphQL data field from the response.",
            description = "Contains the data returned by the GraphQL server. If the `encryptBody` property is set to `true`, this will be null and the data will be in `encryptedBody`."
        )
        private Object body;

        @Schema(
            title = "The GraphQL errors from the response.",
            description = "Contains any errors returned by the GraphQL server."
        )
        private Object error;

        @Schema(
            title = "The encrypted body of the response.",
            description = "If the `encryptBody` property is set to `true`, this will contain the encrypted response."
        )
        private EncryptedString encryptedBody;
    }
}
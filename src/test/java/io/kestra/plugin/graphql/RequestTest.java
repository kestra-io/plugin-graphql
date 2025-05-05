package io.kestra.plugin.graphql;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@KestraTest
@WireMockTest(httpPort = 28181)
class RequestTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .build();

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldExecuteBasicQuerySuccessfully() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/graphql"))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(containing("query GetUser"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"data\": { \"user\": { \"name\": \"admin\", \"email\": \"admin@example.com\" } } }")
            )
        );

        Request task = Request.builder()
            .uri(Property.of("http://localhost:" + wireMock.getPort() + "/graphql"))
            .query(Property.of("query GetUser($id: ID!) { user(id: $id) { name email } }"))
            .variables(Property.of(Map.of("id", "123")))
            .build();

        RunContext runContext = runContextFactory.of();
        Request.Output output = task.run(runContext);

        assertNotNull(output);
        assertEquals(200, output.getCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> userData = (Map<String, Object>) ((Map<String, Object>) output.getBody()).get("user");
        assertEquals("admin", userData.get("name"));
        assertEquals("admin@example.com", userData.get("email"));
    }

    @Test
    void shouldExecuteMutationSuccessfully() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/graphql"))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(containing("mutation CreateUser"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"data\": { \"createUser\": { \"id\": \"456\", \"name\": \"admin\" } } }")
            )
        );

        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "admin");
        vars.put("email", "admin@example.com");

        Request task = Request.builder()
            .uri(Property.of("http://localhost:" + wireMock.getPort() + "/graphql"))
            .query(Property.of("mutation CreateUser($name: String!, $email: String!) { createUser(name: $name, email: $email) { id name } }"))
            .variables(Property.of(vars))
            .build();

        RunContext runContext = runContextFactory.of();
        Request.Output output = task.run(runContext);

        assertNotNull(output);
        assertEquals(200, output.getCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> createUserData = (Map<String, Object>) ((Map<String, Object>) output.getBody()).get("createUser");
        assertEquals("456", createUserData.get("id"));
        assertEquals("admin", createUserData.get("name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteQueryWithSpecificOperationName() throws Exception {

        wireMock.stubFor(post(urlEqualTo("/graphql"))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(containing("operationName"))
            .withRequestBody(containing("GetUserDetails"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"data\": { \"user\": { \"id\": \"123\", \"details\": { \"age\": 30, \"location\": \"earth\" } } } }")
            )
        );

        Request task = Request.builder()
            .uri(Property.of("http://localhost:" + wireMock.getPort() + "/graphql"))
            .query(Property.of("query GetUserBasic { user(id: \"123\") { id name } } query GetUserDetails { user(id: \"123\") { id details { age location } } }"))
            .operationName(Property.of("GetUserDetails"))
            .build();

        RunContext runContext = runContextFactory.of();
        Request.Output output = task.run(runContext);

        assertNotNull(output);
        assertEquals(200, output.getCode());

        Map<String, Object> userData = (Map<String, Object>) ((Map<String, Object>) output.getBody()).get("user");
        assertEquals("123", userData.get("id"));

        Map<String, Object> details = (Map<String, Object>) userData.get("details");
        assertEquals(30, details.get("age"));
        assertEquals("earth", details.get("location"));
    }

    @Test
    void shouldHandleGraphQLErrorsBasedOnConfiguration() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/graphql"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"data\": { \"users\": [{ \"name\": \"admin\" }, null] }, \"errors\": [{ \"message\": \"User with ID 2 not found\", \"path\": [\"users\", 1] }] }")
            )
        );

        Request task = Request.builder()
            .uri(Property.of("http://localhost:" + wireMock.getPort() + "/graphql"))
            .query(Property.of("query { users { name } }"))
            .failOnGraphQLErrors(Property.of(false))
            .build();

        RunContext runContext = runContextFactory.of();
        Request.Output output = task.run(runContext);

        assertNotNull(output);
        assertEquals(200, output.getCode());
        assertNotNull(output.getBody());
        assertNotNull(output.getError());

        Request failingTask = Request.builder()
            .uri(Property.of("http://localhost:" + wireMock.getPort() + "/graphql"))
            .query(Property.of("query { users { name } }"))
            .failOnGraphQLErrors(Property.of(true))
            .build();

        RunContext failingRunContext = runContextFactory.of();
        Exception exception = assertThrows(Exception.class, () -> failingTask.run(failingRunContext));
        assertTrue(exception.getMessage().contains("GraphQL query failed with errors"));
    }

    @Test
    void shouldHandleComplexNestedVariables() throws Exception {

        wireMock.stubFor(post(urlEqualTo("/graphql"))
            .withRequestBody(matchingJsonPath("$.variables.input.address.street", equalTo("some street")))
            .withRequestBody(matchingJsonPath("$.variables.input.address.city", equalTo("some city")))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"data\": { \"createUser\": { \"id\": \"789\", \"name\": \"Admin User\" } } }")
            )
        );

        Map<String, Object> address = new HashMap<>();
        address.put("street", "some street");
        address.put("city", "some city");

        Map<String, Object> userInput = new HashMap<>();
        userInput.put("name", "Admin User");
        userInput.put("address", address);

        Map<String, Object> variables = new HashMap<>();
        variables.put("input", userInput);


        Request task = Request.builder()
            .uri(Property.of("http://localhost:" + wireMock.getPort() + "/graphql"))
            .query(Property.of("mutation CreateUserWithAddress($input: UserInput!) { createUser(input: $input) { id name } }"))
            .variables(Property.of(variables))
            .build();

        RunContext runContext = runContextFactory.of();
        Request.Output output = task.run(runContext);

        assertNotNull(output);
        assertEquals(200, output.getCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> createUserData = (Map<String, Object>) ((Map<String, Object>) output.getBody()).get("createUser");
        assertEquals("789", createUserData.get("id"));
        assertEquals("Admin User", createUserData.get("name"));
    }

    @Test
    void shouldEncryptRequestBodyWhenEnabled() throws Exception {

        wireMock.stubFor(post(urlEqualTo("/graphql"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"data\": { \"sensitiveData\": \"secret information\" } }")
            )
        );

        Request task = Request.builder()
            .uri(Property.of("http://localhost:" + wireMock.getPort() + "/graphql"))
            .query(Property.of("query { sensitiveData }"))
            .encryptBody(Property.of(true))
            .build();

        RunContext runContext = runContextFactory.of(Map.of());
        Request.Output output = task.run(runContext);

        assertNotNull(output);
        assertEquals(200, output.getCode());
        assertNull(output.getBody()); // body should be null when encrypted
        assertNotNull(output.getEncryptedBody());
    }
}
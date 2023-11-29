/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graviteesource.policy;

import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.graviteesource.entrypoint.http.get.HttpGetEntrypointConnectorFactory;
import com.graviteesource.entrypoint.http.post.HttpPostEntrypointConnectorFactory;
import com.graviteesource.reactor.message.MessageApiReactorFactory;
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.fakes.MessageStorage;
import io.gravitee.apim.gateway.tests.sdk.connector.fakes.PersistentMockEndpointConnectorFactory;
import io.gravitee.apim.gateway.tests.sdk.reactor.ReactorBuilder;
import io.gravitee.apim.plugin.reactor.ReactorPlugin;
import io.gravitee.common.http.MediaType;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.reactive.reactor.v4.reactor.ReactorFactory;
import io.gravitee.plugin.endpoint.EndpointConnectorPlugin;
import io.gravitee.plugin.endpoint.mock.MockEndpointConnectorFactory;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.policy.xml2json.XmlToJsonTransformationPolicy;
import io.gravitee.policy.xml2json.configuration.XmlToJsonTransformationPolicyConfiguration;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClient;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
public class XmlToJsonTransformationPolicyV4MessageIntegrationTest {

    @Nested
    @GatewayTest
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class Subscribe extends AbstractPolicyTest<XmlToJsonTransformationPolicy, XmlToJsonTransformationPolicyConfiguration> {

        @Override
        public void configureReactors(Set<ReactorPlugin<? extends ReactorFactory<?>>> reactors) {
            reactors.add(ReactorBuilder.build(MessageApiReactorFactory.class));
        }

        @Override
        public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
            entrypoints.putIfAbsent("http-get", EntrypointBuilder.build("http-get", HttpGetEntrypointConnectorFactory.class));
        }

        @Override
        public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
            endpoints.putIfAbsent("mock", EndpointBuilder.build("mock", MockEndpointConnectorFactory.class));
        }

        @Test
        @DeployApi("/apis/v4/api-subscribe.json")
        void should_transform_on_response_message(HttpClient client) {
            var expected = loadResource("/io/gravitee/policy/xml2json/expected.json");

            client
                .rxRequest(GET, "/test")
                .flatMap(request -> request.putHeader(HttpHeaderNames.ACCEPT, MediaType.APPLICATION_JSON).rxSend())
                .flatMap(response -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    return response.body();
                })
                .map(Buffer::toString)
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertValue(body -> {
                    final JsonObject content = new JsonObject(body);
                    final JsonArray items = content.getJsonArray("items");
                    assertThat(items).hasSize(2);
                    items.forEach(item -> {
                        JsonObject message = (JsonObject) item;
                        assertThatJson(message.getString("content")).isEqualTo(expected);
                        final JsonObject headers = message.getJsonObject("headers");
                        assertThat(headers.getJsonArray("Content-Type")).hasSize(1).contains("application/json;charset=UTF-8");
                        assertThat(headers.getJsonArray("Content-Length")).hasSize(1).contains("65");
                    });
                    return true;
                });
        }

        @Test
        @DeployApi("/apis/v4/api-subscribe-invalid-xml.json")
        void should_respond_with_500_when_invalid_xml_from_backend(HttpClient client) {
            client
                .rxRequest(GET, "/test")
                .flatMap(request -> request.putHeader(HttpHeaderNames.ACCEPT, MediaType.APPLICATION_JSON).rxSend())
                .flatMap(response -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    return response.body();
                })
                .map(Buffer::toString)
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertValue(body -> {
                    final JsonObject content = new JsonObject(body);
                    final JsonArray items = content.getJsonArray("items");
                    assertThat(items).isEmpty();
                    final JsonObject error = content.getJsonObject("error");
                    final JsonObject errorContent = new JsonObject(error.getString("content"));
                    assertThat(errorContent.getString("message")).isEqualTo("Unable to transform invalid XML message to JSON");
                    assertThat(errorContent.getString("http_status_code")).isEqualTo("500");
                    final JsonObject headers = error.getJsonObject("headers");
                    assertThat(headers.getJsonArray("Content-Type")).hasSize(1).contains("application/json");
                    assertThat(headers.getJsonArray("Content-Length")).hasSize(1).contains("84");
                    final JsonObject metadata = error.getJsonObject("metadata");
                    assertThat(metadata.getString("reason")).isEqualTo("Internal Server Error");
                    assertThat(metadata.getString("key")).isEqualTo("XML_INVALID_MESSAGE_PAYLOAD");
                    assertThat(metadata.getInteger("statusCode")).isEqualTo(500);

                    return true;
                });
        }
    }

    @Nested
    @GatewayTest
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class Publish extends AbstractPolicyTest<XmlToJsonTransformationPolicy, XmlToJsonTransformationPolicyConfiguration> {

        private MessageStorage messageStorage;

        @Override
        public void configureReactors(Set<ReactorPlugin<? extends ReactorFactory<?>>> reactors) {
            reactors.add(ReactorBuilder.build(MessageApiReactorFactory.class));
        }

        @Override
        public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
            entrypoints.putIfAbsent("http-post", EntrypointBuilder.build("http-post", HttpPostEntrypointConnectorFactory.class));
        }

        @Override
        public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
            endpoints.putIfAbsent("mock", EndpointBuilder.build("mock", PersistentMockEndpointConnectorFactory.class));
        }

        @BeforeEach
        void setUp() {
            messageStorage = getBean(MessageStorage.class);
        }

        @AfterEach
        void tearDown() {
            messageStorage.reset();
        }

        @Test
        @DeployApi("/apis/v4/api-publish.json")
        void should_transform_on_request_message(HttpClient client) {
            var expected = loadResource("/io/gravitee/policy/xml2json/expected.json");
            var messageContent = loadResource("/io/gravitee/policy/xml2json/input.xml");

            client
                .rxRequest(POST, "/test")
                .flatMap(request -> request.rxSend(messageContent))
                .flatMap(response -> {
                    assertThat(response.statusCode()).isEqualTo(202);
                    return response.body();
                })
                .test()
                .awaitDone(5, TimeUnit.SECONDS);

            messageStorage
                .subject()
                .test()
                .assertValue(message -> {
                    assertThatJson(message.content().toString()).isEqualTo(expected);
                    return true;
                })
                .dispose();
        }

        @Test
        @DeployApi("/apis/v4/api-publish.json")
        void should_respond_with_500_when_posting_invalid_xml(HttpClient client) {
            var param = loadResource("/io/gravitee/policy/xml2json/invalid-input.xml");

            client
                .rxRequest(POST, "/test")
                .flatMap(request -> request.rxSend(param))
                .flatMap(response -> {
                    assertThat(response.statusCode()).isEqualTo(500);
                    return response.body();
                })
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertValue(buffer -> {
                    assertThat(buffer).hasToString("Internal Server Error");
                    return true;
                });

            messageStorage.subject().test().assertNoValues().dispose();
        }
    }

    protected String loadResource(String resource) {
        try (InputStream is = this.getClass().getResourceAsStream(resource)) {
            return new String(Objects.requireNonNull(is).readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}

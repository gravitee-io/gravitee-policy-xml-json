/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.xml2json;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.policy.xml2json.configuration.XmlToJsonTransformationPolicyConfiguration;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
@GatewayTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class XmlToJsonTransformationPolicyIntegrationTest
    extends AbstractPolicyTest<XmlToJsonTransformationPolicy, XmlToJsonTransformationPolicyConfiguration> {

    @Test
    @DeployApi("/apis/v2/api-pre.json")
    void should_post_xml_content_to_backend(HttpClient client) throws InterruptedException {
        final String input = loadResource("/io/gravitee/policy/xml2json/input.xml");
        final String expected = loadResource("/io/gravitee/policy/xml2json/expected.json");

        wiremock.stubFor(post("/team").willReturn(ok("fefze")));

        client
            .rxRequest(POST, "/test")
            .flatMap(request -> request.rxSend(Buffer.buffer(input)))
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                return response.toFlowable();
            })
            .test()
            .await()
            .assertComplete()
            .assertNoErrors();

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/team")).withRequestBody(new EqualToPattern(expected)));
    }

    @Test
    @DeployApi("/apis/v2/api-pre.json")
    void should_return_bad_request_when_posting_invalid_json_to_gateway(HttpClient client) throws InterruptedException {
        final String input = loadResource("/io/gravitee/policy/xml2json/invalid-input.xml");
        client
            .rxRequest(POST, "/test")
            .flatMap(request -> request.rxSend(Buffer.buffer(input)))
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(400);
                return response.toFlowable();
            })
            .test()
            .await()
            .assertComplete()
            .assertNoErrors();
    }

    @Test
    @DeployApi("/apis/v2/api-post.json")
    void should_get_json_content_from_backend(HttpClient client) throws InterruptedException {
        final String expected = loadResource("/io/gravitee/policy/xml2json/expected.json");
        final String backendResponse = loadResource("/io/gravitee/policy/xml2json/input.xml");
        wiremock.stubFor(get("/team").willReturn(ok(backendResponse)));

        client
            .rxRequest(GET, "/test")
            .flatMap(HttpClientRequest::rxSend)
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                return response.toFlowable();
            })
            .test()
            .await()
            .assertValue(buffer -> {
                assertThat(buffer).hasToString(expected);
                return true;
            })
            .assertComplete()
            .assertNoErrors();

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/team")));
    }

    @Test
    @DeployApi("/apis/v2/api-post.json")
    void should_return_internal_error_when_getting_invalid_xml_content_from_backend(HttpClient client) throws InterruptedException {
        final String backendResponse = loadResource("/io/gravitee/policy/xml2json/invalid-input.xml");
        wiremock.stubFor(get("/team").willReturn(ok(backendResponse)));

        client
            .rxRequest(GET, "/test")
            .flatMap(HttpClientRequest::rxSend)
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(500);
                return response.toFlowable();
            })
            .test()
            .await()
            .assertComplete()
            .assertNoErrors();

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/team")));
    }

    @Test
    @DeployApi("/apis/v2/api-pre.json")
    void should_return_internal_error_when_too_many_nested(HttpClient client) throws InterruptedException {
        final String input = loadResource("/io/gravitee/policy/xml2json/invalid-nested-object.xml");
        client
            .rxRequest(POST, "/test")
            .flatMap(request -> request.rxSend(Buffer.buffer(input)))
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(400);
                return response.toFlowable();
            })
            .test()
            .await()
            .assertComplete()
            .assertNoErrors();
    }

    protected String loadResource(String resource) {
        try (InputStream is = this.getClass().getResourceAsStream(resource)) {
            return new String(Objects.requireNonNull(is).readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}

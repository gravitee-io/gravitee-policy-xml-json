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
package io.gravitee.policy.v3.xml2json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.node.api.configuration.Configuration;
import io.gravitee.node.api.configuration.Configuration;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.xml2json.XmlToJsonTransformationPolicy;
import io.gravitee.policy.xml2json.configuration.PolicyScope;
import io.gravitee.policy.xml2json.configuration.XmlToJsonTransformationPolicyConfiguration;
import io.gravitee.reporter.api.http.Metrics;
import io.gravitee.reporter.api.http.Metrics;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.Instant;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class XmlToJsonTransformationPolicyV3Test {

    private XmlToJsonTransformationPolicyV3 cut;

    @Mock
    private XmlToJsonTransformationPolicyConfiguration configuration;

    @Mock
    private PolicyChain policyChain;

    @Spy
    private Request request;

    @Spy
    private Response response;

    @Mock
    private ExecutionContext executionContext;

    @BeforeEach
    public void setUp() {
        cut = new XmlToJsonTransformationPolicyV3(configuration);
        final Configuration config = mock(Configuration.class);
        when(
            config.getProperty(
                XmlToJsonTransformationPolicy.POLICY_XML_JSON_MAXDEPTH,
                Integer.class,
                XmlToJsonTransformationPolicy.DEFAULT_MAX_DEPH
            )
        )
            .thenReturn(XmlToJsonTransformationPolicy.DEFAULT_MAX_DEPH);
        when(executionContext.getComponent(Configuration.class)).thenReturn(config);
    }

    @Test
    @DisplayName("Should transform and add header OnRequestContent")
    void shouldTransformAndAddHeadersOnRequestContent() throws Exception {
        String input = loadResource("/io/gravitee/policy/xml2json/input.xml");
        String expected = loadResource("/io/gravitee/policy/xml2json/expected.json");

        // Prepare context
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST);
        when(request.headers()).thenReturn(HttpHeaders.create());

        final ReadWriteStream result = cut.onRequestContent(request, policyChain, executionContext);
        assertThat(result).isNotNull();
        result.bodyHandler(resultBody -> {
            assertResultingJsonObjectsAreEquals(expected, resultBody);
        });

        result.write(Buffer.buffer(input));
        result.end();

        assertThat(request.headers().names()).contains(HttpHeaderNames.CONTENT_TYPE);
        assertThat(request.headers().getAll(HttpHeaderNames.CONTENT_TYPE).get(0)).isEqualTo(XmlToJsonTransformationPolicy.APPLICATION_JSON);
        assertThat(request.headers().names()).doesNotContain(HttpHeaderNames.TRANSFER_ENCODING);
        assertThat(request.headers().names()).contains(HttpHeaderNames.CONTENT_LENGTH);
    }

    @Test
    @DisplayName("Should not transform when TransformationException thrown OnRequestContent")
    void shouldNotTransformAndAddHeadersOnRequestContent() throws Exception {
        String input = loadResource("/io/gravitee/policy/xml2json/invalid-input.xml");

        // Prepare context
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST);
        when(request.headers()).thenReturn(HttpHeaders.create());
        when(request.metrics()).thenReturn(Metrics.on(Instant.now().toEpochMilli()).build());

        final ReadWriteStream result = cut.onRequestContent(request, policyChain, executionContext);
        assertThat(result).isNotNull();

        result.write(Buffer.buffer(input));
        result.end();

        assertThat(request.headers().names()).doesNotContain(HttpHeaderNames.CONTENT_TYPE);
        assertThat(request.headers().names()).doesNotContain(HttpHeaderNames.TRANSFER_ENCODING);
        assertThat(request.headers().names()).doesNotContain(HttpHeaderNames.CONTENT_LENGTH);
        assertThat(request.metrics().getMessage()).contains("Unable to transform XML into JSON:");
        verify(policyChain, times(1)).streamFailWith(any());
    }

    @Test
    @DisplayName("Should reject too deep nested objects")
    void shouldRejectTooDeepNestedObject() throws Exception {
        String input = loadResource("/io/gravitee/policy/xml2json/invalid-nested-object.xml");

        // Prepare context
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST);
        when(request.headers()).thenReturn(HttpHeaders.create());
        when(request.metrics()).thenReturn(Metrics.on(Instant.now().toEpochMilli()).build());

        final ReadWriteStream result = cut.onRequestContent(request, policyChain, executionContext);
        assertThat(result).isNotNull();

        result.write(Buffer.buffer(input));
        result.end();

        assertThat(request.headers().names()).doesNotContain(HttpHeaderNames.CONTENT_TYPE);
        assertThat(request.headers().names()).doesNotContain(HttpHeaderNames.TRANSFER_ENCODING);
        assertThat(request.headers().names()).doesNotContain(HttpHeaderNames.CONTENT_LENGTH);
        assertThat(request.metrics().getMessage()).contains("Unable to transform XML into JSON:");
        verify(policyChain, times(1)).streamFailWith(any());
    }

    @Test
    @DisplayName("Should transform and add header OnResponseContent")
    void shouldTransformAndAddHeadersOnResponseContent() throws Exception {
        String input = loadResource("/io/gravitee/policy/xml2json/input.xml");
        String expected = loadResource("/io/gravitee/policy/xml2json/expected.json");

        // Prepare context
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE);
        when(response.headers()).thenReturn(HttpHeaders.create());

        final ReadWriteStream result = cut.onResponseContent(response, policyChain, executionContext);
        assertThat(result).isNotNull();
        result.bodyHandler(resultBody -> {
            assertResultingJsonObjectsAreEquals(expected, resultBody);
        });

        result.write(Buffer.buffer(input));
        result.end();

        assertThat(response.headers().names()).contains(HttpHeaderNames.CONTENT_TYPE);
        assertThat(response.headers().getAll(HttpHeaderNames.CONTENT_TYPE).get(0))
            .isEqualTo(XmlToJsonTransformationPolicy.APPLICATION_JSON);
        assertThat(response.headers().names()).doesNotContain(HttpHeaderNames.TRANSFER_ENCODING);
        assertThat(response.headers().names()).contains(HttpHeaderNames.CONTENT_LENGTH);
    }

    @Test
    @DisplayName("Should not transform when TransformationException thrown OnResponseContent")
    void shouldNotTransformAndAddHeadersOnResponseContent() throws Exception {
        String input = loadResource("/io/gravitee/policy/xml2json/invalid-input.xml");

        // Prepare context
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE);
        when(response.headers()).thenReturn(HttpHeaders.create());

        final ReadWriteStream result = cut.onResponseContent(response, policyChain, executionContext);
        assertThat(result).isNotNull();

        result.write(Buffer.buffer(input));
        result.end();

        assertThat(response.headers().names()).doesNotContain(HttpHeaderNames.CONTENT_TYPE);
        assertThat(response.headers().names()).doesNotContain(HttpHeaderNames.TRANSFER_ENCODING);
        assertThat(response.headers().names()).doesNotContain(HttpHeaderNames.CONTENT_LENGTH);
        verify(policyChain, times(1)).streamFailWith(any());
    }

    @ParameterizedTest
    @DisplayName("Should not fail when invalid charset is specified on request content")
    @ValueSource(
        strings = {
            "wrong",
            "application/soap+xml; charset=utf-8;",
            "  ",
            "application/soap+xml; charset=utf-8,",
            "application/soap+xml; charset=utf-8",
        }
    )
    void shouldNotFailWhenInvalidCharsetIsSpecifiedOnRequestContent(String contentType) throws Exception {
        String input = loadResource("/io/gravitee/policy/xml2json/input.xml");
        String expected = loadResource("/io/gravitee/policy/xml2json/expected.json");

        // Prepare context
        final HttpHeaders headers = HttpHeaders.create();
        headers.set(HttpHeaderNames.CONTENT_TYPE, contentType);
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST);
        when(request.headers()).thenReturn(headers);

        final ReadWriteStream result = cut.onRequestContent(request, policyChain, executionContext);
        assertThat(result).isNotNull();
        result.bodyHandler(resultBody -> assertResultingJsonObjectsAreEquals(expected, resultBody));

        result.write(Buffer.buffer(input));
        result.end();

        assertThat(request.headers().names()).contains(HttpHeaderNames.CONTENT_TYPE);
        assertThat(request.headers().getAll(HttpHeaderNames.CONTENT_TYPE).get(0)).isEqualTo(XmlToJsonTransformationPolicy.APPLICATION_JSON);
        assertThat(request.headers().names()).doesNotContain(HttpHeaderNames.TRANSFER_ENCODING);
        assertThat(request.headers().names()).contains(HttpHeaderNames.CONTENT_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "wrong",
            "application/soap+xml; charset=utf-8;",
            "  ",
            "application/soap+xml; charset=utf-8,",
            "application/soap+xml; charset=utf-8",
        }
    )
    @DisplayName("Should not fail when invalid charset is specified on response content")
    void shouldNotFailWhenInvalidCharsetIsSpecifiedOnResponseContent(String contentType) throws Exception {
        String input = loadResource("/io/gravitee/policy/xml2json/input.xml");
        String expected = loadResource("/io/gravitee/policy/xml2json/expected.json");

        // Prepare context
        final HttpHeaders headers = HttpHeaders.create();
        headers.set(HttpHeaderNames.CONTENT_TYPE, contentType);
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE);
        when(response.headers()).thenReturn(headers);

        final ReadWriteStream result = cut.onResponseContent(response, policyChain, executionContext);
        assertThat(result).isNotNull();
        result.bodyHandler(resultBody -> assertResultingJsonObjectsAreEquals(expected, resultBody));

        result.write(Buffer.buffer(input));
        result.end();

        assertThat(response.headers().names()).contains(HttpHeaderNames.CONTENT_TYPE);
        assertThat(response.headers().getAll(HttpHeaderNames.CONTENT_TYPE).get(0))
            .isEqualTo(XmlToJsonTransformationPolicy.APPLICATION_JSON);
        assertThat(response.headers().names()).doesNotContain(HttpHeaderNames.TRANSFER_ENCODING);
        assertThat(response.headers().names()).contains(HttpHeaderNames.CONTENT_LENGTH);
    }

    private void assertResultingJsonObjectsAreEquals(String expected, Object resultBody) {
        assertThat(resultBody).hasToString(expected);
    }

    private String loadResource(String resource) throws IOException {
        InputStream is = this.getClass().getResourceAsStream(resource);
        StringWriter sw = new StringWriter();
        IOUtils.copy(is, sw, "UTF-8");
        return sw.toString();
    }
}

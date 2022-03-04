package io.gravitee.policy.xml2json;

import io.gravitee.common.http.HttpHeaders;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.xml2json.configuration.PolicyScope;
import io.gravitee.policy.xml2json.configuration.XmlToJsonTransformationPolicyConfiguration;
import io.gravitee.reporter.api.http.Metrics;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class XmlToJsonTransformationPolicyTest {

    private XmlToJsonTransformationPolicy cut;

    @Mock
    private XmlToJsonTransformationPolicyConfiguration configuration;

    @Mock
    private PolicyChain policyChain;

    @Spy
    private Request request;

    @Spy
    private Response response;

    @BeforeEach
    public void setUp() {
        cut = new XmlToJsonTransformationPolicy(configuration);
    }

    @Test
    @DisplayName("Should transform and add header OnRequestContent")
    public void shouldTransformAndAddHeadersOnRequestContent() throws Exception {
        String input = loadResource("/io/gravitee/policy/xml2json/input.xml");
        String expected = loadResource("/io/gravitee/policy/xml2json/expected.json");

        // Prepare context
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST);
        when(request.headers()).thenReturn(new HttpHeaders());

        final ReadWriteStream result = cut.onRequestContent(request, policyChain);
        assertThat(result).isNotNull();
        result.bodyHandler(resultBody -> {
            assertResultingJsonObjectsAreEquals(expected, resultBody);
        });

        result.write(Buffer.buffer(input));
        result.end();

        assertThat(request.headers()).containsKey(HttpHeaders.CONTENT_TYPE);
        assertThat(request.headers().get(HttpHeaders.CONTENT_TYPE).get(0)).isEqualTo(XmlToJsonTransformationPolicy.APPLICATION_JSON);
        assertThat(request.headers()).doesNotContainKey(HttpHeaders.TRANSFER_ENCODING);
        assertThat(request.headers()).containsKey(HttpHeaders.CONTENT_LENGTH);
    }

    @Test
    @DisplayName("Should not transform when TransformationException thrown OnRequestContent")
    public void shouldNotTransformAndAddHeadersOnRequestContent() throws Exception {
        String input = loadResource("/io/gravitee/policy/xml2json/invalid-input.xml");

        // Prepare context
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST);
        when(request.headers()).thenReturn(new HttpHeaders());
        when(request.metrics()).thenReturn(Metrics.on(Instant.now().toEpochMilli()).build());

        final ReadWriteStream result = cut.onRequestContent(request, policyChain);
        assertThat(result).isNotNull();

        result.write(Buffer.buffer(input));
        result.end();

        assertThat(request.headers()).doesNotContainKey(HttpHeaders.CONTENT_TYPE);
        assertThat(request.headers()).doesNotContainKey(HttpHeaders.TRANSFER_ENCODING);
        assertThat(request.headers()).doesNotContainKey(HttpHeaders.CONTENT_LENGTH);
        assertThat(request.metrics().getMessage()).contains("Unable to transform XML into JSON:");
        verify(policyChain, times(1)).streamFailWith(any());
    }

    @Test
    @DisplayName("Should transform and add header OnResponseContent")
    public void shouldTransformAndAddHeadersOnResponseContent() throws Exception {
        String input = loadResource("/io/gravitee/policy/xml2json/input.xml");
        String expected = loadResource("/io/gravitee/policy/xml2json/expected.json");

        // Prepare context
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE);
        when(response.headers()).thenReturn(new HttpHeaders());

        final ReadWriteStream result = cut.onResponseContent(response, policyChain);
        assertThat(result).isNotNull();
        result.bodyHandler(resultBody -> {
            assertResultingJsonObjectsAreEquals(expected, resultBody);
        });

        result.write(Buffer.buffer(input));
        result.end();

        assertThat(response.headers()).containsKey(HttpHeaders.CONTENT_TYPE);
        assertThat(response.headers().get(HttpHeaders.CONTENT_TYPE).get(0)).isEqualTo(XmlToJsonTransformationPolicy.APPLICATION_JSON);
        assertThat(response.headers()).doesNotContainKey(HttpHeaders.TRANSFER_ENCODING);
        assertThat(response.headers()).containsKey(HttpHeaders.CONTENT_LENGTH);
    }

    @Test
    @DisplayName("Should not transform when TransformationException thrown OnResponseContent")
    public void shouldNotTransformAndAddHeadersOnResponseContent() throws Exception {
        String input = loadResource("/io/gravitee/policy/xml2json/invalid-input.xml");

        // Prepare context
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE);
        when(response.headers()).thenReturn(new HttpHeaders());

        final ReadWriteStream result = cut.onResponseContent(response, policyChain);
        assertThat(result).isNotNull();

        result.write(Buffer.buffer(input));
        result.end();

        assertThat(response.headers()).doesNotContainKey(HttpHeaders.CONTENT_TYPE);
        assertThat(response.headers()).doesNotContainKey(HttpHeaders.TRANSFER_ENCODING);
        assertThat(response.headers()).doesNotContainKey(HttpHeaders.CONTENT_LENGTH);
        verify(policyChain, times(1)).streamFailWith(any());
    }

    private void assertResultingJsonObjectsAreEquals(String expected, Object resultBody) {
        assertThat(resultBody.toString()).isEqualTo(expected);
    }

    private String loadResource(String resource) throws IOException {
        InputStream is = this.getClass().getResourceAsStream(resource);
        StringWriter sw = new StringWriter();
        IOUtils.copy(is, sw, "UTF-8");
        return sw.toString();
    }
}
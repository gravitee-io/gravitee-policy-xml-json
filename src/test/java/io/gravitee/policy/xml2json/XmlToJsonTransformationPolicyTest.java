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
package io.gravitee.policy.xml2json;

import static io.gravitee.common.http.HttpStatusCode.BAD_REQUEST_400;
import static io.gravitee.common.http.HttpStatusCode.INTERNAL_SERVER_ERROR_500;
import static io.gravitee.policy.v3.xml2json.XmlToJsonTransformationPolicyV3.DEFAULT_MAX_DEPH;
import static io.gravitee.policy.v3.xml2json.XmlToJsonTransformationPolicyV3.POLICY_XML_JSON_MAXDEPTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.ExecutionContext;
import io.gravitee.gateway.reactive.api.context.Request;
import io.gravitee.gateway.reactive.api.context.Response;
import io.gravitee.gateway.reactive.api.message.DefaultMessage;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.core.context.interruption.InterruptionFailureException;
import io.gravitee.node.api.configuration.Configuration;
import io.gravitee.policy.xml2json.configuration.XmlToJsonTransformationPolicyConfiguration;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.MaybeTransformer;
import io.reactivex.rxjava3.observers.TestObserver;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.function.Function;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private ExecutionContext ctx;

    @Mock
    private Request request;

    @Mock
    private Response response;

    @Mock
    private Configuration mockConfiguration;

    @Captor
    private ArgumentCaptor<MaybeTransformer<Buffer, Buffer>> onBodyCaptor;

    @Captor
    private ArgumentCaptor<Function<Message, Maybe<Message>>> onMessageCaptor;

    @BeforeEach
    public void setUp() {
        cut = new XmlToJsonTransformationPolicy(configuration);
        lenient().when(ctx.request()).thenReturn(request);
        lenient().when(ctx.response()).thenReturn(response);
        lenient()
            .when(mockConfiguration.getProperty(POLICY_XML_JSON_MAXDEPTH, Integer.class, DEFAULT_MAX_DEPH))
            .thenReturn(DEFAULT_MAX_DEPH);
        lenient().when(ctx.getComponent(Configuration.class)).thenReturn(mockConfiguration);

        lenient().when(request.headers()).thenReturn(HttpHeaders.create());
        lenient().when(response.headers()).thenReturn(HttpHeaders.create());
        lenient()
            .when(ctx.interruptBodyWith(any(ExecutionFailure.class)))
            .thenAnswer(invocation -> Maybe.error(new InterruptionFailureException(invocation.getArgument(0))));
        lenient()
            .when(ctx.interruptMessageWith(any(ExecutionFailure.class)))
            .thenAnswer(invocation -> Maybe.error(new InterruptionFailureException(invocation.getArgument(0))));
    }

    @Test
    @DisplayName("Should transform and add header OnRequest")
    void shouldTransformAndAddHeadersOnRequest() throws Exception {
        final String input = loadResource("/io/gravitee/policy/xml2json/input.xml");
        final String expected = loadResource("/io/gravitee/policy/xml2json/expected.json");
        final HttpHeaders headers = HttpHeaders.create();

        when(request.onBody(onBodyCaptor.capture())).thenReturn(Completable.complete());
        when(request.headers()).thenReturn(headers);

        final TestObserver<Void> obs = cut.onRequest(ctx).test();
        obs.assertNoValues();

        final TestObserver<Buffer> bodyObs = ((Maybe<Buffer>) onBodyCaptor.getValue().apply(Maybe.just(Buffer.buffer(input)))).test();

        bodyObs.assertValue(buffer -> expected.equals(buffer.toString()));
        verifyHeaders(request.headers());
    }

    @Test
    @DisplayName("Should do nothing when no body OnRequest")
    void shouldDoNothingWhenNoBodyOnRequest() {
        when(request.onBody(onBodyCaptor.capture())).thenReturn(Completable.complete());

        final TestObserver<Void> obs = cut.onRequest(ctx).test();
        obs.assertNoValues();

        final TestObserver<Buffer> bodyObs = ((Maybe<Buffer>) onBodyCaptor.getValue().apply(Maybe.empty())).test();

        bodyObs.assertNoValues();
    }

    @Test
    @DisplayName("Should interrupt with failure when invalid json OnRequest")
    void shouldInterruptWhenInvalidJsonOnRequest() throws IOException {
        final String invalidInput = loadResource("/io/gravitee/policy/xml2json/invalid-input.xml");
        when(request.onBody(onBodyCaptor.capture())).thenReturn(Completable.complete());
        when(request.headers()).thenReturn(HttpHeaders.create());

        final TestObserver<Void> obs = cut.onRequest(ctx).test();
        obs.assertNoValues();

        ((Maybe<Buffer>) onBodyCaptor.getValue().apply(Maybe.just(Buffer.buffer(invalidInput)))).test()
            .assertError(throwable -> {
                assertThat(throwable).isInstanceOf(InterruptionFailureException.class);
                InterruptionFailureException failureException = (InterruptionFailureException) throwable;
                ExecutionFailure executionFailure = failureException.getExecutionFailure();
                assertThat(executionFailure).isNotNull();
                assertThat(executionFailure.key()).isEqualTo("XML_INVALID_PAYLOAD");
                assertThat(executionFailure.statusCode()).isEqualTo(BAD_REQUEST_400);
                assertThat(executionFailure.message()).isNotNull();

                return true;
            });
    }

    @Test
    @DisplayName("Should interrupt with failure when max nested object reach OnRequest")
    void shouldInterruptWhenMaxNestedObjectOnRequest() throws IOException {
        final String invalidInput = loadResource("/io/gravitee/policy/xml2json/invalid-nested-object.xml");
        when(request.onBody(onBodyCaptor.capture())).thenReturn(Completable.complete());
        when(request.headers()).thenReturn(HttpHeaders.create());

        final TestObserver<Void> obs = cut.onRequest(ctx).test();
        obs.assertNoValues();

        ((Maybe<Buffer>) onBodyCaptor.getValue().apply(Maybe.just(Buffer.buffer(invalidInput)))).test()
            .assertError(throwable -> {
                assertThat(throwable).isInstanceOf(InterruptionFailureException.class);
                InterruptionFailureException failureException = (InterruptionFailureException) throwable;
                ExecutionFailure executionFailure = failureException.getExecutionFailure();
                assertThat(executionFailure).isNotNull();
                assertThat(executionFailure.key()).isEqualTo("XML_INVALID_PAYLOAD");
                assertThat(executionFailure.statusCode()).isEqualTo(BAD_REQUEST_400);
                assertThat(executionFailure.message()).isNotNull();

                return true;
            });
    }

    @Test
    @DisplayName("Should transform and add header OnResponse")
    void shouldTransformAndAddHeadersOnResponse() throws Exception {
        final String input = loadResource("/io/gravitee/policy/xml2json/input.xml");
        final String expected = loadResource("/io/gravitee/policy/xml2json/expected.json");
        final HttpHeaders headers = HttpHeaders.create();

        when(response.onBody(onBodyCaptor.capture())).thenReturn(Completable.complete());
        when(response.headers()).thenReturn(headers);

        final TestObserver<Void> obs = cut.onResponse(ctx).test();
        obs.assertNoValues();

        final TestObserver<Buffer> bodyObs = ((Maybe<Buffer>) onBodyCaptor.getValue().apply(Maybe.just(Buffer.buffer(input)))).test();

        bodyObs.assertValue(buffer -> expected.equals(buffer.toString()));
        verifyHeaders(headers);
    }

    @Test
    @DisplayName("Should do nothing when no body OnResponse")
    void shouldDoNothingWhenNoBodyOnResponse() {
        when(response.onBody(onBodyCaptor.capture())).thenReturn(Completable.complete());

        final TestObserver<Void> obs = cut.onResponse(ctx).test();
        obs.assertNoValues();

        final TestObserver<Buffer> bodyObs = ((Maybe<Buffer>) onBodyCaptor.getValue().apply(Maybe.empty())).test();

        bodyObs.assertNoValues();
    }

    @Test
    @DisplayName("Should interrupt with failure when invalid json OnResponse")
    void shouldInterruptWhenInvalidJsonOnResponse() throws IOException {
        final String invalidInput = loadResource("/io/gravitee/policy/xml2json/invalid-input.xml");
        when(response.onBody(onBodyCaptor.capture())).thenReturn(Completable.complete());
        when(response.headers()).thenReturn(HttpHeaders.create());

        final TestObserver<Void> obs = cut.onResponse(ctx).test();
        obs.assertNoValues();

        ((Maybe<Buffer>) onBodyCaptor.getValue().apply(Maybe.just(Buffer.buffer(invalidInput)))).test()
            .assertError(throwable -> {
                assertThat(throwable).isInstanceOf(InterruptionFailureException.class);
                InterruptionFailureException failureException = (InterruptionFailureException) throwable;
                ExecutionFailure executionFailure = failureException.getExecutionFailure();
                assertThat(executionFailure).isNotNull();
                assertThat(executionFailure.key()).isEqualTo("XML_INVALID_PAYLOAD");
                assertThat(executionFailure.statusCode()).isEqualTo(INTERNAL_SERVER_ERROR_500);
                assertThat(executionFailure.message()).isNotNull();

                return true;
            });
    }

    @Test
    @DisplayName("Should transform OnMessageRequest")
    void shouldTransformOnMessageRequest() throws Exception {
        final String input = loadResource("/io/gravitee/policy/xml2json/input.xml");
        final String expected = loadResource("/io/gravitee/policy/xml2json/expected.json");

        when(request.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
        when(request.headers()).thenReturn(HttpHeaders.create());

        final TestObserver<Void> obs = cut.onMessageRequest(ctx).test();
        obs.assertNoValues();

        final TestObserver<Message> bodyObs = onMessageCaptor.getValue().apply(new DefaultMessage(input)).test();

        bodyObs.assertValue(message -> {
            assertThat(expected).isEqualTo(message.content().toString());
            verifyHeaders(message.headers());
            return true;
        });
    }

    @Test
    @DisplayName("Should raise an ExecutionFailure on OnMessageRequest with wrong xml content")
    void shouldRaiseExceptionOnMessageRequestWithWrongContent() throws Exception {
        final String invalidInput = loadResource("/io/gravitee/policy/xml2json/invalid-input.xml");

        when(request.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
        when(request.headers()).thenReturn(HttpHeaders.create());

        final TestObserver<Void> obs = cut.onMessageRequest(ctx).test();
        obs.assertNoValues();

        onMessageCaptor
            .getValue()
            .apply(new DefaultMessage(invalidInput))
            .test()
            .assertError(throwable -> {
                assertThat(throwable).isInstanceOf(InterruptionFailureException.class);
                InterruptionFailureException failureException = (InterruptionFailureException) throwable;
                ExecutionFailure executionFailure = failureException.getExecutionFailure();
                assertThat(executionFailure).isNotNull();
                assertThat(executionFailure.key()).isEqualTo("XML_INVALID_MESSAGE_PAYLOAD");
                assertThat(executionFailure.statusCode()).isEqualTo(BAD_REQUEST_400);
                assertThat(executionFailure.message()).isNotNull();

                return true;
            });
    }

    @Test
    @DisplayName("Should transform OnMessageResponse")
    void shouldTransformOnMessageResponse() throws Exception {
        final String input = loadResource("/io/gravitee/policy/xml2json/input.xml");
        final String expected = loadResource("/io/gravitee/policy/xml2json/expected.json");

        when(response.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
        when(response.headers()).thenReturn(HttpHeaders.create());

        final TestObserver<Void> obs = cut.onMessageResponse(ctx).test();
        obs.assertNoValues();

        final TestObserver<Message> bodyObs = onMessageCaptor.getValue().apply(new DefaultMessage(input)).test();

        bodyObs.assertValue(message -> {
            assertThat(expected).isEqualTo(message.content().toString());
            verifyHeaders(message.headers());
            return true;
        });
    }

    @Test
    @DisplayName("Should raise an ExecutionFailure on OnMessageResponse with wrong xml content")
    void shouldRaiseExceptionOnMessageResponseWithWrongContent() throws Exception {
        final String invalidInput = loadResource("/io/gravitee/policy/xml2json/invalid-input.xml");

        when(response.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
        when(response.headers()).thenReturn(HttpHeaders.create());

        final TestObserver<Void> obs = cut.onMessageResponse(ctx).test();
        obs.assertNoValues();

        onMessageCaptor
            .getValue()
            .apply(new DefaultMessage(invalidInput))
            .test()
            .assertError(throwable -> {
                assertThat(throwable).isInstanceOf(InterruptionFailureException.class);
                InterruptionFailureException failureException = (InterruptionFailureException) throwable;
                ExecutionFailure executionFailure = failureException.getExecutionFailure();
                assertThat(executionFailure).isNotNull();
                assertThat(executionFailure.key()).isEqualTo("XML_INVALID_MESSAGE_PAYLOAD");
                assertThat(executionFailure.statusCode()).isEqualTo(INTERNAL_SERVER_ERROR_500);
                assertThat(executionFailure.message()).isNotNull();

                return true;
            });
    }

    private void verifyHeaders(HttpHeaders headers) {
        assertThat(headers.names()).contains(HttpHeaderNames.CONTENT_TYPE);
        assertThat(headers.getAll(HttpHeaderNames.CONTENT_TYPE).get(0)).isEqualTo(XmlToJsonTransformationPolicy.APPLICATION_JSON);
        assertThat(headers.names()).doesNotContain(HttpHeaderNames.TRANSFER_ENCODING);
        assertThat(headers.names()).contains(HttpHeaderNames.CONTENT_LENGTH);
    }

    private String loadResource(String resource) throws IOException {
        InputStream is = this.getClass().getResourceAsStream(resource);
        StringWriter sw = new StringWriter();
        IOUtils.copy(is, sw, "UTF-8");
        return sw.toString();
    }
}

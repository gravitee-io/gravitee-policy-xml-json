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

import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.stream.exception.TransformationException;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.GenericExecutionContext;
import io.gravitee.gateway.reactive.api.context.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.context.MessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.api.policy.Policy;
import io.gravitee.node.api.configuration.Configuration;
import io.gravitee.policy.v3.xml2json.XmlToJsonTransformationPolicyV3;
import io.gravitee.policy.xml2json.configuration.XmlToJsonTransformationPolicyConfiguration;
import io.gravitee.policy.xml2json.transformer.XML;
import io.gravitee.policy.xml2json.utils.CharsetHelper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class XmlToJsonTransformationPolicy extends XmlToJsonTransformationPolicyV3 implements Policy {

    private static final String INVALID_PAYLOAD_FAILURE_KEY = "XML_INVALID_PAYLOAD";
    private static final String INVALID_MESSAGE_PAYLOAD_FAILURE_KEY = "XML_INVALID_MESSAGE_PAYLOAD";

    private Integer maxDepth;

    public XmlToJsonTransformationPolicy(final XmlToJsonTransformationPolicyConfiguration configuration) {
        super(configuration);
    }

    private static void setContentHeaders(final HttpHeaders headers, final Buffer jsonBuffer) {
        headers.set(HttpHeaderNames.CONTENT_TYPE, APPLICATION_JSON);
        headers.set(HttpHeaderNames.CONTENT_LENGTH, Integer.toString(jsonBuffer.length()));
    }

    @Override
    public String id() {
        return "xml-json";
    }

    @Override
    public Completable onRequest(HttpExecutionContext ctx) {
        return ctx.request().onBody(body -> transformBodyToJson(ctx, body, ctx.request().headers(), HttpStatusCode.BAD_REQUEST_400));
    }

    @Override
    public Completable onResponse(HttpExecutionContext ctx) {
        return ctx
            .response()
            .onBody(body -> transformBodyToJson(ctx, body, ctx.response().headers(), HttpStatusCode.INTERNAL_SERVER_ERROR_500));
    }

    private Maybe<Buffer> transformBodyToJson(
        HttpExecutionContext ctx,
        Maybe<Buffer> bodyUpstream,
        HttpHeaders headers,
        int failureHttpCode
    ) {
        return bodyUpstream
            .flatMap(buffer -> transformToJson(buffer, CharsetHelper.extractCharset(headers), getMaxDepth(ctx)))
            .doOnSuccess(jsonBuffer -> setContentHeaders(headers, jsonBuffer))
            .onErrorResumeWith(
                ctx.interruptBodyWith(
                    new ExecutionFailure(failureHttpCode)
                        .key(INVALID_PAYLOAD_FAILURE_KEY)
                        .message("Unable to transform invalid XML to Json")
                )
            );
    }

    @Override
    public Completable onMessageRequest(MessageExecutionContext ctx) {
        return ctx
            .request()
            .onMessage(message -> transformMessageToJson(ctx, message, ctx.request().headers(), HttpStatusCode.BAD_REQUEST_400));
    }

    @Override
    public Completable onMessageResponse(MessageExecutionContext ctx) {
        return ctx
            .response()
            .onMessage(message -> transformMessageToJson(ctx, message, ctx.response().headers(), HttpStatusCode.INTERNAL_SERVER_ERROR_500));
    }

    private Maybe<Message> transformMessageToJson(MessageExecutionContext ctx, Message message, HttpHeaders headers, int failureHttpCode) {
        return transformToJson(message.content(), CharsetHelper.extractCharset(headers), getMaxDepth(ctx))
            .map(message::content)
            .doOnSuccess(jsonMessage -> setContentHeaders(message.headers(), jsonMessage.content()))
            .onErrorResumeWith(
                ctx.interruptMessageWith(
                    new ExecutionFailure(failureHttpCode)
                        .key(INVALID_MESSAGE_PAYLOAD_FAILURE_KEY)
                        .message("Unable to transform invalid XML message to JSON")
                )
            );
    }

    private int getMaxDepth(GenericExecutionContext ctx) {
        if (this.maxDepth == null) {
            this.maxDepth = ctx.getComponent(Configuration.class).getProperty(POLICY_XML_JSON_MAXDEPTH, Integer.class, DEFAULT_MAX_DEPH);
        }
        return this.maxDepth;
    }

    private Maybe<Buffer> transformToJson(Buffer buffer, Charset charset, int depth) {
        try {
            String encodedPayload = new String(buffer.toString(charset).getBytes(StandardCharsets.UTF_8));
            return Maybe.just(Buffer.buffer(XML.toJSONObject(encodedPayload, depth).toString(), UTF8_CHARSET_NAME));
        } catch (Exception e) {
            return Maybe.error(new TransformationException("Unable to transform XML into JSON:" + e.getMessage(), e));
        }
    }
}

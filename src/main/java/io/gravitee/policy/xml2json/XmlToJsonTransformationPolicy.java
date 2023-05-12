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

import io.gravitee.common.http.MediaType;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.stream.TransformableRequestStreamBuilder;
import io.gravitee.gateway.api.http.stream.TransformableResponseStreamBuilder;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.gateway.api.stream.exception.TransformationException;
import io.gravitee.node.api.configuration.Configuration;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.annotations.OnRequestContent;
import io.gravitee.policy.api.annotations.OnResponseContent;
import io.gravitee.policy.xml2json.configuration.PolicyScope;
import io.gravitee.policy.xml2json.configuration.XmlToJsonTransformationPolicyConfiguration;
import io.gravitee.policy.xml2json.transformer.XML;
import io.gravitee.policy.xml2json.utils.CharsetHelper;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class XmlToJsonTransformationPolicy {

    public static final String POLICY_XML_JSON_MAXDEPTH = "policy.xml-json.maxdepth";
    public static final int DEFAULT_MAX_DEPH = 1000;
    private static final String UTF8_CHARSET_NAME = "UTF-8";
    static final String APPLICATION_JSON = MediaType.APPLICATION_JSON + ";charset=" + UTF8_CHARSET_NAME;

    private Integer maxDepth;

    /**
     * XML to Json transformation configuration
     */
    private final XmlToJsonTransformationPolicyConfiguration xmlToJsonTransformationPolicyConfiguration;

    public XmlToJsonTransformationPolicy(final XmlToJsonTransformationPolicyConfiguration xmlToJsonTransformationPolicyConfiguration) {
        this.xmlToJsonTransformationPolicyConfiguration = xmlToJsonTransformationPolicyConfiguration;
    }

    @OnResponseContent
    public ReadWriteStream onResponseContent(Response response, PolicyChain chain, ExecutionContext context) {
        if (
            xmlToJsonTransformationPolicyConfiguration.getScope() == null ||
            xmlToJsonTransformationPolicyConfiguration.getScope() == PolicyScope.RESPONSE
        ) {
            Charset charset = CharsetHelper.extractCharset(response.headers());

            return TransformableResponseStreamBuilder
                .on(response)
                .chain(chain)
                .contentType(APPLICATION_JSON)
                .transform(map(charset, getMaxDepth(context)))
                .build();
        }

        return null;
    }

    @OnRequestContent
    public ReadWriteStream onRequestContent(Request request, PolicyChain chain, ExecutionContext context) {
        if (xmlToJsonTransformationPolicyConfiguration.getScope() == PolicyScope.REQUEST) {
            Charset charset = CharsetHelper.extractCharset(request.headers());
            return TransformableRequestStreamBuilder
                .on(request)
                .chain(chain)
                .contentType(APPLICATION_JSON)
                .transform(map(charset, getMaxDepth(context)))
                .build();
        }

        return null;
    }

    private Function<Buffer, Buffer> map(Charset charset, int depth) {
        return input -> {
            try {
                String encodedPayload = new String(input.toString(charset).getBytes(UTF8_CHARSET_NAME));
                return Buffer.buffer(XML.toJSONObject(encodedPayload, depth).toString(), UTF8_CHARSET_NAME);
            } catch (Exception ex) {
                throw new TransformationException("Unable to transform XML into JSON: " + ex.getMessage(), ex);
            }
        };
    }

    private int getMaxDepth(ExecutionContext context) {
        if (this.maxDepth == null) {
            this.maxDepth =
                context.getComponent(Configuration.class).getProperty(POLICY_XML_JSON_MAXDEPTH, Integer.class, DEFAULT_MAX_DEPH);
        }
        return maxDepth;
    }
}

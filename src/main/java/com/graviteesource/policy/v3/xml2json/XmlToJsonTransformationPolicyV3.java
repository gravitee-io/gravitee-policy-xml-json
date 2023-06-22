package com.graviteesource.policy.v3.xml2json;

import com.graviteesource.policy.xml2json.configuration.PolicyScope;
import com.graviteesource.policy.xml2json.configuration.XmlToJsonTransformationPolicyConfiguration;
import com.graviteesource.policy.xml2json.deployer.XmlToJsonTransformationPolicyDeploymentLifecycle;
import com.graviteesource.policy.xml2json.transformer.XML;
import com.graviteesource.policy.xml2json.utils.CharsetHelper;
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
import io.gravitee.plugin.api.annotations.Plugin;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.annotations.OnRequestContent;
import io.gravitee.policy.api.annotations.OnResponseContent;
import java.nio.charset.Charset;
import java.util.function.Function;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Plugin(deployment = XmlToJsonTransformationPolicyDeploymentLifecycle.class)
public class XmlToJsonTransformationPolicyV3 {

    public static final String POLICY_XML_JSON_MAXDEPTH = "policy.xml-json.maxdepth";
    public static final int DEFAULT_MAX_DEPH = 100;
    public static final String UTF8_CHARSET_NAME = "UTF-8";
    public static final String APPLICATION_JSON = MediaType.APPLICATION_JSON + ";charset=" + UTF8_CHARSET_NAME;

    private Integer maxDepth;

    /**
     * XML to Json transformation configuration
     */
    protected final XmlToJsonTransformationPolicyConfiguration xmlToJsonTransformationPolicyConfiguration;

    public XmlToJsonTransformationPolicyV3(final XmlToJsonTransformationPolicyConfiguration xmlToJsonTransformationPolicyConfiguration) {
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

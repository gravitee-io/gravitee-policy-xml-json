package com.graviteesource.policy.xml2json.swagger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.graviteesource.policy.xml2json.configuration.PolicyScope;
import com.graviteesource.policy.xml2json.configuration.XmlToJsonTransformationPolicyConfiguration;
import io.gravitee.policy.api.swagger.Policy;
import io.gravitee.policy.api.swagger.v3.OAIOperationVisitor;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import java.util.Map;
import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class XmlToJsonOAIOperationVisitor implements OAIOperationVisitor {

    public static final String SOAP_EXTENSION_ENVELOPE = "x-graviteeio-soap-envelope";

    private final ObjectMapper mapper = new ObjectMapper();

    {
        mapper.configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, true);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public boolean display() {
        return false;
    }

    @Override
    public Optional<Policy> visit(OpenAPI openAPI, Operation operation) {
        Map<String, Object> extensions = operation.getExtensions();

        if (extensions != null && extensions.containsKey(SOAP_EXTENSION_ENVELOPE)) {
            XmlToJsonTransformationPolicyConfiguration configuration = new XmlToJsonTransformationPolicyConfiguration();
            try {
                Policy policy = new Policy();
                policy.setName("xml-json");
                configuration.setScope(PolicyScope.RESPONSE);
                policy.setConfiguration(mapper.writeValueAsString(configuration));
                return Optional.of(policy);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }

        return Optional.empty();
    }
}

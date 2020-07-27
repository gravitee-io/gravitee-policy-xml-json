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
package io.gravitee.policy.xml2json.swagger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.gravitee.policy.api.swagger.Policy;
import io.gravitee.policy.api.swagger.v3.OAIOperationVisitor;
import io.gravitee.policy.xml2json.configuration.PolicyScope;
import io.gravitee.policy.xml2json.configuration.XmlToJsonTransformationPolicyConfiguration;
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

    private final ObjectMapper mapper  = new ObjectMapper();
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
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
package io.gravitee.policy.xml2json.swagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.policy.api.swagger.Policy;
import io.gravitee.policy.xml2json.configuration.PolicyScope;
import io.gravitee.policy.xml2json.configuration.XmlToJsonTransformationPolicyConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class XmlToJsonOAIOperationVisitorTest {

    protected XmlToJsonOAIOperationVisitor visitor = new XmlToJsonOAIOperationVisitor();

    @Test
    public void shouldNotReturnPolicy_operationWithoutExtension() {
        Operation operationMock = mock(Operation.class);
        when(operationMock.getExtensions()).thenReturn(null);
        Optional<Policy> policy = visitor.visit(mock(OpenAPI.class), operationMock);
        assertThat(policy).isEmpty();
    }

    @Test
    public void shouldNotReturnPolicy_operationWithoutSoapEnvelope() {
        Map<String, Object> extensions = new HashMap<>();
        // test existence of extension map without soapEnvelope entry
        Operation operationMock = mock(Operation.class);
        when(operationMock.getExtensions()).thenReturn(extensions);
        when(operationMock.getExtensions()).thenReturn(null);
        Optional<Policy> policy = visitor.visit(mock(OpenAPI.class), operationMock);
        assertThat(policy).isEmpty();
    }

    @Test
    public void shouldReturnPolicy_operationWithSoapEnvelope() throws Exception {
        Map<String, Object> extensions = new HashMap<>();
        extensions.put(XmlToJsonOAIOperationVisitor.SOAP_EXTENSION_ENVELOPE, "envelope");
        Operation operationMock = mock(Operation.class);
        when(operationMock.getExtensions()).thenReturn(extensions);
        Optional<Policy> policy = visitor.visit(mock(OpenAPI.class), operationMock);
        assertThat(policy).isNotEmpty();
        String configuration = policy.get().getConfiguration();
        assertThat(configuration).isNotNull();
        XmlToJsonTransformationPolicyConfiguration readConfig = new ObjectMapper()
            .readValue(configuration, XmlToJsonTransformationPolicyConfiguration.class);
        assertThat(readConfig.getScope()).isEqualTo(PolicyScope.RESPONSE);
    }
}

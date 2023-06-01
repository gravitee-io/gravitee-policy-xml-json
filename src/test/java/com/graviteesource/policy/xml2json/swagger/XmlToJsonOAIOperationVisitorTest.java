package com.graviteesource.policy.xml2json.swagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graviteesource.policy.xml2json.configuration.PolicyScope;
import com.graviteesource.policy.xml2json.configuration.XmlToJsonTransformationPolicyConfiguration;
import io.gravitee.policy.api.swagger.Policy;
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

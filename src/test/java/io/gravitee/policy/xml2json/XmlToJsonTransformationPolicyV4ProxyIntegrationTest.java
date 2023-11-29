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

import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.plugin.endpoint.EndpointConnectorPlugin;
import io.gravitee.plugin.endpoint.http.proxy.HttpProxyEndpointConnectorFactory;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.plugin.entrypoint.http.proxy.HttpProxyEntrypointConnectorFactory;
import io.vertx.rxjava3.core.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
@GatewayTest
public class XmlToJsonTransformationPolicyV4ProxyIntegrationTest extends XmlToJsonTransformationPolicyV4EmulationEngineIntegrationTest {

    @Override
    public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
        entrypoints.putIfAbsent("http-proxy", EntrypointBuilder.build("http-proxy", HttpProxyEntrypointConnectorFactory.class));
    }

    @Override
    public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
        endpoints.putIfAbsent("http-proxy", EndpointBuilder.build("http-proxy", HttpProxyEndpointConnectorFactory.class));
    }

    @Override
    @Test
    @DeployApi("/apis/v4/api-request.json")
    void should_post_xml_content_to_backend(HttpClient client) throws InterruptedException {
        super.should_post_xml_content_to_backend(client);
    }

    @Override
    @Test
    @DeployApi("/apis/v4/api-request.json")
    void should_return_bad_request_when_posting_invalid_json_to_gateway(HttpClient client) throws InterruptedException {
        super.should_return_bad_request_when_posting_invalid_json_to_gateway(client);
    }

    @Override
    @Test
    @DeployApi("/apis/v4/api-response.json")
    void should_get_json_content_from_backend(HttpClient client) throws InterruptedException {
        super.should_get_json_content_from_backend(client);
    }

    @Override
    @Test
    @DeployApi("/apis/v4/api-response.json")
    void should_return_internal_error_when_getting_invalid_xml_content_from_backend(HttpClient client) throws InterruptedException {
        super.should_return_internal_error_when_getting_invalid_xml_content_from_backend(client);
    }

    @Override
    @Test
    @DeployApi("/apis/v4/api-request.json")
    void should_return_internal_error_when_too_many_nested(HttpClient client) throws InterruptedException {
        super.should_return_internal_error_when_too_many_nested(client);
    }
}

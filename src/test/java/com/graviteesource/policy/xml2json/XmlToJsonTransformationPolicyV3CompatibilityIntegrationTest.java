/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graviteesource.policy.xml2json;

import static io.vertx.core.http.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.configuration.GatewayMode;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
@GatewayTest(mode = GatewayMode.COMPATIBILITY)
public class XmlToJsonTransformationPolicyV3CompatibilityIntegrationTest extends XmlToJsonTransformationPolicyIntegrationTest {

    @Override
    @Test
    @DisplayName("Should return Bad Request when posting invalid json to gateway")
    @DeployApi("/apis/v2/api-pre.json")
    void should_return_bad_request_when_posting_invalid_json_to_gateway(HttpClient client) throws InterruptedException {
        final String input = loadResource("/com/graviteesource/policy/xml2json/invalid-input.xml");

        client
            .rxRequest(POST, "/test")
            .flatMap(request -> request.rxSend(Buffer.buffer(input)))
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(500);
                return response.toFlowable();
            })
            .test()
            .await()
            .assertComplete()
            .assertNoErrors();
    }

    @Override
    @Test
    @DeployApi("/apis/v2/api-pre.json")
    void should_return_internal_error_when_too_many_nested(HttpClient client) throws InterruptedException {
        final String input = loadResource("/com/graviteesource/policy/xml2json/invalid-nested-object.xml");
        client
            .rxRequest(POST, "/test")
            .flatMap(request -> request.rxSend(Buffer.buffer(input)))
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(500);
                return response.toFlowable();
            })
            .test()
            .await()
            .assertComplete()
            .assertNoErrors();
    }
}

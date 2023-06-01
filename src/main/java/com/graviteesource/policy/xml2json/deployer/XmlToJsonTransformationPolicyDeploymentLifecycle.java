package com.graviteesource.policy.xml2json.deployer;

import io.gravitee.node.api.deployer.AbstractPluginDeploymentLifecycle;

/**
 * @author Kamiel Ahmadpour (kamiel.ahmadpour at graviteesource.com)
 * @author GraviteeSource Team
 */
public class XmlToJsonTransformationPolicyDeploymentLifecycle extends AbstractPluginDeploymentLifecycle {

    private static final String XML_TO_JSON_TRANSFORMATION_POLICY = "apim-policy-xml-to-json";

    @Override
    protected String getFeatureName() {
        return XML_TO_JSON_TRANSFORMATION_POLICY;
    }
}

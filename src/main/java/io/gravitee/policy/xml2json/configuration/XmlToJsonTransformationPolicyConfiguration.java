package io.gravitee.policy.xml2json.configuration;

import io.gravitee.policy.api.PolicyConfiguration;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class XmlToJsonTransformationPolicyConfiguration implements PolicyConfiguration {

    private PolicyScope scope = PolicyScope.RESPONSE;

    public PolicyScope getScope() {
        return scope;
    }

    public void setScope(PolicyScope scope) {
        this.scope = scope;
    }
}

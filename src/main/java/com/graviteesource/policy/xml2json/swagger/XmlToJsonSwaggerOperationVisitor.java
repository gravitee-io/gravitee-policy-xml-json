package com.graviteesource.policy.xml2json.swagger;

import io.gravitee.policy.api.swagger.Policy;
import io.gravitee.policy.api.swagger.v2.SwaggerOperationVisitor;
import io.swagger.models.Operation;
import io.swagger.models.Swagger;
import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class XmlToJsonSwaggerOperationVisitor implements SwaggerOperationVisitor {

    @Override
    public boolean display() {
        return false;
    }

    @Override
    public Optional<Policy> visit(Swagger swagger, Operation o) {
        return Optional.empty();
    }
}

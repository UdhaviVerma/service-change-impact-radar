package com.udhav.impactradar.analysis;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record OpenApiSpec(
    String serviceName,
    Map<String, Set<String>> operationsByPath,
    Map<String, String> operationPathByOperationId,
    Map<String, ObjectSchema> schemas,
    Map<String, ObjectSchema> eventSchemas
) {

    public record ObjectSchema(
        String name,
        Map<String, PropertySchema> properties,
        Set<String> requiredFields
    ) {}

    public record PropertySchema(
        String type,
        List<String> enumValues,
        String ref
    ) {}
}

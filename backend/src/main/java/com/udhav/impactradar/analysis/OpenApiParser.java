package com.udhav.impactradar.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class OpenApiParser {

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public OpenApiSpec parse(String rawContent) {
        JsonNode root = parseNode(rawContent);

        Map<String, Set<String>> operationsByPath = new LinkedHashMap<>();
        Map<String, String> operationPathById = new LinkedHashMap<>();
        JsonNode paths = root.path("paths");
        if (paths.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> pathIterator = paths.fields();
            while (pathIterator.hasNext()) {
                Map.Entry<String, JsonNode> pathEntry = pathIterator.next();
                String path = pathEntry.getKey();
                JsonNode methodsNode = pathEntry.getValue();
                Set<String> methods = new LinkedHashSet<>();
                methodsNode.fieldNames().forEachRemaining(method -> {
                    methods.add(method.toUpperCase(Locale.ROOT));
                    JsonNode opNode = methodsNode.path(method);
                    String opId = opNode.path("operationId").asText("").trim();
                    if (!opId.isEmpty()) {
                        operationPathById.put(opId, method.toUpperCase(Locale.ROOT) + " " + path);
                    }
                });
                operationsByPath.put(path, methods);
            }
        }

        String serviceName = root.path("info").path("title").asText("unknown-service")
            .replace(" API", "")
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace(' ', '-');

        Map<String, OpenApiSpec.ObjectSchema> schemas = parseSchemaMap(root.path("components").path("schemas"));
        Map<String, OpenApiSpec.ObjectSchema> eventSchemas = parseSchemaMap(root.path("x-events"));

        return new OpenApiSpec(serviceName, operationsByPath, operationPathById, schemas, eventSchemas);
    }

    private JsonNode parseNode(String content) {
        try {
            return jsonMapper.readTree(content);
        } catch (JsonProcessingException ignored) {
        }
        try {
            return yamlMapper.readTree(content);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unsupported OpenAPI format. Upload valid JSON or YAML.");
        }
    }

    private Map<String, OpenApiSpec.ObjectSchema> parseSchemaMap(JsonNode schemasNode) {
        Map<String, OpenApiSpec.ObjectSchema> result = new LinkedHashMap<>();
        if (!schemasNode.isObject()) {
            return result;
        }

        Iterator<Map.Entry<String, JsonNode>> schemaIterator = schemasNode.fields();
        while (schemaIterator.hasNext()) {
            Map.Entry<String, JsonNode> schemaEntry = schemaIterator.next();
            String schemaName = schemaEntry.getKey();
            JsonNode schemaNode = schemaEntry.getValue();

            if (!schemaNode.isObject()) {
                continue;
            }

            Set<String> required = new LinkedHashSet<>();
            JsonNode requiredNode = schemaNode.path("required");
            if (requiredNode.isArray()) {
                requiredNode.forEach(node -> required.add(node.asText()));
            }

            Map<String, OpenApiSpec.PropertySchema> properties = new LinkedHashMap<>();
            JsonNode propertiesNode = schemaNode.path("properties");
            if (propertiesNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> propIterator = propertiesNode.fields();
                while (propIterator.hasNext()) {
                    Map.Entry<String, JsonNode> propEntry = propIterator.next();
                    JsonNode propNode = propEntry.getValue();
                    String type = propNode.path("type").asText("");
                    String ref = propNode.path("$ref").asText("");
                    List<String> enumValues = new ArrayList<>();
                    JsonNode enumNode = propNode.path("enum");
                    if (enumNode.isArray()) {
                        enumNode.forEach(val -> enumValues.add(val.asText()));
                    }
                    properties.put(propEntry.getKey(), new OpenApiSpec.PropertySchema(type, enumValues, ref));
                }
            }

            result.put(schemaName, new OpenApiSpec.ObjectSchema(schemaName, properties, required));
        }

        return result;
    }
}

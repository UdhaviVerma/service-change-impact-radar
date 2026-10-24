package com.udhav.impactradar.analysis;

import com.udhav.impactradar.domain.DemoWorkspace;
import com.udhav.impactradar.domain.DependencyEdge;
import com.udhav.impactradar.domain.DependencyType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ImpactAnalyzerService {

    public ImpactReport analyze(
        String workspaceId,
        String providerService,
        OpenApiSpec before,
        OpenApiSpec after,
        DemoWorkspace workspace
    ) {
        List<ImpactFinding> findings = new ArrayList<>();

        findings.addAll(findRemovedAndRenamedEndpoints(before, after, workspace, providerService));
        findings.addAll(findSchemaChanges(before.schemas(), after.schemas(), workspace, providerService, false));
        findings.addAll(findSchemaChanges(before.eventSchemas(), after.eventSchemas(), workspace, providerService, true));

        findings.sort(Comparator.comparing((ImpactFinding f) -> f.severity().ordinal()));

        Map<Severity, Integer> severityCount = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            severityCount.put(severity, 0);
        }
        findings.forEach(f -> severityCount.computeIfPresent(f.severity(), (k, v) -> v + 1));

        Set<String> affectedServices = findings.stream()
            .map(ImpactFinding::potentiallyAffectedService)
            .filter(value -> value != null && !value.isBlank() && !"unknown".equals(value))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        String risk = overallRisk(severityCount);
        String summary = "%d findings detected across %d potentially affected services.".formatted(findings.size(), affectedServices.size());

        List<String> checklist = List.of(
            "Run consumer contract tests for affected services.",
            "Verify backward compatibility for required and enum fields.",
            "Replay a staging message sample for impacted Kafka topics.",
            "Validate endpoint changes with canary traffic before full rollout.",
            "Update API and event versioning notes in release docs."
        );

        return new ImpactReport(
            workspaceId,
            providerService,
            summary,
            risk,
            severityCount,
            affectedServices,
            findings,
            checklist,
            Instant.now().toString()
        );
    }

    private List<ImpactFinding> findRemovedAndRenamedEndpoints(
        OpenApiSpec before,
        OpenApiSpec after,
        DemoWorkspace workspace,
        String providerService
    ) {
        List<ImpactFinding> findings = new ArrayList<>();

        Set<String> beforeOps = flatten(before.operationsByPath());
        Set<String> afterOps = flatten(after.operationsByPath());

        Set<String> removedOps = new LinkedHashSet<>(beforeOps);
        removedOps.removeAll(afterOps);

        Map<String, String> beforeByOperationId = before.operationPathByOperationId();
        Map<String, String> afterByOperationId = after.operationPathByOperationId();

        for (Map.Entry<String, String> entry : beforeByOperationId.entrySet()) {
            String operationId = entry.getKey();
            String beforePath = entry.getValue();
            String afterPath = afterByOperationId.get(operationId);
            if (afterPath != null && !afterPath.equals(beforePath)) {
                String op = beforePath;
                impactedRestConsumers(workspace, providerService, op).forEach(consumer ->
                    findings.add(new ImpactFinding(
                        Severity.HIGH,
                        "Endpoint path changed for same operationId",
                        "OperationId '%s' moved from '%s' to '%s'. Existing consumers may still call the old path."
                            .formatted(operationId, beforePath, afterPath),
                        operationId,
                        consumer,
                        List.of("operationId=" + operationId, "before=" + beforePath, "after=" + afterPath),
                        "Update consumer client path and run integration tests against '%s'.".formatted(afterPath),
                        true,
                        "high"
                    ))
                );
            }
        }

        for (String removed : removedOps) {
            impactedRestConsumers(workspace, providerService, removed).forEach(consumer ->
                findings.add(new ImpactFinding(
                    Severity.CRITICAL,
                    "Endpoint removed",
                    "Endpoint '%s' exists in before spec but not in after spec."
                        .formatted(removed),
                    removed,
                    consumer,
                    List.of("paths diff", "operation=" + removed),
                    "Either restore endpoint or coordinate client migration before deploy.",
                    true,
                    "high"
                ))
            );
        }

        return findings;
    }

    private List<ImpactFinding> findSchemaChanges(
        Map<String, OpenApiSpec.ObjectSchema> beforeSchemas,
        Map<String, OpenApiSpec.ObjectSchema> afterSchemas,
        DemoWorkspace workspace,
        String providerService,
        boolean events
    ) {
        List<ImpactFinding> findings = new ArrayList<>();
        for (Map.Entry<String, OpenApiSpec.ObjectSchema> schemaEntry : beforeSchemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            OpenApiSpec.ObjectSchema before = schemaEntry.getValue();
            OpenApiSpec.ObjectSchema after = afterSchemas.get(schemaName);
            if (after == null) {
                continue;
            }

            Set<String> beforeFields = before.properties().keySet();
            Set<String> afterFields = after.properties().keySet();

            for (String field : beforeFields) {
                if (!afterFields.contains(field)) {
                    boolean required = before.requiredFields().contains(field);
                    Severity severity = required ? Severity.HIGH : Severity.MEDIUM;
                    consumersFor(workspace, providerService, events).forEach(consumer ->
                        findings.add(new ImpactFinding(
                            severity,
                            "Field removed",
                            "Field '%s' was removed from schema '%s'.".formatted(field, schemaName),
                            schemaName + "." + field,
                            consumer,
                            List.of("schema=" + schemaName, "field=" + field, "required-before=" + required),
                            "Validate deserialization and null-handling in '%s'.".formatted(consumer),
                            required,
                            required ? "high" : "medium"
                        ))
                    );
                }
            }

            for (String field : afterFields) {
                if (!beforeFields.contains(field) && after.requiredFields().contains(field)) {
                    consumersFor(workspace, providerService, events).forEach(consumer ->
                        findings.add(new ImpactFinding(
                            Severity.HIGH,
                            "Required field added",
                            "New required field '%s' was added to schema '%s'.".formatted(field, schemaName),
                            schemaName + "." + field,
                            consumer,
                            List.of("schema=" + schemaName, "field=" + field, "required-after=true"),
                            "Ensure producers populate '%s' and consumers can parse it.".formatted(field),
                            true,
                            "high"
                        ))
                    );
                }
            }

            for (String field : beforeFields) {
                if (!afterFields.contains(field)) {
                    continue;
                }
                OpenApiSpec.PropertySchema beforeField = before.properties().get(field);
                OpenApiSpec.PropertySchema afterField = after.properties().get(field);

                if (!Objects.equals(beforeField.type(), afterField.type()) || !Objects.equals(beforeField.ref(), afterField.ref())) {
                    consumersFor(workspace, providerService, events).forEach(consumer ->
                        findings.add(new ImpactFinding(
                            Severity.HIGH,
                            "Field type changed",
                            "Field '%s' in schema '%s' changed type/ref from '%s%s' to '%s%s'."
                                .formatted(field, schemaName, beforeField.type(), beforeField.ref(), afterField.type(), afterField.ref()),
                            schemaName + "." + field,
                            consumer,
                            List.of("schema=" + schemaName, "field=" + field, "beforeType=" + beforeField.type(), "afterType=" + afterField.type()),
                            "Run contract tests and update serializers if needed.",
                            true,
                            "high"
                        ))
                    );
                }

                Set<String> removedEnumValues = new LinkedHashSet<>(beforeField.enumValues());
                removedEnumValues.removeAll(afterField.enumValues());
                if (!removedEnumValues.isEmpty()) {
                    consumersFor(workspace, providerService, events).forEach(consumer ->
                        findings.add(new ImpactFinding(
                            Severity.MEDIUM,
                            "Enum values removed",
                            "Field '%s' in schema '%s' removed enum values: %s."
                                .formatted(field, schemaName, String.join(", ", removedEnumValues)),
                            schemaName + "." + field,
                            consumer,
                            List.of("schema=" + schemaName, "field=" + field, "removedEnum=" + removedEnumValues),
                            "Check switch/case logic and validation rules for removed enum values.",
                            false,
                            "medium"
                        ))
                    );
                }
            }
        }

        return findings;
    }

    private Set<String> flatten(Map<String, Set<String>> opsByPath) {
        Set<String> result = new LinkedHashSet<>();
        opsByPath.forEach((path, methods) -> methods.forEach(method -> result.add(method + " " + path)));
        return result;
    }

    private Set<String> impactedRestConsumers(DemoWorkspace workspace, String providerService, String operation) {
        return workspace.dependencies().stream()
            .filter(dep -> dep.type() == DependencyType.REST)
            .filter(dep -> dep.targetService().equals(providerService))
            .filter(dep -> operation.equals(dep.operation()))
            .map(DependencyEdge::sourceService)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> consumersFor(DemoWorkspace workspace, String providerService, boolean events) {
        return workspace.dependencies().stream()
            .filter(dep -> dep.targetService().equals(providerService))
            .filter(dep -> events ? dep.type() == DependencyType.KAFKA : dep.type() == DependencyType.REST)
            .map(DependencyEdge::sourceService)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String overallRisk(Map<Severity, Integer> severityCount) {
        if (severityCount.getOrDefault(Severity.CRITICAL, 0) > 0) {
            return "High";
        }
        if (severityCount.getOrDefault(Severity.HIGH, 0) > 1) {
            return "High";
        }
        if (severityCount.getOrDefault(Severity.HIGH, 0) > 0 || severityCount.getOrDefault(Severity.MEDIUM, 0) > 2) {
            return "Medium";
        }
        return "Low";
    }
}

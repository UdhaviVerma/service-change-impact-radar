package com.udhav.impactradar.analysis;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ImpactReport(
    String workspaceId,
    String providerService,
    String summary,
    String overallRisk,
    Map<Severity, Integer> findingsBySeverity,
    Set<String> affectedServices,
    List<ImpactFinding> findings,
    List<String> rolloutChecklist,
    String generatedAt
) {
}

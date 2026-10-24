package com.udhav.impactradar.domain;

public record DependencyEdge(
    String id,
    String sourceService,
    String targetService,
    DependencyType type,
    String operation,
    String topic,
    String sourceReference,
    String notes
) {
}

package com.udhav.impactradar.domain;

import java.util.List;

public record DemoWorkspace(
    String id,
    String name,
    String description,
    List<ServiceNode> services,
    List<DependencyEdge> dependencies,
    String sampleProvider,
    String sampleChangeDescription
) {
}

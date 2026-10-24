package com.udhav.impactradar.analysis;

import java.util.List;

public record ImpactFinding(
    Severity severity,
    String title,
    String explanation,
    String changedElement,
    String potentiallyAffectedService,
    List<String> evidence,
    String recommendedVerification,
    boolean breaking,
    String confidence
) {
}

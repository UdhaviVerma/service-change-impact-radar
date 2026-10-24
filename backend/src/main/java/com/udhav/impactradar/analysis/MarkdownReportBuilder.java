package com.udhav.impactradar.analysis;

import org.springframework.stereotype.Component;

@Component
public class MarkdownReportBuilder {

    public String build(ImpactReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# Service Change Impact Report\n\n");
        md.append("- Workspace: ").append(report.workspaceId()).append("\n");
        md.append("- Provider service: ").append(report.providerService()).append("\n");
        md.append("- Generated at: ").append(report.generatedAt()).append("\n");
        md.append("- Overall risk: **").append(report.overallRisk()).append("**\n\n");

        md.append("## Summary\n");
        md.append(report.summary()).append("\n\n");

        md.append("## Findings by Severity\n");
        report.findingsBySeverity().forEach((severity, count) ->
            md.append("- ").append(severity).append(": ").append(count).append("\n")
        );
        md.append("\n");

        md.append("## Affected Services\n");
        if (report.affectedServices().isEmpty()) {
            md.append("- None identified\n\n");
        } else {
            report.affectedServices().forEach(service -> md.append("- ").append(service).append("\n"));
            md.append("\n");
        }

        md.append("## Detailed Findings\n");
        if (report.findings().isEmpty()) {
            md.append("No findings detected.\n\n");
        } else {
            int index = 1;
            for (ImpactFinding finding : report.findings()) {
                md.append("### ").append(index++).append(". ").append(finding.title()).append("\n");
                md.append("- Severity: **").append(finding.severity()).append("**\n");
                md.append("- Potentially affected service: ").append(finding.potentiallyAffectedService()).append("\n");
                md.append("- Changed element: ").append(finding.changedElement()).append("\n");
                md.append("- Breaking: ").append(finding.breaking() ? "Yes" : "Unknown/Not guaranteed").append("\n");
                md.append("- Confidence: ").append(finding.confidence()).append("\n");
                md.append("- Explanation: ").append(finding.explanation()).append("\n");
                md.append("- Evidence:\n");
                finding.evidence().forEach(ev -> md.append("  - ").append(ev).append("\n"));
                md.append("- Suggested check: ").append(finding.recommendedVerification()).append("\n\n");
            }
        }

        md.append("## Suggested Test and Rollout Checklist\n");
        report.rolloutChecklist().forEach(item -> md.append("- [ ] ").append(item).append("\n"));
        md.append("\n");

        md.append("> Demo note: this analysis uses demo dependency data unless custom workspace wiring is provided.\n");

        return md.toString();
    }
}

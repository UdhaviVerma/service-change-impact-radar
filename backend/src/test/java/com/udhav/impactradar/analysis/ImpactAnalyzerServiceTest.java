package com.udhav.impactradar.analysis;

import com.udhav.impactradar.domain.DemoWorkspace;
import com.udhav.impactradar.repository.DemoWorkspaceRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImpactAnalyzerServiceTest {

    private final OpenApiParser parser = new OpenApiParser();
    private final ImpactAnalyzerService service = new ImpactAnalyzerService();
    private final DemoWorkspace workspace = new DemoWorkspaceRepository().getDemoWorkspace();

    @Test
    void reportsCriticalWhenEndpointIsRemoved() {
        String before = """
            openapi: 3.0.3
            info: { title: Customer Service API, version: '1.0' }
            paths:
              /api/v1/customers/{id}:
                get:
                  operationId: getCustomer
            components: { schemas: {} }
            """;
        String after = """
            openapi: 3.0.3
            info: { title: Customer Service API, version: '1.1' }
            paths: {}
            components: { schemas: {} }
            """;

        ImpactReport report = service.analyze(
            workspace.id(),
            "customer-service",
            parser.parse(before),
            parser.parse(after),
            workspace
        );

        assertThat(report.findings()).anyMatch(f -> f.title().equals("Endpoint removed") && f.severity() == Severity.CRITICAL);
        assertThat(report.overallRisk()).isEqualTo("High");
    }

    @Test
    void reportsHighWhenFieldTypeChanges() {
        String before = """
            openapi: 3.0.3
            info: { title: Customer Service API, version: '1.0' }
            paths: {}
            components:
              schemas:
                Customer:
                  type: object
                  properties:
                    id:
                      type: string
            """;

        String after = """
            openapi: 3.0.3
            info: { title: Customer Service API, version: '1.1' }
            paths: {}
            components:
              schemas:
                Customer:
                  type: object
                  properties:
                    id:
                      type: integer
            """;

        ImpactReport report = service.analyze(
            workspace.id(),
            "customer-service",
            parser.parse(before),
            parser.parse(after),
            workspace
        );

        assertThat(report.findings()).anyMatch(f -> f.title().equals("Field type changed") && f.severity() == Severity.HIGH);
    }
}

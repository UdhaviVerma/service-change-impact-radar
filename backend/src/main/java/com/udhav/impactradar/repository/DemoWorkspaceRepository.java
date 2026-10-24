package com.udhav.impactradar.repository;

import com.udhav.impactradar.domain.DemoWorkspace;
import com.udhav.impactradar.domain.DependencyEdge;
import com.udhav.impactradar.domain.DependencyType;
import com.udhav.impactradar.domain.ServiceNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DemoWorkspaceRepository {

    public DemoWorkspace getDemoWorkspace() {
        return new DemoWorkspace(
            "demo-microservices",
            "Acme Commerce Platform (Demo)",
            "Demo workspace showing REST and Kafka dependencies between five services.",
            List.of(
                new ServiceNode("customer-service", "Customer Service", "Owns customer profiles and contact preferences."),
                new ServiceNode("order-service", "Order Service", "Creates and tracks orders."),
                new ServiceNode("payment-service", "Payment Service", "Handles payment authorization and settlement."),
                new ServiceNode("notification-service", "Notification Service", "Sends email/SMS notifications."),
                new ServiceNode("analytics-service", "Analytics Service", "Builds business metrics from domain events.")
            ),
            List.of(
                new DependencyEdge("dep-1", "order-service", "customer-service", DependencyType.REST, "GET /api/v1/customers/{id}", null, "order-service/src/clients/customer-client.ts", "Fetch customer profile during checkout"),
                new DependencyEdge("dep-2", "payment-service", "order-service", DependencyType.REST, "GET /api/v1/orders/{id}", null, "payment-service/src/main/java/.../OrderClient.java", "Loads order amount before charging"),
                new DependencyEdge("dep-3", "notification-service", "order-service", DependencyType.KAFKA, null, "order.created.v1", "notification-service/src/consumers/orderCreatedConsumer.ts", "Sends order confirmation"),
                new DependencyEdge("dep-4", "analytics-service", "order-service", DependencyType.KAFKA, null, "order.created.v1", "analytics-service/src/pipelines/order-created-pipeline.py", "Updates daily order dashboards"),
                new DependencyEdge("dep-5", "analytics-service", "payment-service", DependencyType.KAFKA, null, "payment.completed.v1", "analytics-service/src/pipelines/payment-pipeline.py", "Tracks conversion and payment success"),
                new DependencyEdge("dep-6", "notification-service", "customer-service", DependencyType.REST, "GET /api/v1/customers/{id}/preferences", null, "notification-service/src/client/customer-preferences.ts", "Checks notification opt-in")
            ),
            "customer-service",
            "Customer Service API change introduces endpoint and schema differences for analysis."
        );
    }
}

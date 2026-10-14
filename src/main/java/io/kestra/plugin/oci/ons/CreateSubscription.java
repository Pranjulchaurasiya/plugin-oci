package io.kestra.plugin.oci.ons;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.ons.NotificationDataPlaneClient;
import com.oracle.bmc.ons.model.CreateSubscriptionDetails;
import com.oracle.bmc.ons.model.Subscription;
import com.oracle.bmc.ons.requests.CreateSubscriptionRequest;
import com.oracle.bmc.ons.responses.CreateSubscriptionResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.oci.AbstractOciTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.time.Instant;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create an OCI Notification Service (ONS) subscription",
    description = "Subscribes an endpoint (Email, PagerDuty, Slack, HTTPS webhook) to receive notifications from an ONS topic."
)
@Plugin(
    examples = {
        @Example(
            title = "Subscribe an HTTPS webhook or PagerDuty to an ONS topic",
            full = true,
            code = """
                id: create_topic_subscription
                namespace: company.ops

                tasks:
                  - id: subscribe
                    type: io.kestra.plugin.oci.ons.CreateSubscription
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    compartmentId: "{{ secret('OCI_COMPARTMENT_OCID') }}"
                    topicId: "{{ secret('OCI_ONS_TOPIC_OCID') }}"
                    protocol: HTTPS
                    endpoint: "https://events.pagerduty.com/integration/xxx/enqueue"
                """
        )
    }
)
public class CreateSubscription extends AbstractOciTask implements RunnableTask<CreateSubscription.Output> {
    @Schema(title = "The OCID of the topic to subscribe to")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> topicId;

    @Schema(title = "The OCID of the compartment")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> compartmentId;

    @Schema(title = "The delivery protocol (e.g. 'EMAIL', 'HTTPS', 'SLACK', 'PAGERDUTY', 'ORACLE_FUNCTIONS')")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> protocol;

    @Schema(title = "The subscription endpoint (e.g. email address or HTTPS URL)")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main", secret = true)
    private Property<String> endpoint;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        String rTopicId = runContext.render(this.topicId).as(String.class).orElseThrow();
        String rCompartmentId = runContext.render(this.compartmentId).as(String.class).orElseThrow();
        String rProtocol = runContext.render(this.protocol).as(String.class).orElseThrow();
        String rEndpoint = runContext.render(this.endpoint).as(String.class).orElseThrow();

        logger.debug("Creating OCI ONS subscription for topic {} with protocol {}", rTopicId, rProtocol);

        try (NotificationDataPlaneClient client = this.createDataPlaneClient(runContext)) {
            CreateSubscriptionDetails details = CreateSubscriptionDetails.builder()
                .topicId(rTopicId)
                .compartmentId(rCompartmentId)
                .protocol(rProtocol)
                .endpoint(rEndpoint)
                .build();

            CreateSubscriptionRequest request = CreateSubscriptionRequest.builder()
                .createSubscriptionDetails(details)
                .build();

            CreateSubscriptionResponse response = client.createSubscription(request);
            Subscription sub = response.getSubscription();

            String subId = sub != null ? sub.getId() : null;
            String state = sub != null && sub.getLifecycleState() != null ? sub.getLifecycleState().getValue() : null;

            logger.info("Created OCI ONS subscription {} (state: {})", subId, state);

            return Output.builder()
                .subscriptionId(subId)
                .topicId(rTopicId)
                .protocol(rProtocol)
                .lifecycleState(state)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the created subscription")
        private final String subscriptionId;

        @Schema(title = "The OCID of the topic subscribed to")
        private final String topicId;

        @Schema(title = "The protocol used")
        private final String protocol;

        @Schema(title = "The lifecycle state of the subscription (e.g. ACTIVE, PENDING)")
        private final String lifecycleState;
    }
}

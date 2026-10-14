package io.kestra.plugin.oci.ons;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.ons.NotificationControlPlaneClient;
import com.oracle.bmc.ons.model.CreateTopicDetails;
import com.oracle.bmc.ons.model.NotificationTopic;
import com.oracle.bmc.ons.requests.CreateTopicRequest;
import com.oracle.bmc.ons.responses.CreateTopicResponse;
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
    title = "Create a new OCI Notification Service (ONS) topic",
    description = "Provisions a new notification topic inside an OCI compartment."
)
@Plugin(
    examples = {
        @Example(
            title = "Create an ONS topic for pipeline alerting",
            full = true,
            code = """
                id: create_alerts_topic
                namespace: company.ops

                tasks:
                  - id: create_topic
                    type: io.kestra.plugin.oci.ons.CreateTopic
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    compartmentId: "{{ secret('OCI_COMPARTMENT_OCID') }}"
                    name: "production-pipeline-alerts"
                    topicDescription: "Alerts topic for mission-critical Kestra executions"
                """
        )
    }
)
public class CreateTopic extends AbstractOciTask implements RunnableTask<CreateTopic.Output> {
    @Schema(title = "The OCID of the compartment where the topic will be created")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> compartmentId;

    @Schema(title = "The name of the topic to create")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> name;

    @Schema(title = "Optional description for the topic")
    @io.kestra.core.models.annotations.PluginProperty(group = "advanced")
    private Property<String> topicDescription;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        String rCompartmentId = runContext.render(this.compartmentId).as(String.class).orElseThrow();
        String rName = runContext.render(this.name).as(String.class).orElseThrow();
        String rDescription = runContext.render(this.topicDescription).as(String.class).orElse(null);

        logger.debug("Creating OCI ONS topic '{}' in compartment {}", rName, rCompartmentId);

        try (NotificationControlPlaneClient client = this.createControlPlaneClient(runContext)) {
            CreateTopicDetails.Builder detailsBuilder = CreateTopicDetails.builder()
                .compartmentId(rCompartmentId)
                .name(rName);

            if (rDescription != null && !rDescription.isBlank()) {
                detailsBuilder.description(rDescription);
            }

            CreateTopicRequest request = CreateTopicRequest.builder()
                .createTopicDetails(detailsBuilder.build())
                .build();

            CreateTopicResponse response = client.createTopic(request);
            NotificationTopic topic = response.getNotificationTopic();

            logger.info("Created OCI ONS topic '{}' with OCID: {}", topic.getName(), topic.getTopicId());

            return Output.builder()
                .topicId(topic.getTopicId())
                .name(topic.getName())
                .compartmentId(topic.getCompartmentId())
                .lifecycleState(topic.getLifecycleState() != null ? topic.getLifecycleState().getValue() : null)
                .timeCreated(topic.getTimeCreated() != null ? topic.getTimeCreated().toInstant() : Instant.now())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the created topic")
        private final String topicId;

        @Schema(title = "The name of the topic")
        private final String name;

        @Schema(title = "The compartment OCID")
        private final String compartmentId;

        @Schema(title = "The lifecycle state of the topic (e.g., ACTIVE, CREATING)")
        private final String lifecycleState;

        @Schema(title = "The timestamp when the topic was created")
        private final Instant timeCreated;
    }
}

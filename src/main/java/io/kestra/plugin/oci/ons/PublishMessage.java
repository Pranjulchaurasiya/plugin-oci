package io.kestra.plugin.oci.ons;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.ons.NotificationDataPlaneClient;
import com.oracle.bmc.ons.model.MessageDetails;
import com.oracle.bmc.ons.requests.PublishMessageRequest;
import com.oracle.bmc.ons.responses.PublishMessageResponse;
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
    title = "Publish a message to an OCI Notification Service (ONS) topic",
    description = "Dispatches structured notification payloads to email, PagerDuty, Slack, or HTTP webhooks subscribed to the ONS topic."
)
@Plugin(
    examples = {
        @Example(
            title = "Send an alert to an OCI Notifications topic on pipeline completion",
            full = true,
            code = """
                id: oci_ons_notify
                namespace: company.ops

                tasks:
                  - id: publish_notification
                    type: io.kestra.plugin.oci.ons.PublishMessage
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    topicId: "{{ secret('OCI_ONS_TOPIC_OCID') }}"
                    title: "Pipeline {{ flow.id }} completed"
                    body: "Execution {{ execution.id }} finished successfully."
                """
        )
    }
)
public class PublishMessage extends AbstractOciTask implements RunnableTask<PublishMessage.Output> {
    @Schema(title = "The OCID of the topic to publish to")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> topicId;

    @Schema(title = "The title / subject of the message")
    @io.kestra.core.models.annotations.PluginProperty(group = "advanced")
    private Property<String> title;

    @Schema(title = "The body of the message")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> body;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        String rTopicId = runContext.render(this.topicId).as(String.class).orElseThrow();
        String rBody = runContext.render(this.body).as(String.class).orElseThrow();
        String rTitle = runContext.render(this.title).as(String.class).orElse(null);

        logger.debug("Publishing message to OCI ONS topic: {}", rTopicId);

        try (NotificationDataPlaneClient client = this.createDataPlaneClient(runContext)) {
            MessageDetails.Builder messageDetailsBuilder = MessageDetails.builder()
                .body(rBody);

            if (rTitle != null && !rTitle.isBlank()) {
                messageDetailsBuilder.title(rTitle);
            }

            PublishMessageRequest request = PublishMessageRequest.builder()
                .topicId(rTopicId)
                .messageDetails(messageDetailsBuilder.build())
                .build();

            PublishMessageResponse response = client.publishMessage(request);
            String messageId = response.getPublishResult() != null ? response.getPublishResult().getMessageId() : null;
            Instant timestamp = response.getPublishResult() != null && response.getPublishResult().getTimeStamp() != null 
                ? response.getPublishResult().getTimeStamp().toInstant() 
                : Instant.now();

            logger.info("Message published to OCI ONS topic {} with messageId: {}", rTopicId, messageId);

            return Output.builder()
                .messageId(messageId)
                .timestamp(timestamp)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The unique identifier of the published message assigned by OCI")
        private final String messageId;

        @Schema(title = "The timestamp when the message was published")
        private final Instant timestamp;
    }
}

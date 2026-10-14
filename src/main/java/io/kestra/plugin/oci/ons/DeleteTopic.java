package io.kestra.plugin.oci.ons;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.ons.NotificationControlPlaneClient;
import com.oracle.bmc.ons.requests.DeleteTopicRequest;
import com.oracle.bmc.ons.responses.DeleteTopicResponse;
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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete an OCI Notification Service (ONS) topic",
    description = "Removes an existing notification topic by its OCID."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete an ONS topic after decommissioning a workload",
            full = true,
            code = """
                id: delete_alerts_topic
                namespace: company.ops

                tasks:
                  - id: delete_topic
                    type: io.kestra.plugin.oci.ons.DeleteTopic
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    topicId: "{{ secret('OCI_TOPIC_OCID_TO_DELETE') }}"
                """
        )
    }
)
public class DeleteTopic extends AbstractOciTask implements RunnableTask<DeleteTopic.Output> {
    @Schema(title = "The OCID of the topic to delete")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> topicId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        String rTopicId = runContext.render(this.topicId).as(String.class).orElseThrow();

        logger.debug("Deleting OCI ONS topic {}", rTopicId);

        try (NotificationControlPlaneClient client = this.createControlPlaneClient(runContext)) {
            DeleteTopicRequest request = DeleteTopicRequest.builder()
                .topicId(rTopicId)
                .build();

            DeleteTopicResponse response = client.deleteTopic(request);
            logger.info("Deleted OCI ONS topic: {}", rTopicId);

            return Output.builder()
                .topicId(rTopicId)
                .opcRequestId(response.getOpcRequestId())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the deleted topic")
        private final String topicId;

        @Schema(title = "Unique Oracle-assigned request identifier")
        private final String opcRequestId;
    }
}

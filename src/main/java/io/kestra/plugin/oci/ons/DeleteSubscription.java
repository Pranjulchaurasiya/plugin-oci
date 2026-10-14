package io.kestra.plugin.oci.ons;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.ons.NotificationDataPlaneClient;
import com.oracle.bmc.ons.requests.DeleteSubscriptionRequest;
import com.oracle.bmc.ons.responses.DeleteSubscriptionResponse;
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
    title = "Delete an OCI Notification Service (ONS) subscription",
    description = "Removes an endpoint subscription from receiving further notifications."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete an ONS subscription by its OCID",
            full = true,
            code = """
                id: delete_topic_subscription
                namespace: company.ops

                tasks:
                  - id: delete_sub
                    type: io.kestra.plugin.oci.ons.DeleteSubscription
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    subscriptionId: "{{ secret('OCI_SUBSCRIPTION_OCID') }}"
                """
        )
    }
)
public class DeleteSubscription extends AbstractOciTask implements RunnableTask<DeleteSubscription.Output> {
    @Schema(title = "The OCID of the subscription to delete")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> subscriptionId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        String rSubscriptionId = runContext.render(this.subscriptionId).as(String.class).orElseThrow();

        logger.debug("Deleting OCI ONS subscription {}", rSubscriptionId);

        try (NotificationDataPlaneClient client = this.createDataPlaneClient(runContext)) {
            DeleteSubscriptionRequest request = DeleteSubscriptionRequest.builder()
                .subscriptionId(rSubscriptionId)
                .build();

            DeleteSubscriptionResponse response = client.deleteSubscription(request);
            logger.info("Deleted OCI ONS subscription: {}", rSubscriptionId);

            return Output.builder()
                .subscriptionId(rSubscriptionId)
                .opcRequestId(response.getOpcRequestId())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the deleted subscription")
        private final String subscriptionId;

        @Schema(title = "Unique Oracle-assigned request identifier")
        private final String opcRequestId;
    }
}

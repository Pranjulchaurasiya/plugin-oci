package io.kestra.plugin.oci.compute;

import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.requests.ListInstancesRequest;
import com.oracle.bmc.core.responses.ListInstancesResponse;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List OCI Compute Instances",
    description = "Lists Oracle Cloud Infrastructure (OCI) compute instances in a compartment with optional filters."
)
@Plugin(
    examples = {
        @Example(
            title = "List running instances in a compartment",
            full = true,
            code = """
                id: list_running_oci_instances
                namespace: company.team

                tasks:
                  - id: list_instances
                    type: io.kestra.plugin.oci.compute.ListInstances
                    region: "eu-frankfurt-1"
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    compartmentId: "ocid1.compartment.oc1..example"
                    lifecycleState: "RUNNING"
                """
        )
    }
)
public class ListInstances extends AbstractOciTask implements RunnableTask<ListInstances.Output> {
    @Schema(title = "The OCID of the compartment")
    @NotNull
    private Property<String> compartmentId;

    @Schema(title = "The availability domain to filter instances by")
    private Property<String> availabilityDomain;

    @Schema(title = "A filter to return only resources that match the given display name exactly")
    private Property<String> displayName;

    @Schema(title = "A filter to return only instances in the specified lifecycle state (e.g. RUNNING, STOPPED, TERMINATED)")
    private Property<String> lifecycleState;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rCompartmentId = runContext.render(this.compartmentId).as(String.class).orElseThrow();
        String rAvailabilityDomain = runContext.render(this.availabilityDomain).as(String.class).orElse(null);
        String rDisplayName = runContext.render(this.displayName).as(String.class).orElse(null);
        String rLifecycleState = runContext.render(this.lifecycleState).as(String.class).orElse(null);

        try (ComputeClient client = ComputeClient.builder().build(this.getAuthenticationDetailsProvider(runContext))) {
            ListInstancesRequest.Builder reqBuilder = ListInstancesRequest.builder()
                .compartmentId(rCompartmentId);

            if (rAvailabilityDomain != null && !rAvailabilityDomain.isBlank()) {
                reqBuilder.availabilityDomain(rAvailabilityDomain);
            }
            if (rDisplayName != null && !rDisplayName.isBlank()) {
                reqBuilder.displayName(rDisplayName);
            }
            if (rLifecycleState != null && !rLifecycleState.isBlank()) {
                reqBuilder.lifecycleState(Instance.LifecycleState.create(rLifecycleState));
            }

            ListInstancesResponse response = client.listInstances(reqBuilder.build());
            List<InstanceItem> instanceItems = new ArrayList<>();

            if (response.getItems() != null) {
                for (Instance inst : response.getItems()) {
                    instanceItems.add(InstanceItem.builder()
                        .instanceId(inst.getId())
                        .displayName(inst.getDisplayName())
                        .lifecycleState(inst.getLifecycleState() != null ? inst.getLifecycleState().getValue() : null)
                        .shape(inst.getShape())
                        .compartmentId(inst.getCompartmentId())
                        .availabilityDomain(inst.getAvailabilityDomain())
                        .region(inst.getRegion())
                        .timeCreated(inst.getTimeCreated())
                        .build()
                    );
                }
            }

            return Output.builder()
                .instances(instanceItems)
                .count(instanceItems.size())
                .build();
        }
    }

    @Builder
    @Getter
    public static class InstanceItem {
        @Schema(title = "The OCID of the instance")
        private final String instanceId;

        @Schema(title = "A user-friendly name of the instance")
        private final String displayName;

        @Schema(title = "The current state of the instance")
        private final String lifecycleState;

        @Schema(title = "The shape of the instance")
        private final String shape;

        @Schema(title = "The OCID of the compartment")
        private final String compartmentId;

        @Schema(title = "The availability domain")
        private final String availabilityDomain;

        @Schema(title = "The region")
        private final String region;

        @Schema(title = "The date and time the instance was created")
        private final Date timeCreated;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The list of instance items matching the query")
        private final List<InstanceItem> instances;

        @Schema(title = "Total number of instances returned")
        private final Integer count;
    }
}

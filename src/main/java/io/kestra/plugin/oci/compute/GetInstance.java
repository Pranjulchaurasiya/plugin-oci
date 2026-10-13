package io.kestra.plugin.oci.compute;

import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.requests.GetInstanceRequest;
import com.oracle.bmc.core.responses.GetInstanceResponse;
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

import java.util.Date;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get OCI Compute Instance",
    description = "Retrieves details and status of an Oracle Cloud Infrastructure (OCI) compute instance by OCID."
)
@Plugin(
    examples = {
        @Example(
            title = "Get instance details and status",
            full = true,
            code = """
                id: get_oci_instance
                namespace: company.team

                tasks:
                  - id: get_instance
                    type: io.kestra.plugin.oci.compute.GetInstance
                    region: "eu-frankfurt-1"
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    instanceId: "ocid1.instance.oc1.eu-frankfurt-1.example"
                """
        )
    }
)
public class GetInstance extends AbstractOciTask implements RunnableTask<GetInstance.Output> {
    @Schema(title = "The OCID of the instance")
    @NotNull
    private Property<String> instanceId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rInstanceId = runContext.render(this.instanceId).as(String.class).orElseThrow();

        try (ComputeClient client = ComputeClient.builder().build(this.getAuthenticationDetailsProvider(runContext))) {
            GetInstanceRequest request = GetInstanceRequest.builder()
                .instanceId(rInstanceId)
                .build();

            GetInstanceResponse response = client.getInstance(request);
            Instance instance = response.getInstance();

            return Output.builder()
                .instanceId(instance.getId())
                .displayName(instance.getDisplayName())
                .lifecycleState(instance.getLifecycleState() != null ? instance.getLifecycleState().getValue() : null)
                .shape(instance.getShape())
                .compartmentId(instance.getCompartmentId())
                .availabilityDomain(instance.getAvailabilityDomain())
                .region(instance.getRegion())
                .timeCreated(instance.getTimeCreated())
                .freeformTags(instance.getFreeformTags())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the instance")
        private final String instanceId;

        @Schema(title = "A user-friendly name of the instance")
        private final String displayName;

        @Schema(title = "The current state of the instance (e.g. RUNNING, STOPPED, TERMINATED)")
        private final String lifecycleState;

        @Schema(title = "The shape of the instance")
        private final String shape;

        @Schema(title = "The OCID of the compartment that contains the instance")
        private final String compartmentId;

        @Schema(title = "The availability domain the instance is running in")
        private final String availabilityDomain;

        @Schema(title = "The region that contains the instance")
        private final String region;

        @Schema(title = "The date and time the instance was created")
        private final Date timeCreated;

        @Schema(title = "Free-form tags for this resource")
        private final Map<String, String> freeformTags;
    }
}

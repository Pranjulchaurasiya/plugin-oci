package io.kestra.plugin.oci.compute;

import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.requests.TerminateInstanceRequest;
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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Terminate OCI Compute Instance",
    description = "Permanently terminates an Oracle Cloud Infrastructure (OCI) compute instance by OCID."
)
@Plugin(
    examples = {
        @Example(
            title = "Terminate an OCI compute instance preserving its boot volume",
            full = true,
            code = """
                id: terminate_oci_instance
                namespace: company.team

                tasks:
                  - id: terminate_instance
                    type: io.kestra.plugin.oci.compute.TerminateInstance
                    region: "eu-frankfurt-1"
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    instanceId: "ocid1.instance.oc1.eu-frankfurt-1.example"
                    preserveBootVolume: true
                """
        )
    }
)
public class TerminateInstance extends AbstractOciTask implements RunnableTask<TerminateInstance.Output> {
    @Schema(title = "The OCID of the instance")
    @NotNull
    private Property<String> instanceId;

    @Schema(title = "Whether to preserve the boot volume associated with the instance")
    @Builder.Default
    private Property<Boolean> preserveBootVolume = Property.of(false);

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rInstanceId = runContext.render(this.instanceId).as(String.class).orElseThrow();
        Boolean rPreserve = runContext.render(this.preserveBootVolume).as(Boolean.class).orElse(false);

        try (ComputeClient client = ComputeClient.builder().build(this.getAuthenticationDetailsProvider(runContext))) {
            TerminateInstanceRequest request = TerminateInstanceRequest.builder()
                .instanceId(rInstanceId)
                .preserveBootVolume(rPreserve)
                .build();

            client.terminateInstance(request);

            return Output.builder()
                .instanceId(rInstanceId)
                .terminated(true)
                .preserveBootVolume(rPreserve)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the terminated instance")
        private final String instanceId;

        @Schema(title = "Whether termination was requested successfully")
        private final Boolean terminated;

        @Schema(title = "Whether the boot volume was preserved")
        private final Boolean preserveBootVolume;
    }
}

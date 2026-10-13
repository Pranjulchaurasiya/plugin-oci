package io.kestra.plugin.oci.compute;

import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.requests.InstanceActionRequest;
import com.oracle.bmc.core.responses.InstanceActionResponse;
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
    title = "Perform OCI Compute Instance Action",
    description = "Performs an action on an Oracle Cloud Infrastructure (OCI) compute instance such as START, STOP, RESET, SOFTRESET, or SOFTSTOP."
)
@Plugin(
    examples = {
        @Example(
            title = "Stop an OCI compute instance gracefully",
            full = true,
            code = """
                id: stop_oci_instance
                namespace: company.team

                tasks:
                  - id: stop_instance
                    type: io.kestra.plugin.oci.compute.InstanceAction
                    region: "eu-frankfurt-1"
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    instanceId: "ocid1.instance.oc1.eu-frankfurt-1.example"
                    action: "SOFTSTOP"
                """
        ),
        @Example(
            title = "Start a stopped OCI compute instance",
            full = true,
            code = """
                id: start_oci_instance
                namespace: company.team

                tasks:
                  - id: start_instance
                    type: io.kestra.plugin.oci.compute.InstanceAction
                    region: "eu-frankfurt-1"
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    instanceId: "ocid1.instance.oc1.eu-frankfurt-1.example"
                    action: "START"
                """
        )
    }
)
public class InstanceAction extends AbstractOciTask implements RunnableTask<InstanceAction.Output> {
    @Schema(title = "The OCID of the instance")
    @NotNull
    private Property<String> instanceId;

    @Schema(title = "The action to perform: START, STOP, RESET, SOFTRESET, or SOFTSTOP")
    @NotNull
    private Property<String> action;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rInstanceId = runContext.render(this.instanceId).as(String.class).orElseThrow();
        String rAction = runContext.render(this.action).as(String.class).orElseThrow().toUpperCase();

        try (ComputeClient client = ComputeClient.builder().build(this.getAuthenticationDetailsProvider(runContext))) {
            InstanceActionRequest request = InstanceActionRequest.builder()
                .instanceId(rInstanceId)
                .action(rAction)
                .build();

            InstanceActionResponse response = client.instanceAction(request);
            Instance instance = response.getInstance();

            return Output.builder()
                .instanceId(instance.getId())
                .action(rAction)
                .lifecycleState(instance.getLifecycleState() != null ? instance.getLifecycleState().getValue() : null)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the instance")
        private final String instanceId;

        @Schema(title = "The action executed on the instance")
        private final String action;

        @Schema(title = "The state of the instance after initiating the action")
        private final String lifecycleState;
    }
}

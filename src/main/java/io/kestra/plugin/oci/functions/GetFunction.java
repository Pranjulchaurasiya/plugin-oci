package io.kestra.plugin.oci.functions;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.functions.FunctionsManagementClient;
import com.oracle.bmc.functions.model.Function;
import com.oracle.bmc.functions.requests.GetFunctionRequest;
import com.oracle.bmc.functions.responses.GetFunctionResponse;
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
    title = "Get details of an OCI serverless function",
    description = "Fetches configuration and runtime details (invoke endpoint, memory, timeout, lifecycle state) of an OCI Function."
)
@Plugin(
    examples = {
        @Example(
            title = "Get OCI Function metadata",
            full = true,
            code = """
                id: get_function_details
                namespace: company.ops

                tasks:
                  - id: get_fn
                    type: io.kestra.plugin.oci.functions.GetFunction
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    functionId: "{{ secret('OCI_FUNCTION_OCID') }}"
                """
        )
    }
)
public class GetFunction extends AbstractOciTask implements RunnableTask<GetFunction.Output> {
    @Schema(title = "The OCID of the function")
    @NotNull
    private Property<String> functionId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rFunctionId = runContext.render(this.functionId).as(String.class).orElseThrow();
        logger.debug("Fetching OCI function details: {}", rFunctionId);

        try (FunctionsManagementClient client = FunctionsManagementClient.builder().build(authProvider)) {
            GetFunctionRequest request = GetFunctionRequest.builder()
                .functionId(rFunctionId)
                .build();

            GetFunctionResponse response = client.getFunction(request);
            Function fn = response.getFunction();

            return Output.builder()
                .id(fn != null ? fn.getId() : null)
                .displayName(fn != null ? fn.getDisplayName() : null)
                .applicationId(fn != null ? fn.getApplicationId() : null)
                .compartmentId(fn != null ? fn.getCompartmentId() : null)
                .image(fn != null ? fn.getImage() : null)
                .memoryInMBs(fn != null ? fn.getMemoryInMBs() : null)
                .timeoutInSeconds(fn != null ? fn.getTimeoutInSeconds() : null)
                .invokeEndpoint(fn != null ? fn.getInvokeEndpoint() : null)
                .lifecycleState(fn != null && fn.getLifecycleState() != null ? fn.getLifecycleState().getValue() : null)
                .timeCreated(fn != null && fn.getTimeCreated() != null ? fn.getTimeCreated().toInstant() : null)
                .timeUpdated(fn != null && fn.getTimeUpdated() != null ? fn.getTimeUpdated().toInstant() : null)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the function")
        private final String id;

        @Schema(title = "The display name of the function")
        private final String displayName;

        @Schema(title = "The OCID of the application the function belongs to")
        private final String applicationId;

        @Schema(title = "The OCID of the compartment containing the function")
        private final String compartmentId;

        @Schema(title = "The qualified container image name of the function")
        private final String image;

        @Schema(title = "Memory allocated to the function in megabytes")
        private final Long memoryInMBs;

        @Schema(title = "Timeout limit for function executions in seconds")
        private final Integer timeoutInSeconds;

        @Schema(title = "The base invoke endpoint URL for the function")
        private final String invokeEndpoint;

        @Schema(title = "The current lifecycle state of the function")
        private final String lifecycleState;

        @Schema(title = "Timestamp when the function was created")
        private final Instant timeCreated;

        @Schema(title = "Timestamp when the function was last updated")
        private final Instant timeUpdated;
    }
}

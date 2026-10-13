package io.kestra.plugin.oci.functions;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.functions.FunctionsManagementClient;
import com.oracle.bmc.functions.requests.DeleteFunctionRequest;
import com.oracle.bmc.functions.responses.DeleteFunctionResponse;
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
    title = "Delete an OCI serverless function",
    description = "Deletes an existing function in Oracle Cloud Infrastructure."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete an OCI Function by OCID",
            full = true,
            code = """
                id: delete_oci_function
                namespace: company.ops

                tasks:
                  - id: delete_fn
                    type: io.kestra.plugin.oci.functions.DeleteFunction
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
public class DeleteFunction extends AbstractOciTask implements RunnableTask<DeleteFunction.Output> {
    @Schema(title = "The OCID of the function to delete")
    @NotNull
    private Property<String> functionId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rFunctionId = runContext.render(this.functionId).as(String.class).orElseThrow();
        logger.debug("Deleting OCI Function: {}", rFunctionId);

        try (FunctionsManagementClient client = FunctionsManagementClient.builder().build(authProvider)) {
            DeleteFunctionRequest request = DeleteFunctionRequest.builder()
                .functionId(rFunctionId)
                .build();

            DeleteFunctionResponse response = client.deleteFunction(request);
            logger.info("Successfully deleted OCI function: {}", rFunctionId);

            return Output.builder()
                .functionId(rFunctionId)
                .opcRequestId(response.getOpcRequestId())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the deleted function")
        private final String functionId;

        @Schema(title = "Unique OCI request identifier (opc-request-id)")
        private final String opcRequestId;
    }
}

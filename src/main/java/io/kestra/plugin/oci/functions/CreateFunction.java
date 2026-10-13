package io.kestra.plugin.oci.functions;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.functions.FunctionsManagementClient;
import com.oracle.bmc.functions.model.CreateFunctionDetails;
import com.oracle.bmc.functions.model.Function;
import com.oracle.bmc.functions.requests.CreateFunctionRequest;
import com.oracle.bmc.functions.responses.CreateFunctionResponse;
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

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a new OCI serverless function in an application",
    description = "Deploys a new function definition into an existing OCI Application."
)
@Plugin(
    examples = {
        @Example(
            title = "Create an OCI Function with container image and memory",
            full = true,
            code = """
                id: create_oci_function
                namespace: company.ops

                tasks:
                  - id: create_fn
                    type: io.kestra.plugin.oci.functions.CreateFunction
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    applicationId: "{{ secret('OCI_APPLICATION_OCID') }}"
                    displayName: "order-processor"
                    image: "fra.ocir.io/tenancy/repo/order-processor:v1"
                    memoryInMBs: 256
                    timeoutInSeconds: 60
                """
        )
    }
)
public class CreateFunction extends AbstractOciTask implements RunnableTask<CreateFunction.Output> {
    @Schema(title = "The OCID of the application")
    @NotNull
    private Property<String> applicationId;

    @Schema(title = "The display name of the function")
    @NotNull
    private Property<String> displayName;

    @Schema(title = "The qualified container image URL")
    @NotNull
    private Property<String> image;

    @Schema(title = "Memory allocated to the function in MB (e.g. 128, 256, 512, 1024)")
    @Builder.Default
    private Property<Long> memoryInMBs = Property.of(128L);

    @Schema(title = "Execution timeout limit in seconds")
    @Builder.Default
    private Property<Integer> timeoutInSeconds = Property.of(30);

    @Schema(title = "Key-value configuration parameters passed into the function runtime environment")
    private Property<Map<String, String>> config;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rAppId = runContext.render(this.applicationId).as(String.class).orElseThrow();
        String rDisplayName = runContext.render(this.displayName).as(String.class).orElseThrow();
        String rImage = runContext.render(this.image).as(String.class).orElseThrow();
        Long rMemory = runContext.render(this.memoryInMBs).as(Long.class).orElse(128L);
        Integer rTimeout = runContext.render(this.timeoutInSeconds).as(Integer.class).orElse(30);
        Map<String, String> rConfig = this.config != null 
            ? runContext.render(this.config).asMap(String.class, String.class) 
            : null;

        logger.debug("Creating OCI Function {} in application {}", rDisplayName, rAppId);

        try (FunctionsManagementClient client = FunctionsManagementClient.builder().build(authProvider)) {
            CreateFunctionDetails.Builder detailsBuilder = CreateFunctionDetails.builder()
                .applicationId(rAppId)
                .displayName(rDisplayName)
                .image(rImage)
                .memoryInMBs(rMemory)
                .timeoutInSeconds(rTimeout);

            if (rConfig != null && !rConfig.isEmpty()) {
                detailsBuilder.config(rConfig);
            }

            CreateFunctionRequest request = CreateFunctionRequest.builder()
                .createFunctionDetails(detailsBuilder.build())
                .build();

            CreateFunctionResponse response = client.createFunction(request);
            Function fn = response.getFunction();
            String id = fn != null ? fn.getId() : null;
            String invokeEndpoint = fn != null ? fn.getInvokeEndpoint() : null;
            String state = fn != null && fn.getLifecycleState() != null ? fn.getLifecycleState().getValue() : null;

            logger.info("Successfully created OCI Function {} with id: {}", rDisplayName, id);

            return Output.builder()
                .id(id)
                .displayName(rDisplayName)
                .invokeEndpoint(invokeEndpoint)
                .lifecycleState(state)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the newly created function")
        private final String id;

        @Schema(title = "The display name of the function")
        private final String displayName;

        @Schema(title = "The base invoke endpoint URL for the function")
        private final String invokeEndpoint;

        @Schema(title = "Current lifecycle state")
        private final String lifecycleState;
    }
}

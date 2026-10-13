package io.kestra.plugin.oci.functions;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.functions.FunctionsManagementClient;
import com.oracle.bmc.functions.model.FunctionSummary;
import com.oracle.bmc.functions.requests.ListFunctionsRequest;
import com.oracle.bmc.functions.responses.ListFunctionsResponse;
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
import java.util.Collections;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List OCI serverless functions in an application",
    description = "Retrieves a list of functions belonging to a specified OCI Application."
)
@Plugin(
    examples = {
        @Example(
            title = "List functions in an application",
            full = true,
            code = """
                id: list_oci_functions
                namespace: company.ops

                tasks:
                  - id: list_fns
                    type: io.kestra.plugin.oci.functions.ListFunctions
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    applicationId: "{{ secret('OCI_APPLICATION_OCID') }}"
                """
        )
    }
)
public class ListFunctions extends AbstractOciTask implements RunnableTask<ListFunctions.Output> {
    @Schema(title = "The OCID of the application")
    @NotNull
    private Property<String> applicationId;

    @Schema(title = "Optional display name filter")
    private Property<String> displayName;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rAppId = runContext.render(this.applicationId).as(String.class).orElseThrow();
        String rDisplayName = runContext.render(this.displayName).as(String.class).orElse(null);

        logger.debug("Listing OCI functions for application: {}", rAppId);

        try (FunctionsManagementClient client = FunctionsManagementClient.builder().build(authProvider)) {
            ListFunctionsRequest.Builder requestBuilder = ListFunctionsRequest.builder()
                .applicationId(rAppId);

            if (rDisplayName != null && !rDisplayName.isBlank()) {
                requestBuilder.displayName(rDisplayName);
            }

            ListFunctionsResponse response = client.listFunctions(requestBuilder.build());
            List<FunctionSummaryItem> items = response.getItems() != null
                ? response.getItems().stream().map(this::mapSummary).toList()
                : Collections.emptyList();

            logger.info("Found {} functions in application {}", items.size(), rAppId);

            return Output.builder()
                .functions(items)
                .count(items.size())
                .build();
        }
    }

    private FunctionSummaryItem mapSummary(FunctionSummary summary) {
        return FunctionSummaryItem.builder()
            .id(summary.getId())
            .displayName(summary.getDisplayName())
            .applicationId(summary.getApplicationId())
            .compartmentId(summary.getCompartmentId())
            .image(summary.getImage())
            .memoryInMBs(summary.getMemoryInMBs())
            .timeoutInSeconds(summary.getTimeoutInSeconds())
            .invokeEndpoint(summary.getInvokeEndpoint())
            .lifecycleState(summary.getLifecycleState() != null ? summary.getLifecycleState().getValue() : null)
            .timeCreated(summary.getTimeCreated() != null ? summary.getTimeCreated().toInstant() : null)
            .timeUpdated(summary.getTimeUpdated() != null ? summary.getTimeUpdated().toInstant() : null)
            .build();
    }

    @Builder
    @Getter
    public static class FunctionSummaryItem {
        @Schema(title = "The OCID of the function")
        private final String id;

        @Schema(title = "The display name of the function")
        private final String displayName;

        @Schema(title = "The OCID of the application")
        private final String applicationId;

        @Schema(title = "The OCID of the compartment")
        private final String compartmentId;

        @Schema(title = "The container image name")
        private final String image;

        @Schema(title = "Allocated memory in megabytes")
        private final Long memoryInMBs;

        @Schema(title = "Execution timeout in seconds")
        private final Integer timeoutInSeconds;

        @Schema(title = "Base invoke endpoint URL")
        private final String invokeEndpoint;

        @Schema(title = "Current lifecycle state")
        private final String lifecycleState;

        @Schema(title = "Creation timestamp")
        private final Instant timeCreated;

        @Schema(title = "Last updated timestamp")
        private final Instant timeUpdated;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "List of functions matching criteria")
        private final List<FunctionSummaryItem> functions;

        @Schema(title = "Total count of functions returned")
        private final Integer count;
    }
}

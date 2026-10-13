package io.kestra.plugin.oci.functions;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.functions.FunctionsInvokeClient;
import com.oracle.bmc.functions.FunctionsManagementClient;
import com.oracle.bmc.functions.requests.GetFunctionRequest;
import com.oracle.bmc.functions.requests.InvokeFunctionRequest;
import com.oracle.bmc.functions.responses.GetFunctionResponse;
import com.oracle.bmc.functions.responses.InvokeFunctionResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.oci.AbstractOciTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Invoke an OCI serverless function",
    description = "Executes an Oracle Cloud Infrastructure Function synchronously, passing an optional payload and capturing the response body."
)
@Plugin(
    examples = {
        @Example(
            title = "Invoke an OCI function with a JSON payload",
            full = true,
            code = """
                id: invoke_oci_function
                namespace: company.ops

                tasks:
                  - id: invoke
                    type: io.kestra.plugin.oci.functions.InvokeFunction
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    functionId: "{{ secret('OCI_FUNCTION_OCID') }}"
                    body:
                      recordId: "12345"
                      action: "process"
                """
        )
    }
)
public class InvokeFunction extends AbstractOciTask implements RunnableTask<InvokeFunction.Output> {
    @Schema(title = "The OCID of the function to invoke")
    @NotNull
    private Property<String> functionId;

    @Schema(
        title = "The invoke endpoint for the function",
        description = "If omitted, it will be automatically fetched using the function OCID."
    )
    private Property<String> invokeEndpoint;

    @Schema(
        title = "The payload to send to the function",
        description = "Can be a string, map, or JSON object."
    )
    private Property<Object> body;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rFunctionId = runContext.render(this.functionId).as(String.class).orElseThrow();
        String rEndpoint = runContext.render(this.invokeEndpoint).as(String.class).orElse(null);

        if (rEndpoint == null || rEndpoint.isBlank()) {
            logger.debug("Resolving invoke endpoint for function OCID: {}", rFunctionId);
            try (FunctionsManagementClient mgmtClient = FunctionsManagementClient.builder().build(authProvider)) {
                GetFunctionResponse getResp = mgmtClient.getFunction(GetFunctionRequest.builder().functionId(rFunctionId).build());
                if (getResp.getFunction() != null) {
                    rEndpoint = getResp.getFunction().getInvokeEndpoint();
                }
            }
        }

        if (rEndpoint == null || rEndpoint.isBlank()) {
            throw new IllegalArgumentException("Unable to determine invoke endpoint for function: " + rFunctionId);
        }

        logger.debug("Invoking OCI Function: {} at endpoint: {}", rFunctionId, rEndpoint);

        byte[] payloadBytes = null;
        if (this.body != null) {
            Object renderedBody = runContext.render(this.body).as(Object.class).orElse(null);
            if (renderedBody != null) {
                if (renderedBody instanceof String str) {
                    payloadBytes = str.getBytes(StandardCharsets.UTF_8);
                } else {
                    payloadBytes = JacksonMapper.ofJson().writeValueAsBytes(renderedBody);
                }
            }
        }

        try (FunctionsInvokeClient invokeClient = FunctionsInvokeClient.builder().endpoint(rEndpoint).build(authProvider)) {
            InvokeFunctionRequest.Builder requestBuilder = InvokeFunctionRequest.builder()
                .functionId(rFunctionId)
                .fnInvokeType(InvokeFunctionRequest.FnInvokeType.Sync);

            if (payloadBytes != null && payloadBytes.length > 0) {
                requestBuilder.invokeFunctionBody(new ByteArrayInputStream(payloadBytes));
            }

            InvokeFunctionResponse response = invokeClient.invokeFunction(requestBuilder.build());
            int statusCode = response.get__httpStatusCode__();
            String responseBody = null;
            InputStream is = response.getInputStream();
            if (is != null) {
                responseBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            logger.info("Successfully invoked function {}, HTTP status: {}", rFunctionId, statusCode);

            return Output.builder()
                .statusCode(statusCode)
                .body(responseBody)
                .opcRequestId(response.getOpcRequestId())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "HTTP status code returned by the function invocation")
        private final Integer statusCode;

        @Schema(title = "Response body returned by the function as a string")
        private final String body;

        @Schema(title = "Unique OCI request identifier (opc-request-id)")
        private final String opcRequestId;
    }
}

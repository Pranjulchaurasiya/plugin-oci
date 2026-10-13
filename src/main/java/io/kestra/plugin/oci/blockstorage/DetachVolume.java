package io.kestra.plugin.oci.blockstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.requests.DetachVolumeRequest;
import com.oracle.bmc.core.responses.DetachVolumeResponse;
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
    title = "Detach an OCI Block Volume from an instance",
    description = "Detaches an attached block volume from an OCI Compute instance using the volume attachment OCID."
)
@Plugin(
    examples = {
        @Example(
            title = "Detach a volume from an instance",
            full = true,
            code = """
                id: detach_volume
                namespace: company.platform

                tasks:
                  - id: detach
                    type: io.kestra.plugin.oci.blockstorage.DetachVolume
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    volumeAttachmentId: "ocid1.volumeattachment.oc1.fra.xxx"
                """
        )
    }
)
public class DetachVolume extends AbstractOciTask implements RunnableTask<DetachVolume.Output> {
    @Schema(title = "The OCID of the volume attachment to detach")
    @NotNull
    private Property<String> volumeAttachmentId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rAttachmentId = runContext.render(this.volumeAttachmentId).as(String.class).orElseThrow();

        logger.debug("Detaching volume attachment: {}", rAttachmentId);

        try (ComputeClient client = ComputeClient.builder().build(authProvider)) {
            DetachVolumeRequest request = DetachVolumeRequest.builder()
                .volumeAttachmentId(rAttachmentId)
                .build();

            DetachVolumeResponse response = client.detachVolume(request);
            logger.info("Successfully initiated detachment for attachment: {}", rAttachmentId);

            return Output.builder()
                .volumeAttachmentId(rAttachmentId)
                .opcRequestId(response.getOpcRequestId())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the detached volume attachment")
        private final String volumeAttachmentId;

        @Schema(title = "Unique OCI request identifier (opc-request-id)")
        private final String opcRequestId;
    }
}

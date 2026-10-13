package io.kestra.plugin.oci.blockstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.requests.DeleteVolumeRequest;
import com.oracle.bmc.core.responses.DeleteVolumeResponse;
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
    title = "Delete an OCI Block Volume",
    description = "Deletes a block storage volume from Oracle Cloud Infrastructure by OCID."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a block volume",
            full = true,
            code = """
                id: delete_volume
                namespace: company.platform

                tasks:
                  - id: delete
                    type: io.kestra.plugin.oci.blockstorage.DeleteVolume
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    volumeId: "ocid1.volume.oc1.fra.xxx"
                """
        )
    }
)
public class DeleteVolume extends AbstractOciTask implements RunnableTask<DeleteVolume.Output> {
    @Schema(title = "The OCID of the volume to delete")
    @NotNull
    private Property<String> volumeId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rVolumeId = runContext.render(this.volumeId).as(String.class).orElseThrow();

        logger.debug("Deleting OCI Block Volume: {}", rVolumeId);

        try (BlockstorageClient client = BlockstorageClient.builder().build(authProvider)) {
            DeleteVolumeRequest request = DeleteVolumeRequest.builder()
                .volumeId(rVolumeId)
                .build();

            DeleteVolumeResponse response = client.deleteVolume(request);
            logger.info("Successfully initiated deletion of OCI Block Volume: {}", rVolumeId);

            return Output.builder()
                .volumeId(rVolumeId)
                .opcRequestId(response.getOpcRequestId())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the deleted volume")
        private final String volumeId;

        @Schema(title = "Unique OCI request identifier (opc-request-id)")
        private final String opcRequestId;
    }
}

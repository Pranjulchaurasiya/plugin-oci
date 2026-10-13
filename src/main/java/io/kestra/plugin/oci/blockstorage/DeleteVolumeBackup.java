package io.kestra.plugin.oci.blockstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.requests.DeleteVolumeBackupRequest;
import com.oracle.bmc.core.responses.DeleteVolumeBackupResponse;
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
    title = "Delete an OCI Block Volume backup",
    description = "Deletes an existing volume backup from Oracle Cloud Infrastructure by OCID."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a volume backup",
            full = true,
            code = """
                id: delete_volume_backup
                namespace: company.platform

                tasks:
                  - id: delete
                    type: io.kestra.plugin.oci.blockstorage.DeleteVolumeBackup
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    volumeBackupId: "ocid1.volumebackup.oc1.fra.xxx"
                """
        )
    }
)
public class DeleteVolumeBackup extends AbstractOciTask implements RunnableTask<DeleteVolumeBackup.Output> {
    @Schema(title = "The OCID of the volume backup to delete")
    @NotNull
    private Property<String> volumeBackupId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rBackupId = runContext.render(this.volumeBackupId).as(String.class).orElseThrow();

        logger.debug("Deleting OCI Block Volume backup: {}", rBackupId);

        try (BlockstorageClient client = BlockstorageClient.builder().build(authProvider)) {
            DeleteVolumeBackupRequest request = DeleteVolumeBackupRequest.builder()
                .volumeBackupId(rBackupId)
                .build();

            DeleteVolumeBackupResponse response = client.deleteVolumeBackup(request);
            logger.info("Successfully initiated deletion of OCI Block Volume backup: {}", rBackupId);

            return Output.builder()
                .volumeBackupId(rBackupId)
                .opcRequestId(response.getOpcRequestId())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the deleted volume backup")
        private final String volumeBackupId;

        @Schema(title = "Unique OCI request identifier (opc-request-id)")
        private final String opcRequestId;
    }
}

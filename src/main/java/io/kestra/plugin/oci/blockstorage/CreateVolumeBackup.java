package io.kestra.plugin.oci.blockstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.model.CreateVolumeBackupDetails;
import com.oracle.bmc.core.model.VolumeBackup;
import com.oracle.bmc.core.requests.CreateVolumeBackupRequest;
import com.oracle.bmc.core.responses.CreateVolumeBackupResponse;
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
    title = "Create an OCI Block Volume backup",
    description = "Creates a point-in-time full or incremental backup of an OCI block storage volume."
)
@Plugin(
    examples = {
        @Example(
            title = "Create an on-demand incremental backup of a volume",
            full = true,
            code = """
                id: create_volume_backup
                namespace: company.platform

                tasks:
                  - id: backup
                    type: io.kestra.plugin.oci.blockstorage.CreateVolumeBackup
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    volumeId: "ocid1.volume.oc1.fra.xxx"
                    displayName: "daily-backup"
                    backupType: INCREMENTAL
                """
        )
    }
)
public class CreateVolumeBackup extends AbstractOciTask implements RunnableTask<CreateVolumeBackup.Output> {
    @Schema(title = "The OCID of the volume to back up")
    @NotNull
    private Property<String> volumeId;

    @Schema(title = "A user-friendly display name for the backup")
    private Property<String> displayName;

    @Schema(title = "The type of backup to create: FULL or INCREMENTAL")
    @Builder.Default
    private Property<BackupType> backupType = Property.of(BackupType.INCREMENTAL);

    public enum BackupType {
        FULL,
        INCREMENTAL
    }

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rVolumeId = runContext.render(this.volumeId).as(String.class).orElseThrow();
        String rDisplayName = runContext.render(this.displayName).as(String.class).orElse(null);
        BackupType rType = runContext.render(this.backupType).as(BackupType.class).orElse(BackupType.INCREMENTAL);

        logger.debug("Creating {} backup for volume {}", rType, rVolumeId);

        try (BlockstorageClient client = BlockstorageClient.builder().build(authProvider)) {
            CreateVolumeBackupDetails.Type sdkType = rType == BackupType.FULL
                ? CreateVolumeBackupDetails.Type.Full
                : CreateVolumeBackupDetails.Type.Incremental;

            CreateVolumeBackupDetails.Builder detailsBuilder = CreateVolumeBackupDetails.builder()
                .volumeId(rVolumeId)
                .type(sdkType);

            if (rDisplayName != null && !rDisplayName.isBlank()) {
                detailsBuilder.displayName(rDisplayName);
            }

            CreateVolumeBackupRequest request = CreateVolumeBackupRequest.builder()
                .createVolumeBackupDetails(detailsBuilder.build())
                .build();

            CreateVolumeBackupResponse response = client.createVolumeBackup(request);
            VolumeBackup backup = response.getVolumeBackup();

            logger.info("Successfully created backup {} for volume {}", backup.getId(), rVolumeId);

            return Output.builder()
                .backupId(backup.getId())
                .displayName(backup.getDisplayName())
                .volumeId(backup.getVolumeId())
                .sizeInGBs(backup.getSizeInGBs())
                .type(backup.getType() != null ? backup.getType().getValue() : null)
                .lifecycleState(backup.getLifecycleState() != null ? backup.getLifecycleState().getValue() : null)
                .timeCreated(backup.getTimeCreated() != null ? backup.getTimeCreated().toInstant() : null)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the created volume backup")
        private final String backupId;

        @Schema(title = "Display name of the backup")
        private final String displayName;

        @Schema(title = "The OCID of the volume backed up")
        private final String volumeId;

        @Schema(title = "Size of the volume in gigabytes")
        private final Long sizeInGBs;

        @Schema(title = "Type of backup created (FULL or INCREMENTAL)")
        private final String type;

        @Schema(title = "Current lifecycle state of the backup")
        private final String lifecycleState;

        @Schema(title = "Timestamp when the backup was created")
        private final Instant timeCreated;
    }
}

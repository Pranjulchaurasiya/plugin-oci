package io.kestra.plugin.oci.blockstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.model.AttachIScsiVolumeDetails;
import com.oracle.bmc.core.model.AttachParavirtualizedVolumeDetails;
import com.oracle.bmc.core.model.AttachVolumeDetails;
import com.oracle.bmc.core.model.VolumeAttachment;
import com.oracle.bmc.core.requests.AttachVolumeRequest;
import com.oracle.bmc.core.responses.AttachVolumeResponse;
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
    title = "Attach an OCI Block Volume to a Compute instance",
    description = "Attaches a block storage volume to an OCI Compute instance using paravirtualized or iSCSI attachment type."
)
@Plugin(
    examples = {
        @Example(
            title = "Attach a volume to a compute instance",
            full = true,
            code = """
                id: attach_volume
                namespace: company.platform

                tasks:
                  - id: attach
                    type: io.kestra.plugin.oci.blockstorage.AttachVolume
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    instanceId: "ocid1.instance.oc1.fra.xxx"
                    volumeId: "ocid1.volume.oc1.fra.yyy"
                    attachmentType: PARAVIRTUALIZED
                """
        )
    }
)
public class AttachVolume extends AbstractOciTask implements RunnableTask<AttachVolume.Output> {
    @Schema(title = "The OCID of the compute instance")
    @NotNull
    private Property<String> instanceId;

    @Schema(title = "The OCID of the block volume to attach")
    @NotNull
    private Property<String> volumeId;

    @Schema(title = "Optional display name for the volume attachment")
    private Property<String> displayName;

    @Schema(title = "The attachment type: PARAVIRTUALIZED or ISCSI")
    @Builder.Default
    private Property<AttachmentType> attachmentType = Property.of(AttachmentType.PARAVIRTUALIZED);

    public enum AttachmentType {
        PARAVIRTUALIZED,
        ISCSI
    }

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rInstanceId = runContext.render(this.instanceId).as(String.class).orElseThrow();
        String rVolumeId = runContext.render(this.volumeId).as(String.class).orElseThrow();
        String rDisplayName = runContext.render(this.displayName).as(String.class).orElse(null);
        AttachmentType rType = runContext.render(this.attachmentType).as(AttachmentType.class).orElse(AttachmentType.PARAVIRTUALIZED);

        logger.debug("Attaching volume {} to instance {} with type {}", rVolumeId, rInstanceId, rType);

        try (ComputeClient client = ComputeClient.builder().build(authProvider)) {
            AttachVolumeDetails details;
            if (rType == AttachmentType.ISCSI) {
                AttachIScsiVolumeDetails.Builder builder = AttachIScsiVolumeDetails.builder()
                    .instanceId(rInstanceId)
                    .volumeId(rVolumeId);
                if (rDisplayName != null && !rDisplayName.isBlank()) {
                    builder.displayName(rDisplayName);
                }
                details = builder.build();
            } else {
                AttachParavirtualizedVolumeDetails.Builder builder = AttachParavirtualizedVolumeDetails.builder()
                    .instanceId(rInstanceId)
                    .volumeId(rVolumeId);
                if (rDisplayName != null && !rDisplayName.isBlank()) {
                    builder.displayName(rDisplayName);
                }
                details = builder.build();
            }

            AttachVolumeRequest request = AttachVolumeRequest.builder()
                .attachVolumeDetails(details)
                .build();

            AttachVolumeResponse response = client.attachVolume(request);
            VolumeAttachment attachment = response.getVolumeAttachment();

            logger.info("Successfully attached volume {} to instance {} (attachment OCID: {})",
                rVolumeId, rInstanceId, attachment.getId());

            return Output.builder()
                .attachmentId(attachment.getId())
                .instanceId(attachment.getInstanceId())
                .volumeId(attachment.getVolumeId())
                .displayName(attachment.getDisplayName())
                .attachmentType(rType.name())
                .lifecycleState(attachment.getLifecycleState() != null ? attachment.getLifecycleState().getValue() : null)
                .timeCreated(attachment.getTimeCreated() != null ? attachment.getTimeCreated().toInstant() : null)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the volume attachment")
        private final String attachmentId;

        @Schema(title = "The OCID of the instance")
        private final String instanceId;

        @Schema(title = "The OCID of the volume")
        private final String volumeId;

        @Schema(title = "Display name of the attachment")
        private final String displayName;

        @Schema(title = "Type of attachment (PARAVIRTUALIZED or ISCSI)")
        private final String attachmentType;

        @Schema(title = "Current lifecycle state of the attachment")
        private final String lifecycleState;

        @Schema(title = "Timestamp when the attachment was created")
        private final Instant timeCreated;
    }
}

package io.kestra.plugin.oci.blockstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.model.Volume;
import com.oracle.bmc.core.requests.GetVolumeRequest;
import com.oracle.bmc.core.responses.GetVolumeResponse;
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
    title = "Fetch details of an OCI Block Volume",
    description = "Retrieves information and current lifecycle state of an OCI block storage volume."
)
@Plugin(
    examples = {
        @Example(
            title = "Get volume details",
            full = true,
            code = """
                id: get_volume
                namespace: company.platform

                tasks:
                  - id: get
                    type: io.kestra.plugin.oci.blockstorage.GetVolume
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
public class GetVolume extends AbstractOciTask implements RunnableTask<GetVolume.Output> {
    @Schema(title = "The OCID of the volume to retrieve")
    @NotNull
    private Property<String> volumeId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rVolumeId = runContext.render(this.volumeId).as(String.class).orElseThrow();

        logger.debug("Fetching OCI Block Volume: {}", rVolumeId);

        try (BlockstorageClient client = BlockstorageClient.builder().build(authProvider)) {
            GetVolumeRequest request = GetVolumeRequest.builder()
                .volumeId(rVolumeId)
                .build();

            GetVolumeResponse response = client.getVolume(request);
            Volume volume = response.getVolume();

            logger.info("Retrieved volume {} with lifecycle state {}", volume.getId(), volume.getLifecycleState());

            return Output.builder()
                .volumeId(volume.getId())
                .displayName(volume.getDisplayName())
                .sizeInGBs(volume.getSizeInGBs())
                .availabilityDomain(volume.getAvailabilityDomain())
                .compartmentId(volume.getCompartmentId())
                .vpusPerGB(volume.getVpusPerGB())
                .lifecycleState(volume.getLifecycleState() != null ? volume.getLifecycleState().getValue() : null)
                .timeCreated(volume.getTimeCreated() != null ? volume.getTimeCreated().toInstant() : null)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the volume")
        private final String volumeId;

        @Schema(title = "Display name of the volume")
        private final String displayName;

        @Schema(title = "Size of the volume in gigabytes")
        private final Long sizeInGBs;

        @Schema(title = "Availability domain of the volume")
        private final String availabilityDomain;

        @Schema(title = "Compartment OCID of the volume")
        private final String compartmentId;

        @Schema(title = "Volume performance units per GB")
        private final Long vpusPerGB;

        @Schema(title = "Current lifecycle state of the volume")
        private final String lifecycleState;

        @Schema(title = "Timestamp when the volume was created")
        private final Instant timeCreated;
    }
}

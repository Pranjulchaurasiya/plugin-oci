package io.kestra.plugin.oci.blockstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.model.CreateVolumeDetails;
import com.oracle.bmc.core.model.Volume;
import com.oracle.bmc.core.requests.CreateVolumeRequest;
import com.oracle.bmc.core.responses.CreateVolumeResponse;
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
    title = "Provision a new OCI Block Volume",
    description = "Provisions a block storage volume within an OCI compartment and availability domain."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a 100GB block volume",
            full = true,
            code = """
                id: create_volume
                namespace: company.platform

                tasks:
                  - id: create
                    type: io.kestra.plugin.oci.blockstorage.CreateVolume
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    compartmentId: "{{ secret('OCI_COMPARTMENT_OCID') }}"
                    availabilityDomain: "AD-1"
                    displayName: "data-volume"
                    sizeInGBs: 100
                """
        )
    }
)
public class CreateVolume extends AbstractOciTask implements RunnableTask<CreateVolume.Output> {
    @Schema(title = "The compartment OCID in which to create the volume")
    @NotNull
    private Property<String> compartmentId;

    @Schema(title = "The availability domain where the volume will reside")
    @NotNull
    private Property<String> availabilityDomain;

    @Schema(title = "A user-friendly display name for the volume")
    private Property<String> displayName;

    @Schema(title = "The size of the volume in gigabytes (GBs)")
    private Property<Long> sizeInGBs;

    @Schema(title = "Volume performance units per GB (e.g. 10 for Balanced, 20 for Higher Performance)")
    private Property<Long> vpusPerGB;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rCompartment = runContext.render(this.compartmentId).as(String.class).orElseThrow();
        String rAd = runContext.render(this.availabilityDomain).as(String.class).orElseThrow();
        String rDisplayName = runContext.render(this.displayName).as(String.class).orElse(null);
        Long rSizeInGBs = runContext.render(this.sizeInGBs).as(Long.class).orElse(null);
        Long rVpus = runContext.render(this.vpusPerGB).as(Long.class).orElse(null);

        logger.debug("Creating OCI Block Volume in compartment {} and AD {}", rCompartment, rAd);

        try (BlockstorageClient client = BlockstorageClient.builder().build(authProvider)) {
            CreateVolumeDetails.Builder detailsBuilder = CreateVolumeDetails.builder()
                .compartmentId(rCompartment)
                .availabilityDomain(rAd);

            if (rDisplayName != null && !rDisplayName.isBlank()) {
                detailsBuilder.displayName(rDisplayName);
            }
            if (rSizeInGBs != null) {
                detailsBuilder.sizeInGBs(rSizeInGBs);
            }
            if (rVpus != null) {
                detailsBuilder.vpusPerGB(rVpus);
            }

            CreateVolumeRequest request = CreateVolumeRequest.builder()
                .createVolumeDetails(detailsBuilder.build())
                .build();

            CreateVolumeResponse response = client.createVolume(request);
            Volume volume = response.getVolume();

            logger.info("Successfully created OCI Block Volume: {} (OCID: {})", volume.getDisplayName(), volume.getId());

            return Output.builder()
                .volumeId(volume.getId())
                .displayName(volume.getDisplayName())
                .sizeInGBs(volume.getSizeInGBs())
                .availabilityDomain(volume.getAvailabilityDomain())
                .compartmentId(volume.getCompartmentId())
                .lifecycleState(volume.getLifecycleState() != null ? volume.getLifecycleState().getValue() : null)
                .timeCreated(volume.getTimeCreated() != null ? volume.getTimeCreated().toInstant() : null)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The OCID of the created volume")
        private final String volumeId;

        @Schema(title = "Display name of the volume")
        private final String displayName;

        @Schema(title = "Size of the volume in gigabytes")
        private final Long sizeInGBs;

        @Schema(title = "Availability domain of the volume")
        private final String availabilityDomain;

        @Schema(title = "Compartment OCID of the volume")
        private final String compartmentId;

        @Schema(title = "Current lifecycle state of the volume")
        private final String lifecycleState;

        @Schema(title = "Timestamp when the volume was created")
        private final Instant timeCreated;
    }
}

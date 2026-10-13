package io.kestra.plugin.oci.blockstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.model.Volume;
import com.oracle.bmc.core.requests.ListVolumesRequest;
import com.oracle.bmc.core.responses.ListVolumesResponse;
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
    title = "List OCI Block Volumes in a compartment",
    description = "Lists block storage volumes in the specified compartment with optional availability domain and display name filtering."
)
@Plugin(
    examples = {
        @Example(
            title = "List volumes in a compartment",
            full = true,
            code = """
                id: list_volumes
                namespace: company.platform

                tasks:
                  - id: list
                    type: io.kestra.plugin.oci.blockstorage.ListVolumes
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    compartmentId: "{{ secret('OCI_COMPARTMENT_OCID') }}"
                """
        )
    }
)
public class ListVolumes extends AbstractOciTask implements RunnableTask<ListVolumes.Output> {
    @Schema(title = "The compartment OCID in which to list volumes")
    @NotNull
    private Property<String> compartmentId;

    @Schema(title = "Optional availability domain filter")
    private Property<String> availabilityDomain;

    @Schema(title = "Optional display name filter")
    private Property<String> displayName;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rCompartment = runContext.render(this.compartmentId).as(String.class).orElseThrow();
        String rAd = runContext.render(this.availabilityDomain).as(String.class).orElse(null);
        String rDisplayName = runContext.render(this.displayName).as(String.class).orElse(null);

        logger.debug("Listing OCI Block Volumes in compartment: {}", rCompartment);

        try (BlockstorageClient client = BlockstorageClient.builder().build(authProvider)) {
            ListVolumesRequest.Builder requestBuilder = ListVolumesRequest.builder()
                .compartmentId(rCompartment);

            if (rAd != null && !rAd.isBlank()) {
                requestBuilder.availabilityDomain(rAd);
            }
            if (rDisplayName != null && !rDisplayName.isBlank()) {
                requestBuilder.displayName(rDisplayName);
            }

            ListVolumesResponse response = client.listVolumes(requestBuilder.build());
            List<Volume> rawVolumes = response.getItems();

            List<VolumeSummaryItem> items = rawVolumes != null
                ? rawVolumes.stream().map(this::mapVolumeSummary).toList()
                : Collections.emptyList();

            logger.info("Found {} OCI Block Volumes in compartment {}", items.size(), rCompartment);

            return Output.builder()
                .volumes(items)
                .count(items.size())
                .build();
        }
    }

    private VolumeSummaryItem mapVolumeSummary(Volume v) {
        return VolumeSummaryItem.builder()
            .volumeId(v.getId())
            .displayName(v.getDisplayName())
            .sizeInGBs(v.getSizeInGBs())
            .availabilityDomain(v.getAvailabilityDomain())
            .compartmentId(v.getCompartmentId())
            .lifecycleState(v.getLifecycleState() != null ? v.getLifecycleState().getValue() : null)
            .timeCreated(v.getTimeCreated() != null ? v.getTimeCreated().toInstant() : null)
            .build();
    }

    @Builder
    @Getter
    public static class VolumeSummaryItem {
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

        @Schema(title = "Current lifecycle state of the volume")
        private final String lifecycleState;

        @Schema(title = "Timestamp when the volume was created")
        private final Instant timeCreated;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "List of block volumes matching query")
        private final List<VolumeSummaryItem> volumes;

        @Schema(title = "Total number of volumes returned")
        private final Integer count;
    }
}

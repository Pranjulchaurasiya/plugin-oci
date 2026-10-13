package io.kestra.plugin.oci.blockstorage;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class VolumeManagementTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testCreateVolumeOutput() {
        Instant now = Instant.now();
        CreateVolume.Output output = CreateVolume.Output.builder()
            .volumeId("ocid1.volume.oc1.fra.samplevol123")
            .displayName("pg-data-volume")
            .sizeInGBs(150L)
            .availabilityDomain("AD-1")
            .compartmentId("ocid1.compartment.oc1..samplecmp")
            .lifecycleState("AVAILABLE")
            .timeCreated(now)
            .build();

        assertThat(output.getVolumeId(), is("ocid1.volume.oc1.fra.samplevol123"));
        assertThat(output.getDisplayName(), is("pg-data-volume"));
        assertThat(output.getSizeInGBs(), is(150L));
        assertThat(output.getLifecycleState(), is("AVAILABLE"));
        assertThat(output.getTimeCreated(), is(now));
    }

    @Test
    void testGetVolumeOutput() {
        GetVolume.Output output = GetVolume.Output.builder()
            .volumeId("ocid1.volume.oc1.fra.vol456")
            .displayName("redis-cache-vol")
            .sizeInGBs(50L)
            .availabilityDomain("AD-2")
            .compartmentId("ocid1.compartment.oc1..samplecmp")
            .vpusPerGB(10L)
            .lifecycleState("AVAILABLE")
            .build();

        assertThat(output.getVolumeId(), is("ocid1.volume.oc1.fra.vol456"));
        assertThat(output.getSizeInGBs(), is(50L));
        assertThat(output.getVpusPerGB(), is(10L));
        assertThat(output.getLifecycleState(), is("AVAILABLE"));
    }

    @Test
    void testDeleteVolumeOutput() {
        DeleteVolume.Output output = DeleteVolume.Output.builder()
            .volumeId("ocid1.volume.oc1.fra.vol789")
            .opcRequestId("req-del-vol-123")
            .build();

        assertThat(output.getVolumeId(), is("ocid1.volume.oc1.fra.vol789"));
        assertThat(output.getOpcRequestId(), is("req-del-vol-123"));
    }

    @Test
    void testListVolumesOutput() {
        ListVolumes.VolumeSummaryItem item = ListVolumes.VolumeSummaryItem.builder()
            .volumeId("ocid1.volume.oc1.fra.v1")
            .displayName("worker-scratch")
            .sizeInGBs(100L)
            .availabilityDomain("AD-1")
            .compartmentId("ocid1.compartment.oc1..samplecmp")
            .lifecycleState("AVAILABLE")
            .build();

        ListVolumes.Output output = ListVolumes.Output.builder()
            .volumes(List.of(item))
            .count(1)
            .build();

        assertThat(output.getCount(), is(1));
        assertThat(output.getVolumes().getFirst().getDisplayName(), is("worker-scratch"));
    }

    @Test
    void testPropertyRendering() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "cmp", "ocid1.compartment.oc1..samplecmp",
            "ad", "AD-3",
            "name", "app-storage",
            "size", 250L
        ));

        CreateVolume task = CreateVolume.builder()
            .region(Property.of("eu-frankfurt-1"))
            .tenancyOcid(Property.of("ocid1.tenancy.oc1..sampletenancy"))
            .userId(Property.of("ocid1.user.oc1..sampleuser"))
            .fingerprint(Property.of("20:3b:97:13:55:1c:5b:0d:d3:37:d0:02:ee:3a:3c:22"))
            .privateKey(Property.of("-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----"))
            .compartmentId(Property.ofExpression("{{ cmp }}"))
            .availabilityDomain(Property.ofExpression("{{ ad }}"))
            .displayName(Property.ofExpression("{{ name }}"))
            .sizeInGBs(Property.of(250L))
            .build();

        assertThat(runContext.render(task.getCompartmentId()).as(String.class).orElse(null), is("ocid1.compartment.oc1..samplecmp"));
        assertThat(runContext.render(task.getAvailabilityDomain()).as(String.class).orElse(null), is("AD-3"));
        assertThat(runContext.render(task.getDisplayName()).as(String.class).orElse(null), is("app-storage"));
        assertThat(runContext.render(task.getSizeInGBs()).as(Long.class).orElse(null), is(250L));
    }

    @Test
    void testMissingCredentialsThrows() {
        RunContext runContext = runContextFactory.of();

        DeleteVolume task = DeleteVolume.builder()
            .volumeId(Property.of("ocid1.volume.oc1.fra.xxx"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}

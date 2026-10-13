package io.kestra.plugin.oci.blockstorage;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class BackupAndAttachmentTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testCreateVolumeBackupOutput() {
        Instant now = Instant.now();
        CreateVolumeBackup.Output output = CreateVolumeBackup.Output.builder()
            .backupId("ocid1.volumebackup.oc1.fra.bkp123")
            .displayName("nightly-backup-20261013")
            .volumeId("ocid1.volume.oc1.fra.vol123")
            .sizeInGBs(100L)
            .type("INCREMENTAL")
            .lifecycleState("AVAILABLE")
            .timeCreated(now)
            .build();

        assertThat(output.getBackupId(), is("ocid1.volumebackup.oc1.fra.bkp123"));
        assertThat(output.getVolumeId(), is("ocid1.volume.oc1.fra.vol123"));
        assertThat(output.getType(), is("INCREMENTAL"));
        assertThat(output.getLifecycleState(), is("AVAILABLE"));
    }

    @Test
    void testDeleteVolumeBackupOutput() {
        DeleteVolumeBackup.Output output = DeleteVolumeBackup.Output.builder()
            .volumeBackupId("ocid1.volumebackup.oc1.fra.bkp456")
            .opcRequestId("req-del-bkp-789")
            .build();

        assertThat(output.getVolumeBackupId(), is("ocid1.volumebackup.oc1.fra.bkp456"));
        assertThat(output.getOpcRequestId(), is("req-del-bkp-789"));
    }

    @Test
    void testAttachVolumeOutput() {
        Instant now = Instant.now();
        AttachVolume.Output output = AttachVolume.Output.builder()
            .attachmentId("ocid1.volumeattachment.oc1.fra.att123")
            .instanceId("ocid1.instance.oc1.fra.inst123")
            .volumeId("ocid1.volume.oc1.fra.vol123")
            .displayName("vol-att-disk")
            .attachmentType("PARAVIRTUALIZED")
            .lifecycleState("ATTACHED")
            .timeCreated(now)
            .build();

        assertThat(output.getAttachmentId(), is("ocid1.volumeattachment.oc1.fra.att123"));
        assertThat(output.getInstanceId(), is("ocid1.instance.oc1.fra.inst123"));
        assertThat(output.getAttachmentType(), is("PARAVIRTUALIZED"));
        assertThat(output.getLifecycleState(), is("ATTACHED"));
    }

    @Test
    void testDetachVolumeOutput() {
        DetachVolume.Output output = DetachVolume.Output.builder()
            .volumeAttachmentId("ocid1.volumeattachment.oc1.fra.att456")
            .opcRequestId("req-detach-999")
            .build();

        assertThat(output.getVolumeAttachmentId(), is("ocid1.volumeattachment.oc1.fra.att456"));
        assertThat(output.getOpcRequestId(), is("req-detach-999"));
    }

    @Test
    void testPropertyRendering() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "instId", "ocid1.instance.oc1.fra.sampleinstance",
            "volId", "ocid1.volume.oc1.fra.samplevol",
            "attName", "data-drive"
        ));

        AttachVolume attachTask = AttachVolume.builder()
            .region(Property.of("eu-frankfurt-1"))
            .tenancyOcid(Property.of("ocid1.tenancy.oc1..sampletenancy"))
            .userId(Property.of("ocid1.user.oc1..sampleuser"))
            .fingerprint(Property.of("20:3b:97:13:55:1c:5b:0d:d3:37:d0:02:ee:3a:3c:22"))
            .privateKey(Property.of("-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----"))
            .instanceId(Property.ofExpression("{{ instId }}"))
            .volumeId(Property.ofExpression("{{ volId }}"))
            .displayName(Property.ofExpression("{{ attName }}"))
            .attachmentType(Property.of(AttachVolume.AttachmentType.PARAVIRTUALIZED))
            .build();

        assertThat(runContext.render(attachTask.getInstanceId()).as(String.class).orElse(null), is("ocid1.instance.oc1.fra.sampleinstance"));
        assertThat(runContext.render(attachTask.getVolumeId()).as(String.class).orElse(null), is("ocid1.volume.oc1.fra.samplevol"));
        assertThat(runContext.render(attachTask.getDisplayName()).as(String.class).orElse(null), is("data-drive"));
    }

    @Test
    void testMissingCredentialsThrows() {
        RunContext runContext = runContextFactory.of();

        CreateVolumeBackup task = CreateVolumeBackup.builder()
            .volumeId(Property.of("ocid1.volume.oc1.fra.samplevol"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}

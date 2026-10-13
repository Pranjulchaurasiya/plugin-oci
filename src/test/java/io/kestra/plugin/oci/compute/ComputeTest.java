package io.kestra.plugin.oci.compute;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ComputeTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testGetInstanceOutput() {
        Date now = new Date();
        GetInstance.Output output = GetInstance.Output.builder()
            .instanceId("ocid1.instance.oc1.fra.example123")
            .displayName("kestra-worker-node")
            .lifecycleState("RUNNING")
            .shape("VM.Standard.E4.Flex")
            .compartmentId("ocid1.compartment.oc1..examplecmp")
            .availabilityDomain("AD-1")
            .region("eu-frankfurt-1")
            .timeCreated(now)
            .freeformTags(Map.of("Environment", "Production"))
            .build();

        assertThat(output.getInstanceId(), is("ocid1.instance.oc1.fra.example123"));
        assertThat(output.getDisplayName(), is("kestra-worker-node"));
        assertThat(output.getLifecycleState(), is("RUNNING"));
        assertThat(output.getShape(), is("VM.Standard.E4.Flex"));
        assertThat(output.getCompartmentId(), is("ocid1.compartment.oc1..examplecmp"));
        assertThat(output.getRegion(), is("eu-frankfurt-1"));
        assertThat(output.getTimeCreated(), is(now));
        assertThat(output.getFreeformTags().get("Environment"), is("Production"));
    }

    @Test
    void testInstanceActionOutput() {
        InstanceAction.Output output = InstanceAction.Output.builder()
            .instanceId("ocid1.instance.oc1.fra.example456")
            .action("SOFTSTOP")
            .lifecycleState("STOPPING")
            .build();

        assertThat(output.getInstanceId(), is("ocid1.instance.oc1.fra.example456"));
        assertThat(output.getAction(), is("SOFTSTOP"));
        assertThat(output.getLifecycleState(), is("STOPPING"));
    }

    @Test
    void testTerminateInstanceOutput() {
        TerminateInstance.Output output = TerminateInstance.Output.builder()
            .instanceId("ocid1.instance.oc1.fra.example789")
            .terminated(true)
            .preserveBootVolume(true)
            .build();

        assertThat(output.getInstanceId(), is("ocid1.instance.oc1.fra.example789"));
        assertThat(output.getTerminated(), is(true));
        assertThat(output.getPreserveBootVolume(), is(true));
    }

    @Test
    void testListInstancesOutput() {
        ListInstances.InstanceItem item = ListInstances.InstanceItem.builder()
            .instanceId("ocid1.instance.oc1.fra.inst1")
            .displayName("kestra-db-master")
            .lifecycleState("RUNNING")
            .shape("VM.Standard3.Flex")
            .compartmentId("ocid1.compartment.oc1..cmp")
            .availabilityDomain("AD-1")
            .region("eu-frankfurt-1")
            .build();

        ListInstances.Output output = ListInstances.Output.builder()
            .instances(List.of(item))
            .count(1)
            .build();

        assertThat(output.getCount(), is(1));
        assertThat(output.getInstances().getFirst().getDisplayName(), is("kestra-db-master"));
        assertThat(output.getInstances().getFirst().getLifecycleState(), is("RUNNING"));
    }

    @Test
    void testPropertyRendering() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "instId", "ocid1.instance.oc1.fra.inst-abc",
            "act", "START",
            "cmp", "ocid1.compartment.oc1..cmp-xyz"
        ));

        InstanceAction actionTask = InstanceAction.builder()
            .region(Property.of("eu-frankfurt-1"))
            .tenancyOcid(Property.of("ocid1.tenancy.oc1..sampletenancy"))
            .userId(Property.of("ocid1.user.oc1..sampleuser"))
            .fingerprint(Property.of("20:3b:97:13:55:1c:5b:0d:d3:37:d0:02:ee:3a:3c:22"))
            .privateKey(Property.of("-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----"))
            .instanceId(Property.ofExpression("{{ instId }}"))
            .action(Property.ofExpression("{{ act }}"))
            .build();

        assertThat(runContext.render(actionTask.getInstanceId()).as(String.class).orElse(null), is("ocid1.instance.oc1.fra.inst-abc"));
        assertThat(runContext.render(actionTask.getAction()).as(String.class).orElse(null), is("START"));

        ListInstances listTask = ListInstances.builder()
            .compartmentId(Property.ofExpression("{{ cmp }}"))
            .lifecycleState(Property.of("RUNNING"))
            .build();

        assertThat(runContext.render(listTask.getCompartmentId()).as(String.class).orElse(null), is("ocid1.compartment.oc1..cmp-xyz"));
        assertThat(runContext.render(listTask.getLifecycleState()).as(String.class).orElse(null), is("RUNNING"));
    }

    @Test
    void testMissingCredentialsThrows() {
        RunContext runContext = runContextFactory.of();

        GetInstance task = GetInstance.builder()
            .instanceId(Property.of("ocid1.instance.oc1.fra.xxx"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}

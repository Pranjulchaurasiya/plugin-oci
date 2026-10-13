package io.kestra.plugin.oci.objectstorage;

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
class BucketManagementTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testCreateBucketOutput() {
        Instant now = Instant.now();
        CreateBucket.Output output = CreateBucket.Output.builder()
            .bucketName("warehouse-archive")
            .namespaceName("nr9xyz789")
            .compartmentId("ocid1.compartment.oc1..sample")
            .timeCreated(now)
            .etag("etag-bucket-123")
            .build();

        assertThat(output.getBucketName(), is("warehouse-archive"));
        assertThat(output.getNamespaceName(), is("nr9xyz789"));
        assertThat(output.getCompartmentId(), is("ocid1.compartment.oc1..sample"));
        assertThat(output.getTimeCreated(), is(now));
        assertThat(output.getEtag(), is("etag-bucket-123"));
    }

    @Test
    void testDeleteBucketOutput() {
        DeleteBucket.Output output = DeleteBucket.Output.builder()
            .bucketName("temp-scratch")
            .namespaceName("nr9xyz789")
            .opcRequestId("req-del-bucket-001")
            .build();

        assertThat(output.getBucketName(), is("temp-scratch"));
        assertThat(output.getNamespaceName(), is("nr9xyz789"));
        assertThat(output.getOpcRequestId(), is("req-del-bucket-001"));
    }

    @Test
    void testCreateBucketPropertyRendering() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "ns", "nr9xyz789",
            "bucket", "lakehouse-silver",
            "cmp", "ocid1.compartment.oc1..mycmp"
        ));

        CreateBucket task = CreateBucket.builder()
            .region(Property.of("eu-frankfurt-1"))
            .tenancyOcid(Property.of("ocid1.tenancy.oc1..sampletenancy"))
            .userId(Property.of("ocid1.user.oc1..sampleuser"))
            .fingerprint(Property.of("20:3b:97:13:55:1c:5b:0d:d3:37:d0:02:ee:3a:3c:22"))
            .privateKey(Property.of("-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----"))
            .namespaceName(Property.ofExpression("{{ ns }}"))
            .bucketName(Property.ofExpression("{{ bucket }}"))
            .compartmentId(Property.ofExpression("{{ cmp }}"))
            .build();

        assertThat(runContext.render(task.getNamespaceName()).as(String.class).orElse(null), is("nr9xyz789"));
        assertThat(runContext.render(task.getBucketName()).as(String.class).orElse(null), is("lakehouse-silver"));
        assertThat(runContext.render(task.getCompartmentId()).as(String.class).orElse(null), is("ocid1.compartment.oc1..mycmp"));
    }

    @Test
    void testMissingCredentialsThrows() {
        RunContext runContext = runContextFactory.of();

        DeleteBucket task = DeleteBucket.builder()
            .namespaceName(Property.of("ns"))
            .bucketName(Property.of("bucket"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}

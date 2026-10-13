package io.kestra.plugin.oci.objectstorage;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ObjectOperationsTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testUploadOutput() {
        Upload.Output output = Upload.Output.builder()
            .bucketName("landing-zone")
            .namespaceName("nr9xyz789")
            .objectName("daily/sales_20261013.parquet")
            .contentLength(1048576L)
            .etag("etag-upload-456")
            .build();

        assertThat(output.getBucketName(), is("landing-zone"));
        assertThat(output.getNamespaceName(), is("nr9xyz789"));
        assertThat(output.getObjectName(), is("daily/sales_20261013.parquet"));
        assertThat(output.getContentLength(), is(1048576L));
        assertThat(output.getEtag(), is("etag-upload-456"));
    }

    @Test
    void testDownloadOutput() {
        URI testUri = URI.create("kestra:///company/data/executions/abc/tasks/def/output.csv");
        Download.Output output = Download.Output.builder()
            .uri(testUri)
            .contentLength(2048L)
            .contentType("text/csv")
            .etag("etag-download-789")
            .build();

        assertThat(output.getUri(), is(testUri));
        assertThat(output.getContentLength(), is(2048L));
        assertThat(output.getContentType(), is("text/csv"));
        assertThat(output.getEtag(), is("etag-download-789"));
    }

    @Test
    void testListObjectsOutput() {
        Instant modTime = Instant.now();
        ListObjects.ObjectItem item = ListObjects.ObjectItem.builder()
            .name("data/batch_01.csv")
            .size(4096L)
            .md5("md5-hash-sample")
            .etag("etag-item-01")
            .timeModified(modTime)
            .storageTier("Standard")
            .build();

        ListObjects.Output output = ListObjects.Output.builder()
            .objects(List.of(item))
            .count(1)
            .nextStartWith("data/batch_02.csv")
            .build();

        assertThat(output.getCount(), is(1));
        assertThat(output.getObjects().getFirst().getName(), is("data/batch_01.csv"));
        assertThat(output.getObjects().getFirst().getSize(), is(4096L));
        assertThat(output.getObjects().getFirst().getStorageTier(), is("Standard"));
        assertThat(output.getNextStartWith(), is("data/batch_02.csv"));
    }

    @Test
    void testDeleteObjectOutput() {
        DeleteObject.Output output = DeleteObject.Output.builder()
            .objectName("temp/scratch.log")
            .bucketName("temp-bucket")
            .namespaceName("nr9xyz789")
            .versionId("v1.0.0")
            .opcRequestId("req-del-obj-999")
            .build();

        assertThat(output.getObjectName(), is("temp/scratch.log"));
        assertThat(output.getBucketName(), is("temp-bucket"));
        assertThat(output.getNamespaceName(), is("nr9xyz789"));
        assertThat(output.getVersionId(), is("v1.0.0"));
        assertThat(output.getOpcRequestId(), is("req-del-obj-999"));
    }

    @Test
    void testPropertyRendering() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "ns", "nr9xyz789",
            "bucket", "lakehouse-gold",
            "obj", "metrics/kpi.json",
            "pfx", "metrics/"
        ));

        ListObjects listTask = ListObjects.builder()
            .region(Property.of("eu-frankfurt-1"))
            .namespaceName(Property.ofExpression("{{ ns }}"))
            .bucketName(Property.ofExpression("{{ bucket }}"))
            .prefix(Property.ofExpression("{{ pfx }}"))
            .build();

        assertThat(runContext.render(listTask.getNamespaceName()).as(String.class).orElse(null), is("nr9xyz789"));
        assertThat(runContext.render(listTask.getBucketName()).as(String.class).orElse(null), is("lakehouse-gold"));
        assertThat(runContext.render(listTask.getPrefix()).as(String.class).orElse(null), is("metrics/"));

        DeleteObject deleteTask = DeleteObject.builder()
            .namespaceName(Property.ofExpression("{{ ns }}"))
            .bucketName(Property.ofExpression("{{ bucket }}"))
            .objectName(Property.ofExpression("{{ obj }}"))
            .build();

        assertThat(runContext.render(deleteTask.getObjectName()).as(String.class).orElse(null), is("metrics/kpi.json"));
    }

    @Test
    void testMissingCredentialsThrows() {
        RunContext runContext = runContextFactory.of();

        Download task = Download.builder()
            .namespaceName(Property.of("ns"))
            .bucketName(Property.of("bucket"))
            .objectName(Property.of("file.txt"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}

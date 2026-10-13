package io.kestra.plugin.oci.objectstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.responses.ListObjectsResponse;
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
    title = "List objects in an OCI Object Storage bucket",
    description = "Lists objects in a bucket with optional prefix filtering and metadata extraction."
)
@Plugin(
    examples = {
        @Example(
            title = "List objects in a bucket under a prefix",
            full = true,
            code = """
                id: list_objects
                namespace: company.data

                tasks:
                  - id: list
                    type: io.kestra.plugin.oci.objectstorage.ListObjects
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    namespaceName: "{{ secret('OCI_NAMESPACE') }}"
                    bucketName: "data-lake-raw"
                    prefix: "raw/2026/"
                """
        )
    }
)
public class ListObjects extends AbstractOciTask implements RunnableTask<ListObjects.Output> {
    @Schema(title = "The Object Storage namespace name")
    @NotNull
    private Property<String> namespaceName;

    @Schema(title = "The name of the bucket")
    @NotNull
    private Property<String> bucketName;

    @Schema(title = "Optional prefix filter to narrow down object results")
    private Property<String> prefix;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rNamespace = runContext.render(this.namespaceName).as(String.class).orElseThrow();
        String rBucket = runContext.render(this.bucketName).as(String.class).orElseThrow();
        String rPrefix = runContext.render(this.prefix).as(String.class).orElse(null);

        logger.debug("Listing objects in OCI bucket: {}/{} with prefix: {}", rNamespace, rBucket, rPrefix);

        try (ObjectStorageClient client = ObjectStorageClient.builder().build(authProvider)) {
            ListObjectsRequest.Builder requestBuilder = ListObjectsRequest.builder()
                .namespaceName(rNamespace)
                .bucketName(rBucket);

            if (rPrefix != null && !rPrefix.isBlank()) {
                requestBuilder.prefix(rPrefix);
            }

            ListObjectsResponse response = client.listObjects(requestBuilder.build());
            com.oracle.bmc.objectstorage.model.ListObjects result = response.getListObjects();

            List<ObjectItem> items = result != null && result.getObjects() != null
                ? result.getObjects().stream().map(this::mapObjectSummary).toList()
                : Collections.emptyList();

            logger.info("Found {} objects in bucket {}/{}", items.size(), rNamespace, rBucket);

            return Output.builder()
                .objects(items)
                .count(items.size())
                .nextStartWith(result != null ? result.getNextStartWith() : null)
                .build();
        }
    }

    private ObjectItem mapObjectSummary(ObjectSummary summary) {
        return ObjectItem.builder()
            .name(summary.getName())
            .size(summary.getSize())
            .md5(summary.getMd5())
            .etag(summary.getEtag())
            .timeModified(summary.getTimeModified() != null ? summary.getTimeModified().toInstant() : null)
            .storageTier(summary.getStorageTier() != null ? summary.getStorageTier().getValue() : null)
            .build();
    }

    @Builder
    @Getter
    public static class ObjectItem {
        @Schema(title = "The name/key of the object")
        private final String name;

        @Schema(title = "Size of the object in bytes")
        private final Long size;

        @Schema(title = "MD5 hash of the object")
        private final String md5;

        @Schema(title = "Entity tag (ETag) of the object")
        private final String etag;

        @Schema(title = "Last modified timestamp")
        private final Instant timeModified;

        @Schema(title = "Storage tier of the object")
        private final String storageTier;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "List of objects matching the query")
        private final List<ObjectItem> objects;

        @Schema(title = "Total number of objects returned")
        private final Integer count;

        @Schema(title = "Next start token for paginated listings")
        private final String nextStartWith;
    }
}

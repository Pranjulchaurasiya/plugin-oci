package io.kestra.plugin.oci.objectstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.Bucket;
import com.oracle.bmc.objectstorage.model.CreateBucketDetails;
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest;
import com.oracle.bmc.objectstorage.responses.CreateBucketResponse;
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
    title = "Create an OCI Object Storage bucket",
    description = "Provisions a new bucket in the specified Object Storage namespace and compartment."
)
@Plugin(
    examples = {
        @Example(
            title = "Create an OCI Object Storage bucket",
            full = true,
            code = """
                id: create_bucket
                namespace: company.data

                tasks:
                  - id: create
                    type: io.kestra.plugin.oci.objectstorage.CreateBucket
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    namespaceName: "{{ secret('OCI_NAMESPACE') }}"
                    compartmentId: "{{ secret('OCI_COMPARTMENT_OCID') }}"
                    bucketName: "data-lake-raw"
                """
        )
    }
)
public class CreateBucket extends AbstractOciTask implements RunnableTask<CreateBucket.Output> {
    @Schema(title = "The Object Storage namespace name")
    @NotNull
    private Property<String> namespaceName;

    @Schema(title = "The compartment OCID in which to create the bucket")
    @NotNull
    private Property<String> compartmentId;

    @Schema(title = "The name of the bucket to create")
    @NotNull
    private Property<String> bucketName;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rNamespace = runContext.render(this.namespaceName).as(String.class).orElseThrow();
        String rCompartment = runContext.render(this.compartmentId).as(String.class).orElseThrow();
        String rBucket = runContext.render(this.bucketName).as(String.class).orElseThrow();

        logger.debug("Creating OCI Object Storage bucket: {} in namespace: {}", rBucket, rNamespace);

        try (ObjectStorageClient client = ObjectStorageClient.builder().build(authProvider)) {
            CreateBucketDetails details = CreateBucketDetails.builder()
                .name(rBucket)
                .compartmentId(rCompartment)
                .build();

            CreateBucketRequest request = CreateBucketRequest.builder()
                .namespaceName(rNamespace)
                .createBucketDetails(details)
                .build();

            CreateBucketResponse response = client.createBucket(request);
            Bucket bucket = response.getBucket();

            logger.info("Successfully created bucket: {} in namespace: {}", rBucket, rNamespace);

            return Output.builder()
                .bucketName(bucket != null ? bucket.getName() : rBucket)
                .namespaceName(rNamespace)
                .compartmentId(rCompartment)
                .timeCreated(bucket != null && bucket.getTimeCreated() != null ? bucket.getTimeCreated().toInstant() : null)
                .etag(response.getETag())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The name of the created bucket")
        private final String bucketName;

        @Schema(title = "The namespace of the bucket")
        private final String namespaceName;

        @Schema(title = "The compartment OCID of the bucket")
        private final String compartmentId;

        @Schema(title = "Timestamp when the bucket was created")
        private final Instant timeCreated;

        @Schema(title = "Entity tag (ETag) of the bucket")
        private final String etag;
    }
}

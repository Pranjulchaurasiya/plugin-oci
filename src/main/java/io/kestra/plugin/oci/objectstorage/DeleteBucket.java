package io.kestra.plugin.oci.objectstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest;
import com.oracle.bmc.objectstorage.responses.DeleteBucketResponse;
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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete an OCI Object Storage bucket",
    description = "Deletes an empty bucket from Oracle Cloud Infrastructure Object Storage."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete an empty OCI bucket",
            full = true,
            code = """
                id: delete_bucket
                namespace: company.data

                tasks:
                  - id: delete
                    type: io.kestra.plugin.oci.objectstorage.DeleteBucket
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    namespaceName: "{{ secret('OCI_NAMESPACE') }}"
                    bucketName: "data-lake-scratch"
                """
        )
    }
)
public class DeleteBucket extends AbstractOciTask implements RunnableTask<DeleteBucket.Output> {
    @Schema(title = "The Object Storage namespace name")
    @NotNull
    private Property<String> namespaceName;

    @Schema(title = "The name of the bucket to delete")
    @NotNull
    private Property<String> bucketName;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rNamespace = runContext.render(this.namespaceName).as(String.class).orElseThrow();
        String rBucket = runContext.render(this.bucketName).as(String.class).orElseThrow();

        logger.debug("Deleting OCI Object Storage bucket: {} in namespace: {}", rBucket, rNamespace);

        try (ObjectStorageClient client = ObjectStorageClient.builder().build(authProvider)) {
            DeleteBucketRequest request = DeleteBucketRequest.builder()
                .namespaceName(rNamespace)
                .bucketName(rBucket)
                .build();

            DeleteBucketResponse response = client.deleteBucket(request);
            logger.info("Successfully deleted bucket: {} in namespace: {}", rBucket, rNamespace);

            return Output.builder()
                .bucketName(rBucket)
                .namespaceName(rNamespace)
                .opcRequestId(response.getOpcRequestId())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The name of the deleted bucket")
        private final String bucketName;

        @Schema(title = "The namespace of the bucket")
        private final String namespaceName;

        @Schema(title = "Unique OCI request identifier (opc-request-id)")
        private final String opcRequestId;
    }
}

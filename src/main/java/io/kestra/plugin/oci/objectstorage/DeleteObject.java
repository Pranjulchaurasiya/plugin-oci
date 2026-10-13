package io.kestra.plugin.oci.objectstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
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
    title = "Delete an object from OCI Object Storage",
    description = "Deletes an object from an Oracle Cloud Infrastructure Object Storage bucket."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete an object from an OCI bucket",
            full = true,
            code = """
                id: delete_object
                namespace: company.data

                tasks:
                  - id: delete
                    type: io.kestra.plugin.oci.objectstorage.DeleteObject
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    namespaceName: "{{ secret('OCI_NAMESPACE') }}"
                    bucketName: "data-lake-raw"
                    objectName: "raw/records.json"
                """
        )
    }
)
public class DeleteObject extends AbstractOciTask implements RunnableTask<DeleteObject.Output> {
    @Schema(title = "The Object Storage namespace name")
    @NotNull
    private Property<String> namespaceName;

    @Schema(title = "The name of the bucket")
    @NotNull
    private Property<String> bucketName;

    @Schema(title = "The name of the object to delete")
    @NotNull
    private Property<String> objectName;

    @Schema(title = "Optional specific object version ID to delete")
    private Property<String> versionId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rNamespace = runContext.render(this.namespaceName).as(String.class).orElseThrow();
        String rBucket = runContext.render(this.bucketName).as(String.class).orElseThrow();
        String rObject = runContext.render(this.objectName).as(String.class).orElseThrow();
        String rVersionId = runContext.render(this.versionId).as(String.class).orElse(null);

        logger.debug("Deleting object {} in OCI bucket: {}/{}", rObject, rNamespace, rBucket);

        try (ObjectStorageClient client = ObjectStorageClient.builder().build(authProvider)) {
            DeleteObjectRequest.Builder requestBuilder = DeleteObjectRequest.builder()
                .namespaceName(rNamespace)
                .bucketName(rBucket)
                .objectName(rObject);

            if (rVersionId != null && !rVersionId.isBlank()) {
                requestBuilder.versionId(rVersionId);
            }

            DeleteObjectResponse response = client.deleteObject(requestBuilder.build());
            logger.info("Successfully deleted object {} in bucket: {}/{}", rObject, rNamespace, rBucket);

            return Output.builder()
                .objectName(rObject)
                .bucketName(rBucket)
                .namespaceName(rNamespace)
                .versionId(rVersionId)
                .opcRequestId(response.getOpcRequestId())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The name of the deleted object")
        private final String objectName;

        @Schema(title = "The bucket name")
        private final String bucketName;

        @Schema(title = "The namespace of the bucket")
        private final String namespaceName;

        @Schema(title = "Version ID of the deleted object, if specified")
        private final String versionId;

        @Schema(title = "Unique OCI request identifier (opc-request-id)")
        private final String opcRequestId;
    }
}

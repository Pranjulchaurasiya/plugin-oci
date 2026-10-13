package io.kestra.plugin.oci.objectstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
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

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Download an object from OCI Object Storage",
    description = "Downloads an object from an OCI bucket into Kestra internal storage and exposes its URI for downstream tasks."
)
@Plugin(
    examples = {
        @Example(
            title = "Download an object to Kestra internal storage",
            full = true,
            code = """
                id: download_object
                namespace: company.data

                tasks:
                  - id: download
                    type: io.kestra.plugin.oci.objectstorage.Download
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
public class Download extends AbstractOciTask implements RunnableTask<Download.Output> {
    @Schema(title = "The Object Storage namespace name")
    @NotNull
    private Property<String> namespaceName;

    @Schema(title = "The name of the bucket")
    @NotNull
    private Property<String> bucketName;

    @Schema(title = "The key/name of the object to download")
    @NotNull
    private Property<String> objectName;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rNamespace = runContext.render(this.namespaceName).as(String.class).orElseThrow();
        String rBucket = runContext.render(this.bucketName).as(String.class).orElseThrow();
        String rObject = runContext.render(this.objectName).as(String.class).orElseThrow();

        logger.debug("Downloading object {} from OCI bucket: {}/{}", rObject, rNamespace, rBucket);

        try (ObjectStorageClient client = ObjectStorageClient.builder().build(authProvider)) {
            GetObjectRequest request = GetObjectRequest.builder()
                .namespaceName(rNamespace)
                .bucketName(rBucket)
                .objectName(rObject)
                .build();

            GetObjectResponse response = client.getObject(request);
            URI uri;
            File tempFile = File.createTempFile("kestra-oci-download-", ".tmp");
            try {
                try (InputStream is = response.getInputStream();
                     OutputStream os = new FileOutputStream(tempFile)) {
                    IOUtils.copy(is, os);
                }
                uri = runContext.storage().putFile(tempFile);
            } finally {
                tempFile.delete();
            }

            Long contentLength = response.getContentLength();
            String contentType = response.getContentType();
            String etag = response.getETag();

            logger.info("Successfully downloaded object {} to Kestra internal storage: {}", rObject, uri);

            return Output.builder()
                .uri(uri)
                .contentLength(contentLength)
                .contentType(contentType)
                .etag(etag)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The URI of the downloaded file in Kestra internal storage")
        private final URI uri;

        @Schema(title = "Content length in bytes")
        private final Long contentLength;

        @Schema(title = "MIME content type of the downloaded object")
        private final String contentType;

        @Schema(title = "Entity tag (ETag) of the object")
        private final String etag;
    }
}

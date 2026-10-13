package io.kestra.plugin.oci.objectstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
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
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Upload an object to OCI Object Storage",
    description = "Uploads a file or string payload from Kestra internal storage or local file system to an OCI Object Storage bucket."
)
@Plugin(
    examples = {
        @Example(
            title = "Upload a file to OCI Object Storage",
            full = true,
            code = """
                id: upload_object
                namespace: company.data

                tasks:
                  - id: upload
                    type: io.kestra.plugin.oci.objectstorage.Upload
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    namespaceName: "{{ secret('OCI_NAMESPACE') }}"
                    bucketName: "data-lake-raw"
                    objectName: "raw/records.json"
                    from: "{{ outputs.generate_data.uri }}"
                """
        )
    }
)
public class Upload extends AbstractOciTask implements RunnableTask<Upload.Output> {
    @Schema(title = "The Object Storage namespace name")
    @NotNull
    private Property<String> namespaceName;

    @Schema(title = "The name of the target bucket")
    @NotNull
    private Property<String> bucketName;

    @Schema(title = "The key/name of the object in the bucket")
    @NotNull
    private Property<String> objectName;

    @Schema(
        title = "The source to upload from",
        description = "Can be a Kestra internal storage URI (kestra://...), a local file path, or raw content string."
    )
    @NotNull
    private Property<String> from;

    @Schema(title = "The content type (MIME type) of the object")
    private Property<String> contentType;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rNamespace = runContext.render(this.namespaceName).as(String.class).orElseThrow();
        String rBucket = runContext.render(this.bucketName).as(String.class).orElseThrow();
        String rObject = runContext.render(this.objectName).as(String.class).orElseThrow();
        String rFrom = runContext.render(this.from).as(String.class).orElseThrow();
        String rContentType = runContext.render(this.contentType).as(String.class).orElse(null);

        logger.debug("Uploading to OCI Object Storage: {}/{} from {}", rBucket, rObject, rFrom);

        InputStream inputStream;
        long contentLength;

        if (rFrom.startsWith("kestra://")) {
            inputStream = runContext.storage().getFile(URI.create(rFrom));
            byte[] bytes = IOUtils.toByteArray(inputStream);
            contentLength = bytes.length;
            inputStream = new ByteArrayInputStream(bytes);
        } else {
            File localFile = new File(rFrom);
            if (localFile.exists()) {
                contentLength = localFile.length();
                inputStream = new FileInputStream(localFile);
            } else {
                byte[] bytes = rFrom.getBytes(StandardCharsets.UTF_8);
                contentLength = bytes.length;
                inputStream = new ByteArrayInputStream(bytes);
            }
        }

        try (ObjectStorageClient client = ObjectStorageClient.builder().build(authProvider);
             InputStream is = inputStream) {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                .namespaceName(rNamespace)
                .bucketName(rBucket)
                .objectName(rObject)
                .putObjectBody(is)
                .contentLength(contentLength);

            if (rContentType != null && !rContentType.isBlank()) {
                requestBuilder.contentType(rContentType);
            }

            PutObjectResponse response = client.putObject(requestBuilder.build());
            logger.info("Successfully uploaded object {} to bucket {} (size: {} bytes)", rObject, rBucket, contentLength);

            return Output.builder()
                .bucketName(rBucket)
                .namespaceName(rNamespace)
                .objectName(rObject)
                .contentLength(contentLength)
                .etag(response.getETag())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The name of the bucket")
        private final String bucketName;

        @Schema(title = "The namespace of the bucket")
        private final String namespaceName;

        @Schema(title = "The key/name of the uploaded object")
        private final String objectName;

        @Schema(title = "Content length in bytes")
        private final Long contentLength;

        @Schema(title = "Entity tag (ETag) of the uploaded object")
        private final String etag;
    }
}

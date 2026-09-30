package com.joblens.hardening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.joblens.common.storage.S3FileStorage;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Exercises S3FileStorage against a real S3-compatible server. Opt in by pointing MINIO_ENDPOINT at one, e.g.
 * {@code docker run -p 9000:9000 -e MINIO_ROOT_USER=minio-test -e MINIO_ROOT_PASSWORD=minio-test-secret minio/minio server /data}
 * then {@code MINIO_ENDPOINT=http://localhost:9000 ./mvnw test -Dtest=S3FileStorageMinioIT}. Skipped otherwise.
 */
class S3FileStorageMinioIT {

    private static final String BUCKET = "joblens-it-" + UUID.randomUUID().toString().substring(0, 8);
    private static S3FileStorage storage;

    @BeforeAll
    static void connect() {
        String endpoint = System.getenv("MINIO_ENDPOINT");
        assumeTrue(endpoint != null && !endpoint.isBlank(), "MINIO_ENDPOINT not set; skipping real S3 test");
        System.setProperty("aws.accessKeyId", System.getenv().getOrDefault("MINIO_USER", "minio-test"));
        System.setProperty("aws.secretAccessKey", System.getenv().getOrDefault("MINIO_PASSWORD", "minio-test-secret"));
        storage = new S3FileStorage(BUCKET, "us-east-1", endpoint);
        S3Client admin = S3Client.builder().region(software.amazon.awssdk.regions.Region.US_EAST_1)
            .endpointOverride(java.net.URI.create(endpoint))
            .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
        admin.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @Test
    void roundTripsAgainstARealS3Api() {
        String key = UUID.randomUUID() + ".pdf";
        byte[] content = "%PDF-1.4 real bytes".getBytes();
        storage.store(key, content);
        assertThat(storage.load(key)).isEqualTo(content);
        storage.delete(key);
        assertThatThrownBy(() -> storage.load(key)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deletingAMissingKeyIsHarmless() {
        assertThatCode(() -> storage.delete("never-there.pdf")).doesNotThrowAnyException();
    }
}

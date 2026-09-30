package com.joblens.common.storage;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Stores uploads in S3. Credentials come from the default AWS provider chain (an IAM role when
 * running on AWS), never from application configuration. The bucket is expected to be private with
 * default encryption enabled (configured in infrastructure, not per request: encryption headers are not
 * portable to S3-compatible servers, and bucket-level enforcement cannot be forgotten by a caller).
 * Keys are server-generated UUIDs, so no user input reaches them.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);
    private static final String CONTENT_TYPE = "application/pdf";

    private final S3Client s3;
    private final String bucket;

    @org.springframework.beans.factory.annotation.Autowired
    public S3FileStorage(@Value("${app.storage.s3.bucket}") String bucket,
                         @Value("${app.storage.s3.region:}") String region,
                         @Value("${app.storage.s3.endpoint:}") String endpoint) {
        this(client(region, endpoint), bucket);
    }

    /** For tests and custom wiring. */
    public S3FileStorage(S3Client s3, String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("app.storage.s3.bucket (S3_BUCKET) must be set when STORAGE_TYPE=s3");
        }
        this.s3 = s3;
        this.bucket = bucket;
        log.info("File storage: S3 bucket {}", bucket);
    }

    private static S3Client client(String region, String endpoint) {
        var builder = S3Client.builder();
        if (!region.isBlank()) {
            builder.region(Region.of(region));
        }
        if (!endpoint.isBlank()) {
            // S3-compatible services (MinIO, LocalStack) for local development and tests.
            builder.endpointOverride(URI.create(endpoint))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    @Override
    public void store(String key, byte[] content) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(CONTENT_TYPE)
            .build(), RequestBody.fromBytes(content));
    }

    @Override
    public byte[] load(String key) {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new IllegalStateException("Stored file is missing: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        // S3 deletes are idempotent: deleting a missing key succeeds.
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}

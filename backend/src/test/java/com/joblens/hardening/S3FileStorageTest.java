package com.joblens.hardening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.joblens.common.storage.S3FileStorage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3FileStorageTest {

    private final S3Client s3 = mock(S3Client.class);
    private final S3FileStorage storage = new S3FileStorage(s3, "joblens-uploads");

    @Test
    void storesObjectsWithTheExpectedShape() throws Exception {
        storage.store("abc.pdf", new byte[] {1, 2, 3});

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("joblens-uploads");
        assertThat(request.getValue().key()).isEqualTo("abc.pdf");
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
        // Encryption is enforced by the bucket's default configuration, not per request.
        assertThat(request.getValue().serverSideEncryption()).isNull();
        assertThat(body.getValue().contentStreamProvider().newStream().readAllBytes()).containsExactly(1, 2, 3);
    }

    @Test
    void loadsObjectBytes() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
            .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {9, 8}));
        assertThat(storage.load("abc.pdf")).containsExactly(9, 8);
    }

    @Test
    void aMissingObjectIsReportedWithoutLeakingProviderDetails() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("arn:aws:secret-detail").build());
        assertThatThrownBy(() -> storage.load("gone.pdf"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("gone.pdf")
            .hasMessageNotContaining("arn:aws");
    }

    @Test
    void deletesByBucketAndKey() {
        storage.delete("abc.pdf");
        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("joblens-uploads");
        assertThat(request.getValue().key()).isEqualTo("abc.pdf");
    }

    @Test
    void refusesToStartWithoutABucket() {
        assertThatThrownBy(() -> new S3FileStorage(s3, " ")).isInstanceOf(IllegalStateException.class).hasMessageContaining("S3_BUCKET");
        assertThatThrownBy(() -> new S3FileStorage(s3, null)).isInstanceOf(IllegalStateException.class);
    }
}

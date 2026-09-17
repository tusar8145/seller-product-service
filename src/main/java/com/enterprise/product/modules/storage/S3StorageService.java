package com.enterprise.product.modules.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public String uploadInput(MultipartFile file, String key) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName).key(key)
                    .contentType(file.getContentType())
                    .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded input to s3://{}/{}", bucketName, key);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("S3 upload failed", e);
        }
    }

    public InputStream download(String key) {
        return s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build());
    }

    public void uploadOutput(byte[] content, String key, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(content));
        log.info("Uploaded output to s3://{}/{}", bucketName, key);
    }

    public String generatePresignedUrl(String key, Duration ttl) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(r -> r.bucket(bucketName).key(key))
                        .build())
                .url().toString();
    }
}

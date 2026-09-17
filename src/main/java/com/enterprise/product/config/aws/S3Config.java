package com.enterprise.product.config.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    @Value("${aws.region:ap-south-1}") private String region;
    @Value("${aws.access-key:}") private String accessKey;
    @Value("${aws.secret-key:}") private String secretKey;
    @Value("${aws.s3.endpoint:none}") private String endpoint;
    @Value("${aws.s3.path-style-access:false}") private boolean pathStyle;

    private AwsCredentialsProvider creds() {
        if (accessKey != null && !accessKey.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }

    private boolean hasCustomEndpoint() {
        return endpoint != null && !endpoint.isBlank() && !"none".equalsIgnoreCase(endpoint.trim());
    }

    @Bean
    public S3Client s3Client() {
        var b = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(creds())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle).build());
        if (hasCustomEndpoint()) b.endpointOverride(URI.create(endpoint.trim()));
        return b.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var b = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(creds());
        if (hasCustomEndpoint()) b.endpointOverride(URI.create(endpoint.trim()));
        return b.build();
    }
}

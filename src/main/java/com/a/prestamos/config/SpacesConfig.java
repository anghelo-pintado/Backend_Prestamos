package com.a.prestamos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class SpacesConfig {

    @Value("${app.do.spaces.access-key}")
    private String key;

    @Value("${app.do.spaces.secret-key}")
    private String secret;

    @Value("${app.do.spaces.endpoint-url}")
    private String endpoint;

    @Value("${app.do.spaces.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(key, secret)
                        )
                )
                .region(Region.of(region))
                .build();
    }
}

package com.example.financedataservice.config;

import java.net.URI;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;

@Configuration
@EnableConfigurationProperties(AwsSecretsManagerProperties.class)
public class AwsSecretsManagerConfig {

    private static final Logger log = LoggerFactory.getLogger(AwsSecretsManagerConfig.class);

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "aws.secrets-manager.enabled", havingValue = "true", matchIfMissing = true)
    public SecretsManagerClient secretsManagerClient(AwsSecretsManagerProperties properties) {
        Objects.requireNonNull(properties, "AwsSecretsManagerProperties must not be null");

        SecretsManagerClientBuilder builder = SecretsManagerClient.builder()
            .region(Region.of(properties.getRegion()));

        if (StringUtils.hasText(properties.getEndpoint())) {
            builder = builder.endpointOverride(URI.create(properties.getEndpoint()));
        }

        if (StringUtils.hasText(properties.getAccessKey()) && StringUtils.hasText(properties.getSecretKey())) {
            builder = builder.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
        } else {
            log.debug("AWS credentials not provided in configuration; relying on default provider chain.");
        }

        return builder.build();
    }
}


package com.example.financedataservice.config;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

@Component
public class ApiKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyProvider.class);

    private final ObjectProvider<SecretsManagerClient> secretsManagerClientProvider;
    private final AwsSecretsManagerProperties properties;
    private final String alphaVantageFallback;
    private final String twelveDataFallback;

    public ApiKeyProvider(ObjectProvider<SecretsManagerClient> secretsManagerClientProvider,
                          AwsSecretsManagerProperties properties,
                          @Value("${alpha-vantage.api-key:}") String alphaVantageFallback,
                          @Value("${twelve-data.api-key:}") String twelveDataFallback) {
        this.secretsManagerClientProvider = secretsManagerClientProvider;
        this.properties = properties;
        this.alphaVantageFallback = alphaVantageFallback;
        this.twelveDataFallback = twelveDataFallback;
    }

    public String getAlphaVantageApiKey() {
        return resolveSecret(properties.getAlphaVantageSecretName(), alphaVantageFallback, "alpha-vantage.api-key");
    }

    public String getTwelveDataApiKey() {
        return resolveSecret(properties.getTwelveDataSecretName(), twelveDataFallback, "twelve-data.api-key");
    }

    private String resolveSecret(String secretName, String fallback, String fallbackProperty) {
        Optional<String> secretValue = fetchSecret(secretName);
        if (secretValue.isPresent()) {
            return secretValue.get();
        }

        String normalizedFallback = normalize(fallback);
        if (StringUtils.hasText(normalizedFallback)) {
            log.debug("Falling back to property {} for secret {}", fallbackProperty, secretName);
            return normalizedFallback;
        }

        throw new IllegalStateException("API key not available from Secrets Manager or property '" + fallbackProperty + "'.");
    }

    private Optional<String> fetchSecret(String secretName) {
        if (properties == null || !properties.isEnabled()) {
            log.debug("AWS Secrets Manager disabled via configuration.");
            return Optional.empty();
        }

        if (!StringUtils.hasText(secretName)) {
            log.debug("Secret name not configured; skipping Secrets Manager lookup.");
            return Optional.empty();
        }

        SecretsManagerClient client = secretsManagerClientProvider.getIfAvailable();
        if (client == null) {
            log.warn("SecretsManagerClient bean not available; falling back to property value.");
            return Optional.empty();
        }

        try {
            GetSecretValueResponse response = client.getSecretValue(
                GetSecretValueRequest.builder().secretId(secretName).build());
            String secretString = normalize(response.secretString());
            if (!StringUtils.hasText(secretString)) {
                log.warn("Secret {} retrieved but empty; falling back to property value.", secretName);
                return Optional.empty();
            }
            return Optional.of(secretString);
        } catch (ResourceNotFoundException resourceNotFoundException) {
            log.warn("Secret {} not found in AWS Secrets Manager; falling back to property value.", secretName);
            return Optional.empty();
        } catch (SdkException sdkException) {
            log.warn("Failed to retrieve secret {} from AWS Secrets Manager; falling back to property value.",
                secretName, sdkException);
            return Optional.empty();
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.strip();
    }
}

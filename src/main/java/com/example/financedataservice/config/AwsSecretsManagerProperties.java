package com.example.financedataservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.secrets-manager")
public class AwsSecretsManagerProperties {

    private boolean enabled = true;
    private String region = "us-east-1";
    private String endpoint;
    private String accessKey = "test";
    private String secretKey = "test";
    private String alphaVantageSecretName = "finance/backend/alpha-vantage/api-key";
    private String twelveDataSecretName = "finance/backend/twelve-data/api-key";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getAlphaVantageSecretName() {
        return alphaVantageSecretName;
    }

    public void setAlphaVantageSecretName(String alphaVantageSecretName) {
        this.alphaVantageSecretName = alphaVantageSecretName;
    }

    public String getTwelveDataSecretName() {
        return twelveDataSecretName;
    }

    public void setTwelveDataSecretName(String twelveDataSecretName) {
        this.twelveDataSecretName = twelveDataSecretName;
    }
}


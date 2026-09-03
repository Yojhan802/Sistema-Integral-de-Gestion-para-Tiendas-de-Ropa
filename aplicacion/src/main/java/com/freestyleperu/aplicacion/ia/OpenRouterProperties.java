package com.freestyleperu.aplicacion.ia;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openrouter")
public class OpenRouterProperties {

    private String apiKey;
    private String baseUrl = "https://openrouter.ai/api/v1";
    private String model;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean configurado() {
        return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
    }
}

package com.freestyleperu.aplicacion.ia;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente mínimo para el endpoint de chat de OpenRouter (openrouter.ai) — API
 * compatible con el formato de OpenAI. Solo el backend habla con OpenRouter;
 * la apiKey nunca se expone al frontend.
 */
@Component
public class OpenRouterClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);
    private static final int MAX_TOKENS_RESPUESTA = 400;

    private final OpenRouterProperties properties;
    private final RestClient restClient;

    public OpenRouterClient(OpenRouterProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
    }

    public String completar(List<OpenRouterMessage> mensajes) {
        if (!properties.configurado()) {
            throw new AsistenteNoDisponibleException("El asistente todavía no está configurado.");
        }

        try {
            OpenRouterChatResponse respuesta = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(new OpenRouterChatRequest(properties.getModel(), mensajes, MAX_TOKENS_RESPUESTA))
                    .retrieve()
                    .body(OpenRouterChatResponse.class);

            String contenido = respuesta == null ? null : respuesta.primerContenido();
            if (contenido == null || contenido.isBlank()) {
                throw new AsistenteNoDisponibleException("El asistente no pudo responder en este momento.");
            }
            return contenido;
        } catch (RestClientException ex) {
            log.warn("Fallo al llamar a OpenRouter", ex);
            throw new AsistenteNoDisponibleException("El asistente no está disponible en este momento.");
        }
    }

    record OpenRouterChatRequest(String model, List<OpenRouterMessage> messages, @JsonProperty("max_tokens") int maxTokens) {
    }

    public record OpenRouterMessage(String role, String content) {
    }

    record OpenRouterChatResponse(List<OpenRouterChoice> choices) {
        String primerContenido() {
            if (choices == null || choices.isEmpty()) return null;
            OpenRouterMessage mensaje = choices.get(0).message();
            return mensaje == null ? null : mensaje.content();
        }
    }

    record OpenRouterChoice(OpenRouterMessage message) {
    }
}

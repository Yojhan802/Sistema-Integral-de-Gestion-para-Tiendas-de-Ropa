package com.freestyleperu.aplicacion.producto.service;

import com.freestyleperu.aplicacion.configuracion.service.ConfiguracionService;
import com.freestyleperu.aplicacion.ia.OpenRouterClient;
import com.freestyleperu.aplicacion.producto.dto.request.GenerarDescripcionRequest;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/** Redacta una descripción de producto en español a partir de lo que el usuario ya escribió en el formulario — nunca inventa material, medidas o cuidados que no se le dieron. */
@Service
public class ProductoAsistenteService {

    private final OpenRouterClient openRouterClient;
    private final ConfiguracionService configuracionService;

    public ProductoAsistenteService(OpenRouterClient openRouterClient, ConfiguracionService configuracionService) {
        this.openRouterClient = openRouterClient;
        this.configuracionService = configuracionService;
    }

    public String generarDescripcion(GenerarDescripcionRequest request) {
        String frase = configuracionService.obtenerContextoIA().frase();
        String systemPrompt = "Escribes descripciones de producto para el catálogo de " + frase + ". "
                + "En español, entre 2 y 4 frases, tono cercano y vendedor pero sin exagerar. "
                + "Usa SOLO los datos que se te dan abajo — si un dato no está (ej. material o calce), no lo menciones ni lo inventes. "
                + "Responde únicamente con el texto de la descripción, sin comillas ni títulos.\n\n"
                + "Datos del producto:\n" + datosProducto(request);

        return openRouterClient.completar(List.of(
                new OpenRouterClient.OpenRouterMessage("system", systemPrompt),
                new OpenRouterClient.OpenRouterMessage("user", "Genera la descripción.")));
    }

    private String datosProducto(GenerarDescripcionRequest r) {
        return Stream.of(
                        campo("Nombre", r.nombre()),
                        campo("Categoría", r.categoria()),
                        campo("Marca", r.marca()),
                        campo("Material", r.material()),
                        campo("Calce", r.calce()))
                .filter(s -> s != null)
                .collect(Collectors.joining("\n"));
    }

    private String campo(String etiqueta, String valor) {
        return (valor == null || valor.isBlank()) ? null : etiqueta + ": " + valor.trim();
    }
}

package com.freestyleperu.aplicacion.ia;

import com.freestyleperu.aplicacion.configuracion.domain.BusinessVertical;
import com.freestyleperu.aplicacion.configuracion.dto.response.ContextoNegocioIAResponse;
import com.freestyleperu.aplicacion.configuracion.service.ConfiguracionService;
import com.freestyleperu.aplicacion.ia.dto.AsistenteHistorialItem;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicCategoriaResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicMetodoPagoResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicProductoDetalleResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicProductoResumenResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicShippingInfoResponse;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicVarianteResponse;
import com.freestyleperu.aplicacion.tienda.service.TiendaCatalogoService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asistente de compras de la tienda pública (plan IA). El system prompt solo
 * incluye datos reales leídos en el momento (catálogo completo por categoría,
 * tallas/colores/stock del producto puntual si aplica, envío, métodos de
 * pago) — nunca se le pide al modelo que invente algo que no esté ahí. El
 * historial lo manda el frontend (sin sesión en el backend).
 */
@Service
@Transactional(readOnly = true)
public class AsistenteTiendaService {

    private static final int MAX_PRODUCTOS_CONTEXTO = 6;
    private static final int MAX_PRODUCTOS_CATALOGO_COMPLETO = 60;
    private static final int MIN_LARGO_PALABRA_NORMALIZABLE = 4;
    private static final int MAX_TURNOS_HISTORIAL = 8;
    // Cualquier mención a un id de producto — no solo el formato de enlace producto.html?id=N,
    // porque el modelo a veces cita "(id 4, S/ 79.90)" suelto sin envolverlo en el enlace, y ese
    // caso también hay que validar contra los ids reales (así se detectó "Pantalón Jeans Slim
    // Fit (id 4)", un producto 100% inventado que no tenía enlace pero sí un id falso).
    private static final Pattern PATRON_CUALQUIER_ID = Pattern.compile("(?i)\\bid\\s*[:#]?\\s*(\\d+)\\b");
    // Alucinación sin ningún id citado (ej. "Sí, tenemos varios buzos disponibles"). Se aplica
    // cuando el cliente nombró una categoría real sin stock, o cuando la búsqueda puntual no
    // encontró nada — en ambos casos, si además no cita ningún id real, no tiene de dónde haber
    // sacado esa afirmación.
    private static final Pattern PATRON_AFIRMACION_FALSA = Pattern.compile(
            "(?i)\\b(te (puedo|podemos) (recomendar|mostrar|ofrecer)|puedo (recomendarte|mostrarte|ofrecerte)|"
                    + "te recomiendo|aquí tienes|tenemos (varios|varias|algunas|algunos|disponible|disponibles)|"
                    + "contamos con|s[ií],?\\s+(tenemos|contamos|hay)|claro,?\\s+tenemos)\\b");
    private static final String RESPUESTA_SIN_PRODUCTO = "No encontré eso en la tienda ahora mismo. ¿Te ayudo con algo más?";

    private final TiendaCatalogoService catalogoService;
    private final ConfiguracionService configuracionService;
    private final OpenRouterClient openRouterClient;

    public AsistenteTiendaService(TiendaCatalogoService catalogoService, ConfiguracionService configuracionService,
            OpenRouterClient openRouterClient) {
        this.catalogoService = catalogoService;
        this.configuracionService = configuracionService;
        this.openRouterClient = openRouterClient;
    }

    public String responder(String mensajeCliente, List<AsistenteHistorialItem> historial) {
        List<AsistenteHistorialItem> turnosRecientes = ultimosTurnos(historial);
        List<PublicProductoResumenResponse> productosEspecificos = buscarProductosRelevantes(mensajeCliente, turnosRecientes);
        boolean categoriaNombradaVacia = categoriaFueNombradaYEstaVacia(mensajeCliente, turnosRecientes);
        Map<String, List<PublicProductoResumenResponse>> catalogoPorCategoria = catalogoCompletoPorCategoria();

        Set<Long> idsReales = new HashSet<>();
        productosEspecificos.forEach(p -> idsReales.add(p.id()));
        catalogoPorCategoria.values().forEach(lista -> lista.forEach(p -> idsReales.add(p.id())));

        String systemPrompt = construirSystemPrompt(productosEspecificos, catalogoPorCategoria);

        List<OpenRouterClient.OpenRouterMessage> mensajes = new ArrayList<>();
        mensajes.add(new OpenRouterClient.OpenRouterMessage("system", systemPrompt));
        turnosRecientes.forEach(h -> mensajes.add(new OpenRouterClient.OpenRouterMessage(h.role(), h.content())));
        mensajes.add(new OpenRouterClient.OpenRouterMessage("user", mensajeCliente));

        String respuesta = openRouterClient.completar(mensajes);

        // Guardrail 1: cualquier id citado (con enlace producto.html?id=N o suelto como "id 4")
        // debe ser real — aplica siempre, sea consulta puntual o combo/outfit.
        Matcher citas = PATRON_CUALQUIER_ID.matcher(respuesta);
        boolean citoAlgunIdValido = false;
        while (citas.find()) {
            if (!idsReales.contains(Long.valueOf(citas.group(1)))) {
                return RESPUESTA_SIN_PRODUCTO;
            }
            citoAlgunIdValido = true;
        }

        // Guardrail 2: si la búsqueda puntual no encontró nada (o el cliente nombró una categoría
        // real sin stock) y la respuesta afirma tener algo SIN citar ningún id real que lo respalde,
        // es una alucinación pura — no tiene de dónde haber sacado esa afirmación.
        boolean sinBaseReal = (categoriaNombradaVacia || productosEspecificos.isEmpty()) && !citoAlgunIdValido;
        if (sinBaseReal && PATRON_AFIRMACION_FALSA.matcher(respuesta).find()) {
            return RESPUESTA_SIN_PRODUCTO;
        }
        return respuesta;
    }

    /** El frontend puede mandar una conversación larga — nos quedamos solo con los últimos turnos para no disparar el costo. */
    private List<AsistenteHistorialItem> ultimosTurnos(List<AsistenteHistorialItem> historial) {
        int desde = Math.max(0, historial.size() - MAX_TURNOS_HISTORIAL);
        return historial.subList(desde, historial.size());
    }

    private String construirSystemPrompt(List<PublicProductoResumenResponse> productosEspecificos,
            Map<String, List<PublicProductoResumenResponse>> catalogoPorCategoria) {
        String nombreTienda = configuracionService.obtenerBranding().name();
        ContextoNegocioIAResponse negocio = configuracionService.obtenerContextoIA();
        PublicShippingInfoResponse envio = catalogoService.obtenerInfoEnvio();
        List<PublicMetodoPagoResponse> metodosPago = catalogoService.listarMetodosPago();

        StringBuilder prompt = new StringBuilder();
        prompt.append("Eres el asistente virtual de la tienda online de ").append(nombreTienda)
                .append(", ").append(negocio.frase()).append(". Respondes en español, breve, cordial y directo, ")
                .append("y te comportas como un vendedor experto: propositivo, cálido, te gusta ayudar a decidir y ")
                .append("sugerir combinaciones reales — pero SIEMPRE con productos y precios reales, nunca inventados.\n\n");
        prompt.append("SOLO puedes hablar de esta tienda. Si te preguntan algo fuera de eso, NUNCA la respondas aunque la sepas ")
                .append("— ni siquiera un dato simple como una capital o una fecha. Dilo amablemente y redirige a la tienda. Ejemplo:\n")
                .append("Cliente: \"¿cuál es la capital de Francia?\"\n")
                .append("Tú: \"Eso no lo puedo responder, solo te ayudo con temas de la tienda. ¿Te ayudo con algo de aquí?\"\n\n");
        prompt.append("REGLA MÁS IMPORTANTE, por encima de cualquier otra: el \"Catálogo completo\" y los \"Productos relevantes\" ")
                .append("de más abajo son la ÚNICA fuente de verdad sobre qué vende esta tienda. Nunca menciones un producto, ")
                .append("precio, variante o id que no esté literalmente ahí. Inventar disponibilidad es el peor error que puedes cometer.\n\n");
        prompt.append("Si el cliente pide una categoría marcada como SIN STOCK: dilo con buena onda, sin sonar cortante, y sin ")
                .append("prometer una fecha de reposición que no tienes confirmada — algo como \"por ahora no tenemos eso en catálogo, ")
                .append("seguimos ampliando\" — y si hay algo real en otra categoría que pueda interesarle, ofrécelo también.\n\n");
        if (negocio.vertical() == BusinessVertical.CLOTHING) {
            prompt.append("Si el cliente pide un outfit, combo o te da un presupuesto: arma una propuesta real usando el \"Catálogo ")
                    .append("completo\" de abajo — suma los precios reales de los productos que seleccionas y quédate dentro del ")
                    .append("presupuesto si te dieron uno. Si alguna prenda del outfit (ej. pantalón) está en una categoría SIN STOCK, ")
                    .append("dilo con la misma buena onda de arriba y arma el resto con lo que sí hay — no inventes esa prenda.\n\n");
        }
        prompt.append("Solo cuando tengas un producto real con su id (del catálogo completo o de los productos relevantes), ")
                .append("puedes darle su enlace al cliente: escribe la palabra producto.html?id= seguida directamente del número ")
                .append("de ese id (ej.: si el id es 7, escribes producto.html?id=7 — nunca inventes un número).\n\n");
        prompt.append("Cuidado con esta trampa: que un valor de un atributo del producto (ej. un color) exista, y un valor de otro ")
                .append("atributo (ej. una talla) también exista, NO significa que esa combinación específica esté en stock junta ")
                .append("— revisa \"variantes CON stock\" del producto puntual (más abajo) antes de confirmar una combinación exacta.\n\n");

        prompt.append("Envío: S/ ").append(envio.flatRate()).append(" a todo el Perú, gratis en ")
                .append(envio.freeShippingDistrict()).append(".\n");
        prompt.append("Métodos de pago disponibles: ").append(
                metodosPago.stream().map(PublicMetodoPagoResponse::name).collect(Collectors.joining(", ")))
                .append(".\n\n");

        prompt.append("Catálogo completo, por categoría (úsalo para armar combos/outfits o para saber qué categorías no tienen stock):\n");
        catalogoPorCategoria.forEach((categoria, productos) -> {
            if (productos.isEmpty()) {
                prompt.append("- ").append(categoria).append(": SIN STOCK por ahora\n");
            } else {
                prompt.append("- ").append(categoria).append(": ").append(
                        productos.stream()
                                .map(p -> p.name() + " (id " + p.id() + ", S/ " + precioMostrar(p) + ")")
                                .collect(Collectors.joining("; ")))
                        .append("\n");
            }
        });
        prompt.append("\n");

        if (!productosEspecificos.isEmpty()) {
            prompt.append("Productos relevantes para la pregunta puntual del cliente, con tallas/colores reales. ")
                    .append("\"Variantes CON stock\" lista SOLO las combinaciones talla-color que sí tienen stock — cualquier ")
                    .append("combinación que no aparezca ahí exactamente está agotada:\n");
            productosEspecificos.forEach(p -> {
                PublicProductoDetalleResponse detalle = catalogoService.obtenerProducto(p.id());
                prompt.append("- ").append(p.name()).append(" (id ").append(p.id()).append(")")
                        .append(" — variantes CON stock: ").append(variantesDisponibles(detalle))
                        .append("\n");
            });
        }

        return prompt.toString();
    }

    private String precioMostrar(PublicProductoResumenResponse p) {
        BigDecimal precio = p.promoPrice() != null ? p.promoPrice() : p.price();
        return precio.toPlainString();
    }

    /** Todo el catálogo activo, agrupado por categoría — incluye las categorías sin ningún producto. */
    private Map<String, List<PublicProductoResumenResponse>> catalogoCompletoPorCategoria() {
        List<PublicCategoriaResponse> categorias = catalogoService.listarCategorias();
        List<PublicProductoResumenResponse> todos = catalogoService
                .listarProductos(null, null, null, PageRequest.of(0, MAX_PRODUCTOS_CATALOGO_COMPLETO))
                .content();

        Map<String, List<PublicProductoResumenResponse>> porCategoria = todos.stream()
                .collect(Collectors.groupingBy(PublicProductoResumenResponse::categoryName, LinkedHashMap::new, Collectors.toList()));

        Map<String, List<PublicProductoResumenResponse>> resultado = new LinkedHashMap<>();
        categorias.forEach(c -> resultado.put(c.name(), porCategoria.getOrDefault(c.name(), List.of())));
        return resultado;
    }

    /**
     * True si el cliente (o el historial reciente) nombró una categoría real por su nombre y esa
     * categoría no tiene ningún producto activo — la señal más confiable de "esto no existe, no es
     * un tema de talla/color puntual". Se usa para decidir cuándo aplicar el guardrail de
     * afirmaciones falsas sin bloquear respuestas legítimas tipo "no tenemos X, pero sí Y".
     */
    private boolean categoriaFueNombradaYEstaVacia(String mensajeCliente, List<AsistenteHistorialItem> historial) {
        String contextoReciente = historial.stream().map(AsistenteHistorialItem::content).collect(Collectors.joining(" "))
                + " " + mensajeCliente;
        List<String> palabrasContexto = normalizarMensaje(contextoReciente);

        return catalogoService.listarCategorias().stream()
                .filter(c -> palabrasContexto.contains(normalizarPalabra(c.name())))
                .anyMatch(c -> catalogoService
                        .listarProductos(null, c.id(), null, PageRequest.of(0, 1))
                        .content().isEmpty());
    }

    /**
     * Búsqueda de texto libre por LIKE (ver ProductRepository) no reconoce
     * "camisas en talla s" como relacionado a la categoría "Camisas" — así
     * que primero intentamos resolver una categoría o marca mencionada en el
     * mensaje (o en el historial reciente — "llévame al producto" no dice
     * nada por sí solo, pero si dos turnos antes se habló de "Camisa de
     * Vestir", seguimos entendiendo de qué producto se trata sin depender de
     * que el modelo haya repetido el ID correctamente) y filtramos por eso;
     * solo si no hay match usamos la búsqueda de texto plano del mensaje
     * actual (útil para "tienen la casaca denim?", que sí es un nombre).
     */
    private List<PublicProductoResumenResponse> buscarProductosRelevantes(String mensajeCliente, List<AsistenteHistorialItem> historial) {
        String contextoReciente = historial.stream().map(AsistenteHistorialItem::content).collect(Collectors.joining(" "))
                + " " + mensajeCliente;
        List<String> palabrasContexto = normalizarMensaje(contextoReciente);

        Long categoriaId = catalogoService.listarCategorias().stream()
                .filter(c -> palabrasContexto.contains(normalizarPalabra(c.name())))
                .map(PublicCategoriaResponse::id)
                .findFirst().orElse(null);

        Long marcaId = catalogoService.listarMarcas().stream()
                .filter(m -> palabrasContexto.contains(normalizarPalabra(m.name())))
                .map(m -> m.id())
                .findFirst().orElse(null);

        if (categoriaId != null || marcaId != null) {
            return catalogoService
                    .listarProductos(null, categoriaId, marcaId, PageRequest.of(0, MAX_PRODUCTOS_CONTEXTO))
                    .content();
        }
        return catalogoService
                .listarProductos(mensajeCliente, null, null, PageRequest.of(0, MAX_PRODUCTOS_CONTEXTO))
                .content();
    }

    private List<String> normalizarMensaje(String mensaje) {
        return Arrays.stream(mensaje.toLowerCase().split("\\W+"))
                .map(this::normalizarPalabra)
                .collect(Collectors.toList());
    }

    /** Quita el plural español más común (-es/-s) para poder comparar "camisas" con "Camisa". */
    private String normalizarPalabra(String palabra) {
        String p = palabra.toLowerCase();
        if (p.length() <= MIN_LARGO_PALABRA_NORMALIZABLE) return p;
        if (p.endsWith("es")) return p.substring(0, p.length() - 2);
        if (p.endsWith("s")) return p.substring(0, p.length() - 1);
        return p;
    }

    /**
     * Lista SOLO las combinaciones talla-color con stock real — a propósito no incluye las
     * agotadas ni agrega por talla/color separado. Un modelo barato no siempre distingue bien
     * una etiqueta "(agotado)" dentro de una lista larga (eso causó respuestas falsas: "talla S
     * en beige" cuando el beige real solo tenía stock en talla M, o "talla L disponible" cuando
     * TODA la talla L estaba agotada) — pero si la combinación simplemente no aparece en la
     * lista, no tiene nada que malinterpretar.
     */
    private String variantesDisponibles(PublicProductoDetalleResponse detalle) {
        if (detalle.variants().isEmpty()) return "sin variantes registradas";
        List<String> conStock = detalle.variants().stream()
                .filter(PublicVarianteResponse::inStock)
                .map(PublicVarianteResponse::variantLabel)
                .toList();
        return conStock.isEmpty() ? "AGOTADO en todas las tallas y colores" : String.join(", ", conStock);
    }
}

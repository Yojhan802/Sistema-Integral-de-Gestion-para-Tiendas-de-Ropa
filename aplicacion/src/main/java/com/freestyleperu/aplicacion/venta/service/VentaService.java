package com.freestyleperu.aplicacion.venta.service;

import com.freestyleperu.aplicacion.caja.domain.CashSession;
import com.freestyleperu.aplicacion.caja.domain.CashSessionStatus;
import com.freestyleperu.aplicacion.caja.repository.CashSessionRepository;
import com.freestyleperu.aplicacion.caja.service.CajaService;
import com.freestyleperu.aplicacion.cliente.domain.Customer;
import com.freestyleperu.aplicacion.cliente.repository.CustomerRepository;
import com.freestyleperu.aplicacion.configuracion.service.ConfiguracionService;
import com.freestyleperu.aplicacion.combo.domain.Combo;
import com.freestyleperu.aplicacion.combo.service.ComboService;
import com.freestyleperu.aplicacion.inventario.service.InventarioService;
import com.freestyleperu.aplicacion.facturacion.service.BillingConfigurationService;
import com.freestyleperu.aplicacion.facturacion.service.ElectronicDocumentService;
import com.freestyleperu.aplicacion.pago.domain.PaymentMethod;
import com.freestyleperu.aplicacion.pago.service.PagoService;
import com.freestyleperu.aplicacion.producto.domain.Product;
import com.freestyleperu.aplicacion.producto.domain.ProductVariant;
import com.freestyleperu.aplicacion.promocion.domain.Promocion;
import com.freestyleperu.aplicacion.promocion.service.PromocionService;
import com.freestyleperu.aplicacion.promotor.domain.Promoter;
import com.freestyleperu.aplicacion.promotor.service.PromoterService;
import com.freestyleperu.aplicacion.producto.repository.ProductVariantRepository;
import com.freestyleperu.aplicacion.pedido.domain.PedidoBillingDocumentType;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.exception.OperacionNoPermitidaException;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import com.freestyleperu.aplicacion.shared.util.SequenceService;
import com.freestyleperu.aplicacion.shared.validation.RucValidator;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import com.freestyleperu.aplicacion.venta.domain.Payment;
import com.freestyleperu.aplicacion.venta.domain.PaymentStatus;
import com.freestyleperu.aplicacion.venta.domain.Sale;
import com.freestyleperu.aplicacion.venta.domain.SaleDetail;
import com.freestyleperu.aplicacion.venta.domain.SaleStatus;
import com.freestyleperu.aplicacion.venta.dto.request.AnularVentaRequest;
import com.freestyleperu.aplicacion.venta.dto.request.CrearVentaRequest;
import com.freestyleperu.aplicacion.venta.dto.request.ItemVentaRequest;
import com.freestyleperu.aplicacion.venta.dto.request.PagoVentaRequest;
import com.freestyleperu.aplicacion.venta.dto.response.PagoResponse;
import com.freestyleperu.aplicacion.venta.dto.response.VentaItemResponse;
import com.freestyleperu.aplicacion.venta.dto.response.VentaResponse;
import com.freestyleperu.aplicacion.venta.dto.response.VentaResumenResponse;
import com.freestyleperu.aplicacion.venta.repository.PaymentRepository;
import com.freestyleperu.aplicacion.venta.repository.SaleDetailRepository;
import com.freestyleperu.aplicacion.venta.repository.SaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta la operación más crítica del sistema: una venta completa
 * (detalle + inventario + pagos + caja + auditoría) en una única
 * transacción (docs/02-arquitectura.md §5). Si algo falla, no queda nada
 * a medias.
 */
@Service
@Transactional(readOnly = true)
public class VentaService {

    private final SaleRepository saleRepository;
    private final SaleDetailRepository saleDetailRepository;
    private final PaymentRepository paymentRepository;
    private final ProductVariantRepository variantRepository;
    private final CustomerRepository customerRepository;
    private final CashSessionRepository cashSessionRepository;
    private final UsuarioRepository usuarioRepository;
    private final InventarioService inventarioService;
    private final CajaService cajaService;
    private final PagoService pagoService;
    private final PromoterService promoterService;
    private final ComboService comboService;
    private final PromocionService promocionService;
    private final SequenceService sequenceService;
    private final AuditService auditService;
    private final ElectronicDocumentService electronicDocumentService;
    private final BillingConfigurationService billingConfigurationService;
    private final ConfiguracionService configuracionService;

    public VentaService(SaleRepository saleRepository, SaleDetailRepository saleDetailRepository,
            PaymentRepository paymentRepository, ProductVariantRepository variantRepository,
            CustomerRepository customerRepository, CashSessionRepository cashSessionRepository,
            UsuarioRepository usuarioRepository, InventarioService inventarioService, CajaService cajaService,
            PagoService pagoService, PromoterService promoterService, ComboService comboService,
            PromocionService promocionService, SequenceService sequenceService, AuditService auditService,
            ElectronicDocumentService electronicDocumentService,
            BillingConfigurationService billingConfigurationService,
            ConfiguracionService configuracionService) {
        this.saleRepository = saleRepository;
        this.saleDetailRepository = saleDetailRepository;
        this.paymentRepository = paymentRepository;
        this.variantRepository = variantRepository;
        this.customerRepository = customerRepository;
        this.cashSessionRepository = cashSessionRepository;
        this.usuarioRepository = usuarioRepository;
        this.inventarioService = inventarioService;
        this.cajaService = cajaService;
        this.comboService = comboService;
        this.promocionService = promocionService;
        this.pagoService = pagoService;
        this.promoterService = promoterService;
        this.sequenceService = sequenceService;
        this.auditService = auditService;
        this.electronicDocumentService = electronicDocumentService;
        this.billingConfigurationService = billingConfigurationService;
        this.configuracionService = configuracionService;
    }

    public PageResponse<VentaResumenResponse> listar(Long userId, boolean verTodas, SaleStatus status,
            LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Long filtroUsuario = verTodas ? null : userId;
        return PageResponse.of(saleRepository.buscar(filtroUsuario, null, status, from, to, pageable), this::toResumen);
    }

    public VentaResponse obtener(Long id) {
        Sale sale = buscarOFallar(id);
        return toResponse(sale, saleDetailRepository.findAllBySaleId(id), paymentRepository.findAllBySaleId(id));
    }

    @Transactional
    public VentaResponse registrarVenta(CrearVentaRequest request, Long userId, Set<String> authorities) {
        CashSession session = cashSessionRepository.findById(request.cashSessionId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Sesión de caja", request.cashSessionId()));
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new ReglaDeNegocioException("No hay una sesión de caja abierta");
        }

        Customer customer = request.customerId() != null
                ? customerRepository.findById(request.customerId())
                        .orElseThrow(() -> RecursoNoEncontradoException.de("Cliente", request.customerId()))
                : null;
        Promoter promoter = request.promoterId() != null ? promoterService.obtenerActivoOFallar(request.promoterId()) : null;

        List<ItemVentaRequest> itemsOrdenados = request.items().stream()
                .sorted(Comparator.comparing(ItemVentaRequest::variantId))
                .toList();

        validarModificadoresMutuamenteExcluyentes(itemsOrdenados);

        boolean hayDescuentoManual = tieneImporte(request.discountAmount())
                || itemsOrdenados.stream()
                        .anyMatch(item -> item.comboId() == null && item.promotionId() == null && tieneImporte(item.discountAmount()));
        if (hayDescuentoManual && !authorities.contains(Permisos.VENTAS_DESCUENTO)) {
            throw new OperacionNoPermitidaException("No tienes permisos para aplicar descuentos");
        }

        boolean hayPromocion = itemsOrdenados.stream().anyMatch(item -> item.promotionId() != null);
        if (hayPromocion && !authorities.contains(Permisos.PROMOCIONES_APLICAR)) {
            throw new OperacionNoPermitidaException("No tienes permisos para aplicar promociones");
        }

        Map<Long, ProductVariant> variantesPorId = cargarVariantes(itemsOrdenados);
        List<DetalleCalculado> detalles = calcularDetalles(itemsOrdenados, variantesPorId);

        BigDecimal subtotal = detalles.stream().map(DetalleCalculado::bruto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descuentoGlobal = valorOCero(request.discountAmount());
        BigDecimal descuentoLineas = detalles.stream().map(DetalleCalculado::descuento).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descuentoTotal = descuentoGlobal.add(descuentoLineas);
        BigDecimal total = subtotal.subtract(descuentoTotal);
        if (total.signum() < 0) {
            throw new ReglaDeNegocioException("El descuento no puede dejar el total en negativo");
        }

        BigDecimal sumaPagos = request.payments().stream().map(PagoVentaRequest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumaPagos.compareTo(total) != 0) {
            throw new ReglaDeNegocioException(
                    "La suma de pagos (" + sumaPagos + ") no coincide con el total (" + total + ")");
        }

        DatosComprobante datosComprobante = normalizarComprobante(request, customer, total);

        Usuario vendedor = usuarioRepository.findById(userId).orElseThrow(() -> RecursoNoEncontradoException.de("Usuario", userId));

        Sale sale = new Sale();
        sale.setSaleNumber(sequenceService.next("VENTA", "V001", 8));
        sale.setCustomer(customer);
        sale.setPromoter(promoter);
        sale.setUser(vendedor);
        sale.setCashSession(session);
        sale.setSubtotal(subtotal);
        sale.setDiscountAmount(descuentoTotal);
        sale.setBillingDocumentType(datosComprobante.type());
        sale.setBillingDocumentNumber(datosComprobante.number());
        sale.setBillingName(datosComprobante.name());
        sale.setTotal(total);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setNotes(request.notes());
        sale.setCreatedAt(LocalDateTime.now());
        Sale guardada = saleRepository.save(sale);

        List<SaleDetail> detallesGuardados = new ArrayList<>();
        for (DetalleCalculado dc : detalles) {
            SaleDetail detail = new SaleDetail();
            detail.setSale(guardada);
            detail.setVariant(dc.variant());
            detail.setQuantity(dc.quantity());
            detail.setUnitPrice(dc.unitPrice());
            detail.setDiscountAmount(dc.descuento());
            detail.setSubtotal(dc.subtotal());
            detail.setCombo(dc.combo());
            detail.setPromotion(dc.promotion());
            detail.setProductName(dc.variant().getProduct().getName());
            detail.setVariantSku(dc.variant().getSku());
            detail.setVariantLabel(dc.variant().getVariantLabel());
            detallesGuardados.add(saleDetailRepository.save(detail));
        }

        // Descuenta el stock en un orden global por variant_id, no en el orden de las
        // líneas: así toda transacción que toque varias variantes a la vez (venta,
        // separación, pedido, devolución) adquiere sus bloqueos de fila en el mismo
        // orden, y dos ventas concurrentes con las mismas variantes nunca hacen deadlock
        // entre sí (ver docs/04-reglas-negocio.md, concurrencia).
        for (DetalleCalculado dc : detalles.stream().sorted(Comparator.comparing(d -> d.variant().getId())).toList()) {
            inventarioService.registrarPorVenta(dc.variant().getId(), dc.quantity(), guardada.getId(), userId);
        }

        List<Payment> pagosGuardados = new ArrayList<>();
        for (PagoVentaRequest p : request.payments()) {
            PaymentMethod method = pagoService.obtenerActivoOFallar(p.paymentMethodId());
            Payment payment = new Payment();
            payment.setSale(guardada);
            payment.setPaymentMethod(method);
            payment.setAmount(p.amount());
            payment.setReference(p.reference());
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCreatedAt(LocalDateTime.now());
            pagosGuardados.add(paymentRepository.save(payment));

            if (method.isAffectsCash()) {
                cajaService.registrarPorVenta(session.getId(), p.amount(), guardada.getId(), userId);
            }
        }

        auditService.log("VENTA_CREADA", "VENTA", guardada.getId(), null,
                new Object[] { guardada.getSaleNumber(), total }, AuditResult.SUCCESS);

        return toResponse(guardada, detallesGuardados, pagosGuardados);
    }

    @Transactional
    public VentaResponse anular(Long saleId, AnularVentaRequest request, Long userId) {
        Sale sale = buscarOFallar(saleId);
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new ReglaDeNegocioException("La venta ya está anulada");
        }
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new ReglaDeNegocioException("Solo se pueden anular ventas completadas");
        }

        List<SaleDetail> detalles = saleDetailRepository.findAllBySaleId(saleId);
        List<Payment> pagos = paymentRepository.findAllBySaleId(saleId);
        // Mismo criterio de orden global por variant_id que registrarVenta() — evita deadlocks
        // entre anulaciones/ventas concurrentes que comparten variantes.
        for (SaleDetail detail : detalles.stream().sorted(Comparator.comparing(d -> d.getVariant().getId())).toList()) {
            inventarioService.registrarPorDevolucion(detail.getVariant().getId(), detail.getQuantity(), saleId, userId);
        }

        for (Payment payment : pagos) {
            if (payment.getPaymentMethod().isAffectsCash()) {
                if (sale.getCashSession() == null) {
                    throw new ReglaDeNegocioException(
                            "Esta venta no pasó por caja (proviene de un pedido online) — no se puede anular "
                                    + "un pago que afecta caja sobre ella");
                }
                cajaService.registrarReversion(sale.getCashSession().getId(), payment.getAmount(), saleId, userId);
            }
        }

        // Si ya existe una factura o boleta aceptada, primero se debe neutralizar
        // fiscalmente mediante una nota de credito aceptada por el proveedor configurado. Si la nota
        // queda pendiente o es rechazada, la excepcion provoca rollback de las
        // reversiones locales y la venta sigue COMPLETED para poder reintentarse.
        electronicDocumentService.asegurarNotaCreditoAnulacion(saleId, request.reason(), userId);

        sale.setStatus(SaleStatus.CANCELLED);
        sale.setCancelledAt(LocalDateTime.now());
        sale.setCancelledBy(usuarioRepository.getReferenceById(userId));
        sale.setCancellationReason(request.reason());

        auditService.log("VENTA_ANULADA", "VENTA", sale.getId(), null, request.reason(), AuditResult.SUCCESS);
        return toResponse(sale, detalles, pagos);
    }

    private Map<Long, ProductVariant> cargarVariantes(List<ItemVentaRequest> items) {
        Map<Long, ProductVariant> resultado = new HashMap<>();
        for (ItemVentaRequest item : items) {
            if (resultado.containsKey(item.variantId())) {
                continue;
            }
            ProductVariant variant = variantRepository.findById(item.variantId())
                    .orElseThrow(() -> RecursoNoEncontradoException.de("Variante", item.variantId()));
            resultado.put(item.variantId(), variant);
        }
        return resultado;
    }

    private void validarModificadoresMutuamenteExcluyentes(List<ItemVentaRequest> items) {
        for (ItemVentaRequest item : items) {
            int modificadores = 0;
            if (tieneImporte(item.discountAmount())) {
                modificadores++;
            }
            if (item.comboId() != null) {
                modificadores++;
            }
            if (item.promotionId() != null) {
                modificadores++;
            }
            if (modificadores > 1) {
                throw new ReglaDeNegocioException(
                        "Una línea de venta no puede combinar descuento manual, combo y promoción a la vez");
            }
        }
    }

    private List<DetalleCalculado> calcularDetalles(List<ItemVentaRequest> items, Map<Long, ProductVariant> variantesPorId) {
        List<DetalleCalculado> resultado = new ArrayList<>();

        Map<Long, List<ItemVentaRequest>> itemsPorCombo = new LinkedHashMap<>();
        for (ItemVentaRequest item : items) {
            if (item.comboId() != null) {
                itemsPorCombo.computeIfAbsent(item.comboId(), k -> new ArrayList<>()).add(item);
            }
        }
        for (Map.Entry<Long, List<ItemVentaRequest>> entry : itemsPorCombo.entrySet()) {
            resultado.addAll(calcularDetallesCombo(entry.getKey(), entry.getValue(), variantesPorId));
        }

        for (ItemVentaRequest item : items) {
            if (item.comboId() != null) {
                continue;
            }
            resultado.add(calcularDetalleIndividual(item, variantesPorId));
        }
        return resultado;
    }

    private DetalleCalculado calcularDetalleIndividual(ItemVentaRequest item, Map<Long, ProductVariant> variantesPorId) {
        ProductVariant variant = variantesPorId.get(item.variantId());
        BigDecimal unitPrice = precioVigente(variant);
        BigDecimal bruto = unitPrice.multiply(BigDecimal.valueOf(item.quantity()));

        BigDecimal descuento;
        Promocion promocion = null;
        if (item.promotionId() != null) {
            promocion = promocionService.obtenerAplicableOFallar(item.promotionId(), variant);
            descuento = promocionService.calcularDescuento(promocion, bruto);
        } else {
            descuento = valorOCero(item.discountAmount());
        }

        BigDecimal subtotal = bruto.subtract(descuento);
        if (subtotal.signum() < 0) {
            throw new ReglaDeNegocioException("El descuento de " + variant.getProduct().getName() + " supera su importe");
        }
        return new DetalleCalculado(variant, item.quantity(), unitPrice, descuento, bruto, subtotal, null, promocion);
    }

    /**
     * Un combo se vende como varias líneas normales (una por variante elegida),
     * pero el descuento de cada línea lo calcula el backend, no el cliente: se
     * reparte proporcionalmente al precio normal de cada línea para que la suma
     * final cuadre exactamente con el precio fijo del combo (la última línea
     * absorbe el redondeo, técnica ya usada en otras reparticiones del sistema).
     */
    private List<DetalleCalculado> calcularDetallesCombo(Long comboId, List<ItemVentaRequest> items, Map<Long, ProductVariant> variantesPorId) {
        Combo combo = comboService.obtenerActivoOFallar(comboId);

        record LineaCombo(ItemVentaRequest item, ProductVariant variant, BigDecimal unitPrice, BigDecimal bruto) {
        }
        List<LineaCombo> lineas = new ArrayList<>();
        BigDecimal totalNormal = BigDecimal.ZERO;
        for (ItemVentaRequest item : items) {
            ProductVariant variant = variantesPorId.get(item.variantId());
            BigDecimal unitPrice = precioVigente(variant);
            BigDecimal bruto = unitPrice.multiply(BigDecimal.valueOf(item.quantity()));
            lineas.add(new LineaCombo(item, variant, unitPrice, bruto));
            totalNormal = totalNormal.add(bruto);
        }

        // Reparte cada línea vendida entre las líneas del combo (mismo algoritmo
        // que usa la detección automática del POS, ver ComboService): primero las
        // de producto específico (más restrictivas), luego las de categoría. Acá,
        // a diferencia de la detección automática, no se tolera que sobre nada:
        // toda línea marcada con este comboId debe quedar explicada por el combo.
        List<ComboService.CandidatoCombo> candidatos = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            ProductVariant variant = lineas.get(i).variant();
            Product product = variant.getProduct();
            candidatos.add(new ComboService.CandidatoCombo(
                    i, variant.getId(), product.getId(), product.getCategory().getId(),
                    product.getBrand() != null ? product.getBrand().getId() : null, lineas.get(i).item().quantity()));
        }
        int[] consumido = comboService.consumirCandidatos(combo, candidatos)
                .orElseThrow(() -> new ReglaDeNegocioException("Faltan productos para completar el combo " + combo.getName()));
        for (int i = 0; i < consumido.length; i++) {
            if (consumido[i] != candidatos.get(i).cantidad()) {
                throw new ReglaDeNegocioException(
                        "Los productos elegidos no coinciden con la definición del combo " + combo.getName());
            }
        }

        BigDecimal descuentoTotal = totalNormal.subtract(combo.getPrice());
        if (descuentoTotal.signum() < 0) {
            throw new ReglaDeNegocioException(
                    "El precio del combo " + combo.getName() + " ya no es válido frente al precio actual de sus productos");
        }

        List<DetalleCalculado> resultado = new ArrayList<>();
        BigDecimal descuentoAsignado = BigDecimal.ZERO;
        for (int i = 0; i < lineas.size(); i++) {
            LineaCombo linea = lineas.get(i);
            BigDecimal descuentoLinea;
            if (i == lineas.size() - 1) {
                descuentoLinea = descuentoTotal.subtract(descuentoAsignado);
            } else {
                descuentoLinea = descuentoTotal.multiply(linea.bruto()).divide(totalNormal, 2, RoundingMode.HALF_UP);
                descuentoAsignado = descuentoAsignado.add(descuentoLinea);
            }
            BigDecimal subtotal = linea.bruto().subtract(descuentoLinea);
            resultado.add(new DetalleCalculado(
                    linea.variant(), linea.item().quantity(), linea.unitPrice(), descuentoLinea, linea.bruto(), subtotal, combo, null));
        }
        return resultado;
    }

    private BigDecimal precioVigente(ProductVariant variant) {
        return variant.getProduct().getPromoPrice() != null ? variant.getProduct().getPromoPrice() : variant.getProduct().getPrice();
    }

    private boolean tieneImporte(BigDecimal valor) {
        return valor != null && valor.signum() > 0;
    }

    private BigDecimal valorOCero(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private Sale buscarOFallar(Long id) {
        return saleRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Venta", id));
    }

    private VentaResumenResponse toResumen(Sale sale) {
        return new VentaResumenResponse(
                sale.getId(),
                sale.getSaleNumber(),
                sale.getCustomer() != null ? sale.getCustomer().getFullName() : null,
                sale.getUser().getFullName(),
                sale.getTotal(),
                sale.getStatus(),
                sale.getCreatedAt());
    }

    private VentaResponse toResponse(Sale sale, List<SaleDetail> detalles, List<Payment> pagos) {
        List<VentaItemResponse> items = detalles.stream()
                .map(d -> new VentaItemResponse(
                        d.getVariant().getId(), d.getProductName(), d.getVariantSku(), d.getVariantLabel(),
                        d.getQuantity(), d.getUnitPrice(), d.getDiscountAmount(), d.getSubtotal(),
                        d.getCombo() != null ? d.getCombo().getId() : null,
                        d.getPromotion() != null ? d.getPromotion().getId() : null))
                .toList();
        List<PagoResponse> pagosResponse = pagos.stream()
                .map(p -> new PagoResponse(p.getPaymentMethod().getId(), p.getPaymentMethod().getName(), p.getAmount(), p.getReference()))
                .toList();
        return new VentaResponse(
                sale.getId(),
                sale.getSaleNumber(),
                sale.getCustomer() != null ? sale.getCustomer().getId() : null,
                sale.getCustomer() != null ? sale.getCustomer().getFullName() : null,
                sale.getPromoter() != null ? sale.getPromoter().getId() : null,
                sale.getPromoter() != null ? sale.getPromoter().getName() : null,
                sale.getUser().getId(),
                sale.getUser().getFullName(),
                sale.getSubtotal(),
                sale.getDiscountAmount(),
                sale.getShippingAmount(),
                sale.getTotal(),
                sale.getStatus(),
                sale.getNotes(),
                sale.getCreatedAt(),
                sale.getCancelledAt(),
                sale.getCancelledBy() != null ? sale.getCancelledBy().getUsername() : null,
                sale.getCancellationReason(),
                items,
                pagosResponse,
                sale.getBillingDocumentType() == null ? PedidoBillingDocumentType.TICKET : sale.getBillingDocumentType(),
                sale.getBillingDocumentNumber(),
                sale.getBillingName());
    }

    private DatosComprobante normalizarComprobante(CrearVentaRequest request, Customer customer, BigDecimal total) {
        PedidoBillingDocumentType type = request.billingDocumentType() == null
                ? PedidoBillingDocumentType.TICKET : request.billingDocumentType();
        String number = blankToNull(request.billingDocumentNumber());
        String name = blankToNull(request.billingName());

        if (type == PedidoBillingDocumentType.TICKET) {
            if (number != null || name != null) {
                throw new ReglaDeNegocioException("El ticket interno no requiere datos de facturacion");
            }
            return new DatosComprobante(type, null, null);
        }
        var company = configuracionService.obtener();
        if (!company.electronicInvoicingEnabled() || !RucValidator.isValid(company.ruc())) {
            throw new ReglaDeNegocioException("La facturacion electronica no esta habilitada para esta empresa");
        }
        var billing = billingConfigurationService.obtener();
        if (!billing.enabled() || !billing.configured()) {
            throw new ReglaDeNegocioException("La facturacion electronica no esta lista: configura el proveedor antes de cobrar");
        }

        if (type == PedidoBillingDocumentType.FACTURA) {
            if (!esRucValido(number)) {
                throw new ReglaDeNegocioException("La factura requiere un RUC valido de 11 digitos y digito verificador correcto");
            }
            if (name == null || !name.matches("[\\p{L}\\p{N} .,'&()\\-/]+")) {
                throw new ReglaDeNegocioException("La factura requiere una razon social valida");
            }
            if (billing.invoiceSeries() == null || billing.invoiceSeries().isBlank()) {
                throw new ReglaDeNegocioException("Configura la serie de factura antes de cobrar");
            }
            return new DatosComprobante(type, number, name);
        }

        if (number == null && customer != null && customer.getDocNumber() != null) {
            number = blankToNull(customer.getDocNumber());
        }
        if (number != null && !number.matches("[A-Za-z0-9]{1,15}")) {
            throw new ReglaDeNegocioException("El documento de la boleta solo admite hasta 15 caracteres alfanumericos");
        }
        if (total.compareTo(BigDecimal.valueOf(700)) > 0 && number == null) {
            throw new ReglaDeNegocioException("La boleta por importes mayores a S/ 700 requiere identificar al adquirente");
        }
        if (billing.receiptSeries() == null || billing.receiptSeries().isBlank()) {
            throw new ReglaDeNegocioException("Configura la serie de boleta antes de cobrar");
        }
        return new DatosComprobante(type, number, name);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean esRucValido(String value) {
        if (value == null || !value.matches("\\d{11}")) return false;
        int[] weights = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        int sum = 0;
        for (int index = 0; index < weights.length; index++) {
            sum += Character.digit(value.charAt(index), 10) * weights[index];
        }
        int expected = (11 - (sum % 11)) % 10;
        return expected == Character.digit(value.charAt(10), 10);
    }

    private record DetalleCalculado(
            ProductVariant variant, int quantity, BigDecimal unitPrice, BigDecimal descuento, BigDecimal bruto, BigDecimal subtotal,
            Combo combo, Promocion promotion) {
    }

    private record DatosComprobante(PedidoBillingDocumentType type, String number, String name) {
    }
}

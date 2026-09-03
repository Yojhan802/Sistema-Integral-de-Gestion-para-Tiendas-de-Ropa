package com.freestyleperu.aplicacion.inventario.service;

import com.freestyleperu.aplicacion.inventario.domain.InventoryMovement;
import com.freestyleperu.aplicacion.inventario.domain.MovementType;
import com.freestyleperu.aplicacion.inventario.domain.ReferenceType;
import com.freestyleperu.aplicacion.inventario.domain.Warehouse;
import com.freestyleperu.aplicacion.inventario.dto.request.AjusteInventarioRequest;
import com.freestyleperu.aplicacion.inventario.dto.request.EntradaInventarioRequest;
import com.freestyleperu.aplicacion.inventario.dto.request.SalidaInventarioRequest;
import com.freestyleperu.aplicacion.inventario.dto.response.InventoryItemResponse;
import com.freestyleperu.aplicacion.inventario.dto.response.MovimientoResponse;
import com.freestyleperu.aplicacion.inventario.repository.InventoryMovementRepository;
import com.freestyleperu.aplicacion.inventario.repository.WarehouseRepository;
import com.freestyleperu.aplicacion.producto.domain.ProductVariant;
import com.freestyleperu.aplicacion.producto.repository.ProductVariantRepository;
import com.freestyleperu.aplicacion.producto.service.AjusteStockResultado;
import com.freestyleperu.aplicacion.producto.service.VarianteService;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventarioService {

    private final InventoryMovementRepository movementRepository;
    private final ProductVariantRepository variantRepository;
    private final WarehouseRepository warehouseRepository;
    private final UsuarioRepository usuarioRepository;
    private final VarianteService varianteService;
    private final AuditService auditService;

    public InventarioService(InventoryMovementRepository movementRepository, ProductVariantRepository variantRepository,
            WarehouseRepository warehouseRepository, UsuarioRepository usuarioRepository, VarianteService varianteService,
            AuditService auditService) {
        this.movementRepository = movementRepository;
        this.variantRepository = variantRepository;
        this.warehouseRepository = warehouseRepository;
        this.usuarioRepository = usuarioRepository;
        this.varianteService = varianteService;
        this.auditService = auditService;
    }

    public PageResponse<InventoryItemResponse> listarStock(String search, Pageable pageable) {
        return PageResponse.of(variantRepository.buscarInventario(search, pageable), this::toItem);
    }

    public List<InventoryItemResponse> listarStockBajo() {
        return variantRepository.findLowStock().stream().map(this::toItem).toList();
    }

    public List<InventoryItemResponse> listarAgotados() {
        return variantRepository.findOutOfStock().stream().map(this::toItem).toList();
    }

    public PageResponse<MovimientoResponse> listarMovimientos(Long variantId, MovementType type, LocalDateTime from,
            LocalDateTime to, Pageable pageable) {
        return PageResponse.of(movementRepository.buscar(variantId, type, from, to, pageable), this::toResponse);
    }

    @Transactional
    public MovimientoResponse registrarEntrada(EntradaInventarioRequest request, Long userId) {
        return registrar(request.variantId(), request.warehouseId(), MovementType.ENTRADA, request.quantity(),
                null, null, request.reason(), userId);
    }

    @Transactional
    public MovimientoResponse registrarSalida(SalidaInventarioRequest request, Long userId) {
        return registrar(request.variantId(), request.warehouseId(), MovementType.SALIDA, -request.quantity(),
                null, null, request.reason(), userId);
    }

    @Transactional
    public MovimientoResponse registrarAjuste(AjusteInventarioRequest request, Long userId) {
        ProductVariant variant = variantRepository.findById(request.variantId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Variante", request.variantId()));
        int delta = request.newStock() - variant.getStock();
        if (delta == 0) {
            throw new ReglaDeNegocioException("El nuevo stock coincide con el actual; no hay nada que ajustar");
        }
        return registrar(request.variantId(), request.warehouseId(), MovementType.AJUSTE, delta,
                ReferenceType.ADJUSTMENT, null, request.reason(), userId);
    }

    /** Invocado por {@code VentaService} dentro de su propia transacción al confirmar una venta. */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovimientoResponse registrarPorVenta(Long variantId, int quantity, Long saleId, Long userId) {
        return registrar(variantId, null, MovementType.VENTA, -quantity, ReferenceType.SALE, saleId, null, userId);
    }

    /** Invocado por {@code VentaService} al anular una venta o registrar una devolución. */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovimientoResponse registrarPorDevolucion(Long variantId, int quantity, Long referenceId, Long userId) {
        return registrar(variantId, null, MovementType.DEVOLUCION, quantity, ReferenceType.RETURN, referenceId, null, userId);
    }

    /** Invocado por {@code PedidoService} al cancelar un pedido que ya tenía el pago confirmado (reingresa stock). */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovimientoResponse registrarPorCancelacionPedido(Long variantId, int quantity, Long orderId, Long staffUserId) {
        return registrar(variantId, null, MovementType.DEVOLUCION, quantity, ReferenceType.ORDER, orderId, null, staffUserId);
    }

    /**
     * Invocado por {@code ReservaService} al crear una separación — la
     * prenda queda físicamente apartada de inmediato.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovimientoResponse registrarPorReserva(Long variantId, int quantity, Long reservationId, Long userId) {
        return registrar(variantId, null, MovementType.RESERVA, -quantity, ReferenceType.RESERVATION, reservationId, null, userId);
    }

    /** Invocado por {@code ReservaService} al cancelar o vencer una separación (reingresa stock). */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovimientoResponse registrarPorLiberacionReserva(Long variantId, int quantity, Long reservationId, Long userId) {
        return registrar(variantId, null, MovementType.RESERVA_LIBERADA, quantity, ReferenceType.RESERVATION, reservationId, null, userId);
    }

    /**
     * Invocado por {@code PedidoService} al crear un pedido online — el stock se retiene de
     * inmediato (mismo criterio que una separación física) para que dos pedidos concurrentes
     * por la última unidad no puedan pasar ambos a "pendiente de pago" sobre el mismo stock
     * (corrección del hallazgo ALTA PED-07: antes el stock no se tocaba hasta confirmar el
     * pago, lo que permitía sobreventa entre pedidos pendientes — crítico bajo alta demanda
     * simultánea, ej. lanzamientos o campañas).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovimientoResponse registrarReservaPorPedido(Long variantId, int quantity, Long orderId, Long userId) {
        return registrar(variantId, null, MovementType.RESERVA, -quantity, ReferenceType.ORDER, orderId, null, userId);
    }

    /** Invocado por {@code PedidoService} al anular un pedido que aún no tenía el pago confirmado (libera el stock retenido). */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovimientoResponse registrarLiberacionReservaPorPedido(Long variantId, int quantity, Long orderId, Long userId) {
        return registrar(variantId, null, MovementType.RESERVA_LIBERADA, quantity, ReferenceType.ORDER, orderId, null, userId);
    }

    private MovimientoResponse registrar(Long variantId, Long warehouseId, MovementType type, int delta,
            ReferenceType referenceType, Long referenceId, String reason, Long userId) {
        Warehouse warehouse = resolverAlmacen(warehouseId);
        AjusteStockResultado resultado = varianteService.ajustarStock(variantId, delta);

        InventoryMovement movement = new InventoryMovement();
        movement.setVariant(varianteService.referencia(variantId));
        movement.setWarehouse(warehouse);
        movement.setType(type);
        movement.setQuantity(delta);
        movement.setStockBefore(resultado.stockBefore());
        movement.setStockAfter(resultado.stockAfter());
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setReason(reason);
        movement.setUser(usuarioRepository.getReferenceById(userId));
        movement.setCreatedAt(LocalDateTime.now());
        InventoryMovement guardado = movementRepository.save(movement);

        auditService.log("INVENTARIO_" + type.name(), "VARIANTE", variantId,
                resultado.stockBefore(), resultado.stockAfter(), AuditResult.SUCCESS);

        return toResponse(guardado, resultado.variantSku(), resultado.productName(), warehouse.getName());
    }

    private Warehouse resolverAlmacen(Long warehouseId) {
        if (warehouseId != null) {
            return warehouseRepository.findById(warehouseId)
                    .orElseThrow(() -> RecursoNoEncontradoException.de("Almacén", warehouseId));
        }
        return warehouseRepository.findFirstByStatusOrderByIdAsc(EstadoGeneral.ACTIVE)
                .orElseThrow(() -> new ReglaDeNegocioException("No hay ningún almacén activo configurado"));
    }

    private InventoryItemResponse toItem(ProductVariant variant) {
        return new InventoryItemResponse(
                variant.getId(),
                variant.getProduct().getName(),
                variant.getSku(),
                variant.getBarcode(),
                variant.getVariantLabel(),
                variant.getStock(),
                variant.getMinStock(),
                variant.getStatus());
    }

    private MovimientoResponse toResponse(InventoryMovement movement) {
        return toResponse(movement, movement.getVariant().getSku(), movement.getVariant().getProduct().getName(),
                movement.getWarehouse().getName());
    }

    private MovimientoResponse toResponse(InventoryMovement movement, String variantSku, String productName, String warehouseName) {
        return new MovimientoResponse(
                movement.getId(),
                movement.getVariant().getId(),
                variantSku,
                productName,
                warehouseName,
                movement.getType(),
                movement.getQuantity(),
                movement.getStockBefore(),
                movement.getStockAfter(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getReason(),
                movement.getUser().getUsername(),
                movement.getCreatedAt());
    }
}

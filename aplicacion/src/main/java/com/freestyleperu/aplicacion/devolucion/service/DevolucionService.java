package com.freestyleperu.aplicacion.devolucion.service;

import com.freestyleperu.aplicacion.caja.service.CajaService;
import com.freestyleperu.aplicacion.facturacion.dto.request.NotaItemRequest;
import com.freestyleperu.aplicacion.facturacion.service.ElectronicDocumentService;
import com.freestyleperu.aplicacion.devolucion.domain.Return;
import com.freestyleperu.aplicacion.devolucion.domain.ReturnDetail;
import com.freestyleperu.aplicacion.devolucion.dto.request.CrearDevolucionRequest;
import com.freestyleperu.aplicacion.devolucion.dto.request.ItemDevolucionRequest;
import com.freestyleperu.aplicacion.devolucion.dto.response.DevolucionItemResponse;
import com.freestyleperu.aplicacion.devolucion.dto.response.DevolucionResponse;
import com.freestyleperu.aplicacion.devolucion.dto.response.ReturnableItemResponse;
import com.freestyleperu.aplicacion.devolucion.repository.ReturnDetailRepository;
import com.freestyleperu.aplicacion.devolucion.repository.ReturnRepository;
import com.freestyleperu.aplicacion.inventario.service.InventarioService;
import com.freestyleperu.aplicacion.pago.domain.PaymentMethod;
import com.freestyleperu.aplicacion.pago.service.PagoService;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.shared.util.SequenceService;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import com.freestyleperu.aplicacion.venta.domain.Sale;
import com.freestyleperu.aplicacion.venta.domain.SaleDetail;
import com.freestyleperu.aplicacion.venta.domain.SaleStatus;
import com.freestyleperu.aplicacion.venta.repository.SaleDetailRepository;
import com.freestyleperu.aplicacion.venta.repository.SaleRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DevolucionService {

    private final ReturnRepository returnRepository;
    private final ReturnDetailRepository returnDetailRepository;
    private final SaleRepository saleRepository;
    private final SaleDetailRepository saleDetailRepository;
    private final UsuarioRepository usuarioRepository;
    private final InventarioService inventarioService;
    private final CajaService cajaService;
    private final PagoService pagoService;
    private final SequenceService sequenceService;
    private final AuditService auditService;
    private final ElectronicDocumentService electronicDocumentService;

    public DevolucionService(ReturnRepository returnRepository, ReturnDetailRepository returnDetailRepository,
            SaleRepository saleRepository, SaleDetailRepository saleDetailRepository, UsuarioRepository usuarioRepository,
            InventarioService inventarioService, CajaService cajaService, PagoService pagoService,
            SequenceService sequenceService, AuditService auditService,
            ElectronicDocumentService electronicDocumentService) {
        this.returnRepository = returnRepository;
        this.returnDetailRepository = returnDetailRepository;
        this.saleRepository = saleRepository;
        this.saleDetailRepository = saleDetailRepository;
        this.usuarioRepository = usuarioRepository;
        this.inventarioService = inventarioService;
        this.cajaService = cajaService;
        this.pagoService = pagoService;
        this.sequenceService = sequenceService;
        this.auditService = auditService;
        this.electronicDocumentService = electronicDocumentService;
    }

    public PageResponse<DevolucionResponse> listar(Long saleId, Pageable pageable) {
        return PageResponse.of(returnRepository.buscar(saleId, pageable),
                r -> toResponse(r, returnDetailRepository.findAllByReturnEntityId(r.getId())));
    }

    public DevolucionResponse obtener(Long id) {
        Return devolucion = buscarOFallar(id);
        return toResponse(devolucion, returnDetailRepository.findAllByReturnEntityId(id));
    }

    public List<ReturnableItemResponse> itemsDevolvibles(Long saleId) {
        List<SaleDetail> detalles = saleDetailRepository.findAllBySaleId(saleId);
        if (detalles.isEmpty()) {
            throw RecursoNoEncontradoException.de("Venta", saleId);
        }
        return detalles.stream()
                .map(d -> {
                    int devuelto = returnDetailRepository.cantidadDevueltaPorLinea(d.getId());
                    return new ReturnableItemResponse(
                            d.getId(), d.getVariant().getId(), d.getProductName(), d.getVariantSku(),
                            d.getQuantity(), devuelto, d.getQuantity() - devuelto);
                })
                .toList();
    }

    @Transactional
    public DevolucionResponse registrar(CrearDevolucionRequest request, Long userId) {
        Sale sale = saleRepository.findById(request.saleId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Venta", request.saleId()));
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new ReglaDeNegocioException("No se puede registrar una devolución sobre una venta anulada");
        }
        PaymentMethod refundMethod = pagoService.obtenerActivoOFallar(request.refundMethodId());

        Return devolucion = new Return();
        devolucion.setReturnNumber(sequenceService.next("DEVOLUCION", "D001", 8));
        devolucion.setSale(sale);
        devolucion.setUser(usuarioRepository.getReferenceById(userId));
        devolucion.setReason(request.reason());
        devolucion.setRefundMethod(refundMethod);
        devolucion.setCreatedAt(LocalDateTime.now());

        BigDecimal total = BigDecimal.ZERO;
        List<ReturnDetail> detallesCalculados = new ArrayList<>();
        for (ItemDevolucionRequest item : request.items()) {
            SaleDetail saleDetail = saleDetailRepository.findById(item.saleDetailId())
                    .orElseThrow(() -> RecursoNoEncontradoException.de("Línea de venta", item.saleDetailId()));
            if (!saleDetail.getSale().getId().equals(sale.getId())) {
                throw new ReglaDeNegocioException("La línea " + item.saleDetailId() + " no pertenece a esta venta");
            }
            int yaDevuelto = returnDetailRepository.cantidadDevueltaPorLinea(saleDetail.getId());
            int disponible = saleDetail.getQuantity() - yaDevuelto;
            if (item.quantity() > disponible) {
                throw new ReglaDeNegocioException(
                        "No se puede devolver " + item.quantity() + " unidades de " + saleDetail.getProductName()
                                + ": solo quedan " + disponible + " disponibles para devolver");
            }

            BigDecimal subtotal = saleDetail.getUnitPrice().multiply(BigDecimal.valueOf(item.quantity()));
            total = total.add(subtotal);

            ReturnDetail detail = new ReturnDetail();
            detail.setSaleDetail(saleDetail);
            detail.setVariant(saleDetail.getVariant());
            detail.setQuantity(item.quantity());
            detail.setUnitPrice(saleDetail.getUnitPrice());
            detail.setSubtotal(subtotal);
            detail.setRestock(item.restock());
            detallesCalculados.add(detail);
        }

        electronicDocumentService.asegurarNotaCreditoDevolucion(
                sale.getId(),
                request.reason(),
                detallesCalculados.stream()
                        .map(detail -> new NotaItemRequest(detail.getVariant().getId(), detail.getQuantity()))
                        .toList(),
                "return-" + devolucion.getReturnNumber(),
                userId);

        devolucion.setTotalAmount(total);
        Return guardada = returnRepository.save(devolucion);

        List<ReturnDetail> detallesGuardados = new ArrayList<>();
        for (ReturnDetail detail : detallesCalculados) {
            detail.setReturnEntity(guardada);
            detallesGuardados.add(returnDetailRepository.save(detail));
        }

        // Orden global por variant_id — mismo criterio de concurrencia que VentaService,
        // para que devoluciones/ventas concurrentes con las mismas variantes no hagan deadlock.
        for (ReturnDetail detail : detallesCalculados.stream()
                .filter(ReturnDetail::isRestock)
                .sorted(Comparator.comparing(d -> d.getVariant().getId()))
                .toList()) {
            inventarioService.registrarPorDevolucion(detail.getVariant().getId(), detail.getQuantity(), sale.getId(), userId);
        }

        if (refundMethod.isAffectsCash()) {
            if (sale.getCashSession() == null) {
                throw new ReglaDeNegocioException(
                        "Esta venta no pasó por caja (proviene de un pedido online) — elige un método de "
                                + "reembolso que no afecte caja");
            }
            cajaService.registrarReversion(sale.getCashSession().getId(), total, sale.getId(), userId);
        }

        actualizarEstadoVenta(sale);

        auditService.log("DEVOLUCION_REGISTRADA", "VENTA", sale.getId(), null,
                new Object[] { guardada.getReturnNumber(), total }, AuditResult.SUCCESS);

        return toResponse(guardada, detallesGuardados);
    }

    private void actualizarEstadoVenta(Sale sale) {
        int totalVendido = saleDetailRepository.findAllBySaleId(sale.getId()).stream().mapToInt(SaleDetail::getQuantity).sum();
        int totalDevuelto = saleDetailRepository.findAllBySaleId(sale.getId()).stream()
                .mapToInt(d -> returnDetailRepository.cantidadDevueltaPorLinea(d.getId()))
                .sum();
        sale.setStatus(totalDevuelto >= totalVendido ? SaleStatus.RETURNED : SaleStatus.PARTIALLY_RETURNED);
    }

    private Return buscarOFallar(Long id) {
        return returnRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Devolución", id));
    }

    private DevolucionResponse toResponse(Return devolucion, List<ReturnDetail> detalles) {
        List<DevolucionItemResponse> items = detalles.stream()
                .map(d -> new DevolucionItemResponse(
                        d.getSaleDetail().getId(), d.getVariant().getId(), d.getSaleDetail().getProductName(),
                        d.getSaleDetail().getVariantSku(), d.getQuantity(), d.getUnitPrice(), d.getSubtotal(), d.isRestock()))
                .toList();
        return new DevolucionResponse(
                devolucion.getId(),
                devolucion.getReturnNumber(),
                devolucion.getSale().getId(),
                devolucion.getSale().getSaleNumber(),
                devolucion.getTotalAmount(),
                devolucion.getRefundMethod().getName(),
                devolucion.getReason(),
                devolucion.getUser().getUsername(),
                devolucion.getCreatedAt(),
                items);
    }
}

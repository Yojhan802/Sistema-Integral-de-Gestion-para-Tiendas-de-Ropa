package com.freestyleperu.aplicacion.reporte.web;

import com.freestyleperu.aplicacion.reporte.dto.request.AsistenteReporteRequest;
import com.freestyleperu.aplicacion.reporte.dto.response.AsistenteReporteResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.CajaSesionResumenResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.DashboardResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.IntegracionEstadoResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.ProductoTopResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.PromotorReporteResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.ResumenPeriodoResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.SerieDiaResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.SerieEtiquetaResponse;
import com.freestyleperu.aplicacion.reporte.service.ReporteAsistenteService;
import com.freestyleperu.aplicacion.reporte.service.ReporteService;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('" + Permisos.REPORTES_CONSULTAR + "')")
public class ReporteController {

    private final ReporteService reporteService;
    private final ReporteAsistenteService reporteAsistenteService;

    public ReporteController(ReporteService reporteService, ReporteAsistenteService reporteAsistenteService) {
        this.reporteService = reporteService;
        this.reporteAsistenteService = reporteAsistenteService;
    }

    @GetMapping("/api/reports/dashboard")
    public DashboardResponse dashboard() {
        return reporteService.dashboard();
    }

    @GetMapping("/api/reports/sales/by-day")
    public List<SerieDiaResponse> ventasPorDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reporteService.ventasPorDia(from, to);
    }

    @GetMapping("/api/reports/sales/by-category")
    public List<SerieEtiquetaResponse> ventasPorCategoria(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reporteService.ventasPorCategoria(from, to);
    }

    @GetMapping("/api/reports/sales/by-seller")
    public List<SerieEtiquetaResponse> ventasPorVendedor(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reporteService.ventasPorVendedor(from, to);
    }

    @GetMapping("/api/reports/sales/by-promoter")
    public List<PromotorReporteResponse> ventasPorPromotor(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reporteService.ventasPorPromotor(from, to);
    }

    @GetMapping("/api/reports/sales/by-payment-method")
    public List<SerieEtiquetaResponse> distribucionPorMetodoPago(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reporteService.distribucionPorMetodoPago(from, to);
    }

    /** Solo Yape/Plin/transferencia/etc. (no efectivo, que ya se ve por sesión de caja) — por día calendario. */
    @GetMapping("/api/reports/payments/non-cash")
    public List<SerieEtiquetaResponse> distribucionPagosDigitales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reporteService.distribucionPagosDigitales(from, to);
    }

    @GetMapping("/api/reports/payments/online")
    public List<IntegracionEstadoResponse> pagosOnline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reporteService.pagosOnline(from, to);
    }

    @GetMapping("/api/reports/billing/documents")
    public List<IntegracionEstadoResponse> comprobantesElectronicos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reporteService.comprobantesElectronicos(from, to);
    }

    /** Cantidad y monto total de ventas en el período — "cuántas ventas se hicieron". */
    @GetMapping("/api/reports/sales/summary")
    public ResumenPeriodoResponse resumenVentas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reporteService.resumenVentas(from, to);
    }

    @GetMapping("/api/reports/products/top-selling")
    public List<ProductoTopResponse> topProductos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return reporteService.topProductos(from, to, limit);
    }

    @GetMapping("/api/reports/cash/sessions")
    public List<CajaSesionResumenResponse> sesionesCaja(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reporteService.sesionesCaja(from, to);
    }

    /** "Pregúntale a tus datos" (plan IA) — el frontend manda la pregunta y el reporte que ya tiene en pantalla. */
    @PostMapping("/api/reports/assistant/ask")
    @PreAuthorize("hasAuthority('" + Permisos.REPORTES_CONSULTAR + "') and @modulos.activo('IA')")
    public AsistenteReporteResponse preguntarAsistente(@Valid @RequestBody AsistenteReporteRequest request) {
        return new AsistenteReporteResponse(reporteAsistenteService.responder(request));
    }
}

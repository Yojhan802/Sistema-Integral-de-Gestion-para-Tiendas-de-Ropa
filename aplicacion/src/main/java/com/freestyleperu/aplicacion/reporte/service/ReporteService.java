package com.freestyleperu.aplicacion.reporte.service;

import com.freestyleperu.aplicacion.caja.domain.CashSession;
import com.freestyleperu.aplicacion.caja.repository.CashSessionRepository;
import com.freestyleperu.aplicacion.inventario.service.InventarioService;
import com.freestyleperu.aplicacion.reporte.dto.response.CajaSesionResumenResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.DashboardResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.IntegracionEstadoResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.ProductoTopResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.PromotorReporteResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.ResumenPeriodoResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.SerieDiaResponse;
import com.freestyleperu.aplicacion.reporte.dto.response.SerieEtiquetaResponse;
import com.freestyleperu.aplicacion.venta.repository.PaymentRepository;
import com.freestyleperu.aplicacion.venta.repository.SaleDetailRepository;
import com.freestyleperu.aplicacion.venta.repository.SaleRepository;
import com.freestyleperu.aplicacion.pago.repository.PaymentTransactionRepository;
import com.freestyleperu.aplicacion.facturacion.repository.ElectronicDocumentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReporteService {

    private final SaleRepository saleRepository;
    private final SaleDetailRepository saleDetailRepository;
    private final PaymentRepository paymentRepository;
    private final CashSessionRepository cashSessionRepository;
    private final InventarioService inventarioService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ElectronicDocumentRepository electronicDocumentRepository;

    public ReporteService(SaleRepository saleRepository, SaleDetailRepository saleDetailRepository,
            PaymentRepository paymentRepository, CashSessionRepository cashSessionRepository,
            InventarioService inventarioService, PaymentTransactionRepository paymentTransactionRepository,
            ElectronicDocumentRepository electronicDocumentRepository) {
        this.saleRepository = saleRepository;
        this.saleDetailRepository = saleDetailRepository;
        this.paymentRepository = paymentRepository;
        this.cashSessionRepository = cashSessionRepository;
        this.inventarioService = inventarioService;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.electronicDocumentRepository = electronicDocumentRepository;
    }

    public DashboardResponse dashboard() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime inicioHoy = hoy.atStartOfDay();
        LocalDateTime inicioManana = hoy.plusDays(1).atStartOfDay();
        LocalDateTime inicioMes = YearMonth.from(hoy).atDay(1).atStartOfDay();
        LocalDateTime inicioMesSiguiente = YearMonth.from(hoy).plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime inicioUltimos7 = hoy.minusDays(6).atStartOfDay();

        ResumenPeriodoResponse ventasHoy = resumen(inicioHoy, inicioManana);
        ResumenPeriodoResponse ventasMes = resumen(inicioMes, inicioMesSiguiente);
        long productosVendidosHoy = saleDetailRepository.unidadesVendidas(inicioHoy, inicioManana);

        return new DashboardResponse(
                ventasHoy,
                ventasMes,
                productosVendidosHoy,
                inventarioService.listarStockBajo().size(),
                inventarioService.listarAgotados().size(),
                distribucion(paymentRepository.distribucionPorMetodo(inicioHoy, inicioManana)),
                serieDiaria(hoy.minusDays(6), hoy, inicioUltimos7, inicioManana),
                topProductos(inicioMes, inicioMesSiguiente, 5));
    }

    public List<SerieDiaResponse> ventasPorDia(LocalDate from, LocalDate to) {
        LocalDate desde = from != null ? from : LocalDate.now().minusDays(6);
        LocalDate hasta = to != null ? to : LocalDate.now();
        return serieDiaria(desde, hasta, desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());
    }

    public List<SerieEtiquetaResponse> ventasPorCategoria(LocalDate from, LocalDate to) {
        LocalDateTime[] rango = rango(from, to);
        return distribucion(saleDetailRepository.ventasPorCategoria(rango[0], rango[1]));
    }

    public List<SerieEtiquetaResponse> ventasPorVendedor(LocalDate from, LocalDate to) {
        LocalDateTime[] rango = rango(from, to);
        return distribucion(saleRepository.ventasPorVendedor(rango[0], rango[1]));
    }

    public List<PromotorReporteResponse> ventasPorPromotor(LocalDate from, LocalDate to) {
        LocalDateTime[] rango = rango(from, to);
        return saleRepository.ventasPorPromotor(rango[0], rango[1]).stream()
                .map(fila -> new PromotorReporteResponse((String) fila[0], (Long) fila[1], (BigDecimal) fila[2]))
                .toList();
    }

    public List<SerieEtiquetaResponse> distribucionPorMetodoPago(LocalDate from, LocalDate to) {
        LocalDateTime[] rango = rango(from, to);
        return distribucion(paymentRepository.distribucionPorMetodo(rango[0], rango[1]));
    }

    /** Solo métodos que no afectan caja (Yape, Plin, transferencia...) — ver PaymentRepository.distribucionPorMetodoNoEfectivo. */
    public List<SerieEtiquetaResponse> distribucionPagosDigitales(LocalDate from, LocalDate to) {
        LocalDateTime[] rango = rango(from, to);
        return distribucion(paymentRepository.distribucionPorMetodoNoEfectivo(rango[0], rango[1]));
    }

    /** Cantidad y monto total de ventas completadas en el período — para "cuántas ventas se hicieron". */
    public ResumenPeriodoResponse resumenVentas(LocalDate from, LocalDate to) {
        LocalDateTime[] rango = rango(from, to);
        return resumen(rango[0], rango[1]);
    }

    public List<ProductoTopResponse> topProductos(LocalDate from, LocalDate to, int limit) {
        LocalDateTime[] rango = rango(from, to);
        return topProductos(rango[0], rango[1], limit);
    }

    public List<CajaSesionResumenResponse> sesionesCaja(LocalDate from, LocalDate to) {
        LocalDateTime[] rango = rango(from, to);
        return cashSessionRepository.sesionesCerradasEntre(rango[0], rango[1]).stream()
                .map(this::toCajaResumen)
                .toList();
    }

    public List<IntegracionEstadoResponse> pagosOnline(LocalDate from, LocalDate to) {
        LocalDateTime[] rango = rango(from, to);
        return paymentTransactionRepository.resumenPorEstado(rango[0], rango[1]).stream()
                .map(fila -> new IntegracionEstadoResponse(
                        fila[0].toString(), fila[1].toString(), ((Number) fila[2]).longValue(), (BigDecimal) fila[3]))
                .toList();
    }

    public List<IntegracionEstadoResponse> comprobantesElectronicos(LocalDate from, LocalDate to) {
        LocalDateTime[] rango = rango(from, to);
        return electronicDocumentRepository.resumenPorEstado(rango[0], rango[1]).stream()
                .map(fila -> new IntegracionEstadoResponse(
                        fila[0].toString(), fila[1].toString(), ((Number) fila[2]).longValue(), (BigDecimal) fila[3]))
                .toList();
    }

    private ResumenPeriodoResponse resumen(LocalDateTime from, LocalDateTime to) {
        Object[] fila = saleRepository.resumen(from, to).get(0);
        return new ResumenPeriodoResponse((Long) fila[0], (BigDecimal) fila[1]);
    }

    private List<SerieDiaResponse> serieDiaria(LocalDate desde, LocalDate hasta, LocalDateTime from, LocalDateTime to) {
        Map<LocalDate, BigDecimal> porFecha = new LinkedHashMap<>();
        for (Object[] fila : saleRepository.ventasPorDia(from, to)) {
            porFecha.put(aLocalDate(fila[0]), (BigDecimal) fila[1]);
        }
        List<SerieDiaResponse> resultado = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            resultado.add(new SerieDiaResponse(fecha, porFecha.getOrDefault(fecha, BigDecimal.ZERO)));
        }
        return resultado;
    }

    private List<ProductoTopResponse> topProductos(LocalDateTime from, LocalDateTime to, int limit) {
        return saleDetailRepository.topProductos(from, to, PageRequest.of(0, limit)).stream()
                .map(fila -> new ProductoTopResponse((String) fila[0], (Long) fila[1], (BigDecimal) fila[2]))
                .toList();
    }

    private List<SerieEtiquetaResponse> distribucion(List<Object[]> filas) {
        BigDecimal total = filas.stream().map(f -> (BigDecimal) f[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SerieEtiquetaResponse> resultado = new ArrayList<>();
        for (Object[] fila : filas) {
            BigDecimal valor = (BigDecimal) fila[1];
            Double porcentaje = total.signum() > 0
                    ? valor.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;
            resultado.add(new SerieEtiquetaResponse((String) fila[0], valor, porcentaje));
        }
        return resultado;
    }

    private CajaSesionResumenResponse toCajaResumen(CashSession session) {
        return new CajaSesionResumenResponse(
                session.getId(),
                session.getCashRegister().getName(),
                session.getOpenedBy().getUsername(),
                session.getClosedBy() != null ? session.getClosedBy().getUsername() : null,
                session.getOpenedAt(),
                session.getClosedAt(),
                session.getOpeningAmount(),
                session.getExpectedAmount(),
                session.getCountedAmount(),
                session.getDifference());
    }

    private LocalDate aLocalDate(Object valor) {
        if (valor instanceof LocalDate localDate) {
            return localDate;
        }
        if (valor instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (valor instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (valor instanceof java.util.Date utilDate) {
            return utilDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        throw new IllegalStateException("Tipo de fecha inesperado: " + valor.getClass());
    }

    private LocalDateTime[] rango(LocalDate from, LocalDate to) {
        LocalDate desde = from != null ? from : YearMonth.now().atDay(1);
        LocalDate hasta = to != null ? to : LocalDate.now();
        return new LocalDateTime[] { desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay() };
    }
}

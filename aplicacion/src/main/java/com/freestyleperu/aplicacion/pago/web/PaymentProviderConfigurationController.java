package com.freestyleperu.aplicacion.pago.web;

import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;
import com.freestyleperu.aplicacion.pago.dto.request.ActualizarPaymentProviderRequest;
import com.freestyleperu.aplicacion.pago.dto.response.PaymentProviderResponse;
import com.freestyleperu.aplicacion.pago.dto.response.PublicPaymentProviderResponse;
import com.freestyleperu.aplicacion.pago.service.PaymentProviderConfigurationService;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentProviderConfigurationController {

    private final PaymentProviderConfigurationService service;

    public PaymentProviderConfigurationController(PaymentProviderConfigurationService service) {
        this.service = service;
    }

    @GetMapping("/api/settings/payment-providers")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_PAGOS + "')")
    public List<PaymentProviderResponse> listar() {
        return service.listar();
    }

    @GetMapping("/api/store/catalog/payment-providers")
    public List<PublicPaymentProviderResponse> listarPublicos() {
        return service.listarPublicos();
    }

    @PutMapping("/api/settings/payment-providers/{provider}")
    @PreAuthorize("hasAuthority('" + Permisos.CONFIGURACION_PAGOS + "')")
    public PaymentProviderResponse actualizar(
            @PathVariable PaymentProviderType provider,
            @Valid @RequestBody ActualizarPaymentProviderRequest request) {
        return service.actualizar(provider, request);
    }
}

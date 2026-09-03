package com.freestyleperu.aplicacion.pago.web;

import com.freestyleperu.aplicacion.pago.dto.request.CrearPaymentTransactionRequest;
import com.freestyleperu.aplicacion.pago.dto.request.ProcesarPaymentTransactionRequest;
import com.freestyleperu.aplicacion.pago.dto.response.PaymentTransactionResponse;
import com.freestyleperu.aplicacion.pago.service.PaymentTransactionService;
import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentTransactionController {

    private final PaymentTransactionService service;

    public PaymentTransactionController(PaymentTransactionService service) {
        this.service = service;
    }

    @PostMapping("/api/store/orders/{orderId}/payment-transactions")
    @PreAuthorize("hasAuthority('" + Permisos.ROLE_CUSTOMER + "') and @modulos.activo('TIENDA')")
    public PaymentTransactionResponse crear(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CrearPaymentTransactionRequest request) {
        return service.crearParaPedido(orderId, currentUser.id(), request, idempotencyKey);
    }

    @GetMapping("/api/store/payment-transactions/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.ROLE_CUSTOMER + "') and @modulos.activo('TIENDA')")
    public PaymentTransactionResponse obtener(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return service.obtenerPropia(id, currentUser.id());
    }

    @PostMapping("/api/store/payment-transactions/{id}/charge")
    @PreAuthorize("hasAuthority('" + Permisos.ROLE_CUSTOMER + "') and @modulos.activo('TIENDA')")
    public PaymentTransactionResponse procesar(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ProcesarPaymentTransactionRequest request) {
        return service.procesar(id, currentUser.id(), request);
    }

    @GetMapping("/api/store/payment-transactions/{id}/checkout")
    @PreAuthorize("hasAuthority('" + Permisos.ROLE_CUSTOMER + "') and @modulos.activo('TIENDA')")
    public com.freestyleperu.aplicacion.pago.dto.response.PaymentProviderCheckoutResponse inicializarCheckout(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return service.inicializarCheckout(id, currentUser.id());
    }
}

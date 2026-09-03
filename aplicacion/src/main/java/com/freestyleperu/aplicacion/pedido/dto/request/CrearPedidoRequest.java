package com.freestyleperu.aplicacion.pedido.dto.request;

import com.freestyleperu.aplicacion.pedido.domain.PedidoBillingDocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CrearPedidoRequest(
        @NotEmpty @Valid List<ItemPedidoRequest> items,
        @NotNull Long paymentMethodId,
        @Size(max = 50) String paymentReference,
        @NotBlank @Pattern(regexp = "\\d{8}", message = "debe contener exactamente 8 dígitos") @Size(max = 8) String recipientDni,
        @NotBlank @Pattern(regexp = "[\\p{L} .'-]+", message = "solo admite letras, espacios, apóstrofes y guiones") @Size(max = 100) String recipientFirstName,
        @NotBlank @Pattern(regexp = "[\\p{L} .'-]+", message = "solo admite letras, espacios, apóstrofes y guiones") @Size(max = 60) String recipientLastNamePaterno,
        @NotBlank @Pattern(regexp = "[\\p{L} .'-]+", message = "solo admite letras, espacios, apóstrofes y guiones") @Size(max = 60) String recipientLastNameMaterno,
        @NotBlank @Pattern(regexp = "9\\d{8}", message = "debe contener 9 dígitos y comenzar en 9") @Size(max = 9) String phone,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 100) String department,
        @NotBlank @Size(max = 100) String province,
        @NotBlank @Size(max = 100) String district,
        @Size(max = 255) String notes,
        PedidoBillingDocumentType billingDocumentType,
        @Size(max = 15) String billingDocumentNumber,
        @Size(max = 150) String billingName,

        /**
         * Aceptación expresa de los Términos y Condiciones. La contratación a distancia
         * exige el consentimiento informado del consumidor, así que el pedido no se crea
         * sin él y se guarda cuándo y sobre qué versión se aceptó.
         */
        @NotNull(message = "debes aceptar los términos y condiciones")
        @AssertTrue(message = "debes aceptar los términos y condiciones") Boolean acceptedTerms,
        @Size(max = 20) String termsVersion) {

    /**
     * Compatibilidad con clientes internos que todavía no envían datos de comprobante.
     * Marca la aceptación como dada porque este constructor no atiende peticiones HTTP:
     * el consentimiento del comprador se valida en el borde, sobre el cuerpo recibido.
     */
    public CrearPedidoRequest(
            List<ItemPedidoRequest> items,
            Long paymentMethodId,
            String paymentReference,
            String recipientDni,
            String recipientFirstName,
            String recipientLastNamePaterno,
            String recipientLastNameMaterno,
            String phone,
            String address,
            String department,
            String province,
            String district,
            String notes) {
        this(items, paymentMethodId, paymentReference, recipientDni, recipientFirstName,
                recipientLastNamePaterno, recipientLastNameMaterno, phone, address,
                department, province, district, notes, null, null, null, Boolean.TRUE, null);
    }

    /** Compatibilidad con llamadas internas anteriores a la aceptación de términos. */
    public CrearPedidoRequest(
            List<ItemPedidoRequest> items,
            Long paymentMethodId,
            String paymentReference,
            String recipientDni,
            String recipientFirstName,
            String recipientLastNamePaterno,
            String recipientLastNameMaterno,
            String phone,
            String address,
            String department,
            String province,
            String district,
            String notes,
            PedidoBillingDocumentType billingDocumentType,
            String billingDocumentNumber,
            String billingName) {
        this(items, paymentMethodId, paymentReference, recipientDni, recipientFirstName,
                recipientLastNamePaterno, recipientLastNameMaterno, phone, address,
                department, province, district, notes, billingDocumentType, billingDocumentNumber,
                billingName, Boolean.TRUE, null);
    }
}

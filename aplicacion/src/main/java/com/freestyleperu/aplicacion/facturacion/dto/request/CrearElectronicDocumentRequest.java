package com.freestyleperu.aplicacion.facturacion.dto.request;

import com.freestyleperu.aplicacion.facturacion.domain.ElectronicDocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CrearElectronicDocumentRequest(
        @NotNull ElectronicDocumentType documentType,
        Long sourceDocumentId,
        @Size(max = 2) String reasonCode,
        @Size(max = 250) String reasonDescription,
        List<@Valid NotaItemRequest> items) {
}

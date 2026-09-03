package com.freestyleperu.aplicacion.reclamo.dto.response;

import com.freestyleperu.aplicacion.reclamo.domain.ComplaintStatus;
import com.freestyleperu.aplicacion.reclamo.domain.ComplaintType;
import java.time.LocalDateTime;

/** Vista mínima para consultar el código entregado al consumidor, sin exponer sus datos personales. */
public record PublicComplaintResponse(
        String entryNumber,
        ComplaintType type,
        ComplaintStatus status,
        String providerName,
        String response,
        LocalDateTime createdAt,
        LocalDateTime respondedAt) {
}

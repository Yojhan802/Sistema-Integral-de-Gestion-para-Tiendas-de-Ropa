package com.freestyleperu.aplicacion.reclamo.dto.request;

import com.freestyleperu.aplicacion.reclamo.domain.ComplaintType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateComplaintRequest(
        @NotNull ComplaintType type,
        @NotBlank @Size(max = 150) String consumerName,
        @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9 .-]*", message = "contiene caracteres no permitidos") String consumerDocument,
        @NotBlank @Email @Size(max = 150) String consumerEmail,
        @Size(max = 20) @Pattern(regexp = "[0-9+() -]*", message = "contiene caracteres no permitidos") String consumerPhone,
        @NotBlank @Size(max = 255) String consumerAddress,
        @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9-]*", message = "contiene caracteres no permitidos") String orderNumber,
        @NotBlank @Size(max = 255) String productServiceDescription,
        @DecimalMin(value = "0.00") BigDecimal amount,
        @NotBlank @Size(max = 5000) String detail,
        @NotBlank @Size(max = 3000) String consumerRequest) {
}

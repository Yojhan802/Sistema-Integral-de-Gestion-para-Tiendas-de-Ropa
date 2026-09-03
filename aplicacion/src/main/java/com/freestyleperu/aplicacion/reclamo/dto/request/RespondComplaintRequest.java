package com.freestyleperu.aplicacion.reclamo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RespondComplaintRequest(
        @NotBlank @Size(max = 5000) String response,
        boolean close) {
}

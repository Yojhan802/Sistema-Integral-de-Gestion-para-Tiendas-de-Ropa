package com.freestyleperu.aplicacion.ia.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AsistenteChatRequest(
        @NotBlank @Size(max = 500) String message,
        @Valid List<AsistenteHistorialItem> history) {

    public List<AsistenteHistorialItem> historyOrEmpty() {
        return history == null ? List.of() : history;
    }
}

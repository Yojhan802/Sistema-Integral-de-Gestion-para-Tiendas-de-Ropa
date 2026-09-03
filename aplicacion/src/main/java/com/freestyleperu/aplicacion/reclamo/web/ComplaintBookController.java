package com.freestyleperu.aplicacion.reclamo.web;

import com.freestyleperu.aplicacion.reclamo.dto.request.CreateComplaintRequest;
import com.freestyleperu.aplicacion.reclamo.dto.request.RespondComplaintRequest;
import com.freestyleperu.aplicacion.reclamo.dto.response.ComplaintReceiptResponse;
import com.freestyleperu.aplicacion.reclamo.dto.response.ComplaintResponse;
import com.freestyleperu.aplicacion.reclamo.dto.response.PublicComplaintResponse;
import com.freestyleperu.aplicacion.reclamo.service.ComplaintBookService;
import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ComplaintBookController {

    private final ComplaintBookService service;

    public ComplaintBookController(ComplaintBookService service) { this.service = service; }

    /**
     * Devuelve la constancia completa (D.S. 011-2011-PCM, Art. 5) porque es la única
     * respuesta dirigida a quien acaba de declarar esos datos. La consulta posterior
     * por número usa {@link #get} y nunca expone datos personales.
     */
    @PostMapping("/api/store/complaints")
    public ResponseEntity<ComplaintReceiptResponse> create(@Valid @RequestBody CreateComplaintRequest request) {
        ComplaintReceiptResponse receipt = service.createAndIssueReceipt(request);
        return ResponseEntity.created(URI.create("/api/store/complaints/" + receipt.entryNumber())).body(receipt);
    }

    @GetMapping("/api/store/complaints/{entryNumber}")
    public PublicComplaintResponse get(@PathVariable String entryNumber) { return service.getPublicByNumber(entryNumber); }

    @GetMapping("/api/complaints")
    @PreAuthorize("hasAuthority('" + Permisos.RECLAMOS_CONSULTAR + "')")
    public PageResponse<ComplaintResponse> list(Pageable pageable) { return service.list(pageable); }

    @PatchMapping("/api/complaints/{id}/response")
    @PreAuthorize("hasAuthority('" + Permisos.RECLAMOS_RESPONDER + "')")
    public ComplaintResponse respond(@PathVariable Long id, @Valid @RequestBody RespondComplaintRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return service.respond(id, request, currentUser.id());
    }
}

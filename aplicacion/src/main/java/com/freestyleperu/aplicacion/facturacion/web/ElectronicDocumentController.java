package com.freestyleperu.aplicacion.facturacion.web;

import com.freestyleperu.aplicacion.facturacion.dto.request.CrearElectronicDocumentRequest;
import com.freestyleperu.aplicacion.facturacion.dto.response.ElectronicDocumentResponse;
import com.freestyleperu.aplicacion.facturacion.service.ElectronicDocumentService;
import com.freestyleperu.aplicacion.facturacion.port.ElectronicInvoicingResource;
import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ElectronicDocumentController {

    private final ElectronicDocumentService service;

    public ElectronicDocumentController(ElectronicDocumentService service) {
        this.service = service;
    }

    @GetMapping("/api/sales/{saleId}/electronic-documents")
    @PreAuthorize("hasAuthority('" + Permisos.VENTAS_CONSULTAR + "')")
    public List<ElectronicDocumentResponse> listar(@PathVariable Long saleId) {
        return service.listarPorVenta(saleId);
    }

    @PostMapping("/api/sales/{saleId}/electronic-documents")
    @PreAuthorize("hasAuthority('" + Permisos.VENTAS_CREAR + "')")
    public ResponseEntity<ElectronicDocumentResponse> crear(
            @PathVariable Long saleId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CrearElectronicDocumentRequest request) {
        ElectronicDocumentResponse response = service.crearBorrador(saleId, request, idempotencyKey, currentUser.id());
        return ResponseEntity.created(URI.create("/api/electronic-documents/" + response.id())).body(response);
    }

    @PostMapping("/api/electronic-documents/{id}/submit")
    @PreAuthorize("hasAuthority('" + Permisos.VENTAS_CREAR + "')")
    public ElectronicDocumentResponse enviar(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return service.enviar(id, currentUser.id());
    }

    @GetMapping("/api/electronic-documents/{id}/status")
    @PreAuthorize("hasAuthority('" + Permisos.VENTAS_CONSULTAR + "')")
    public ElectronicDocumentResponse actualizarEstado(@PathVariable Long id) {
        return service.actualizarEstado(id);
    }

    @PostMapping("/api/electronic-documents/{id}/retry")
    @PreAuthorize("hasAuthority('" + Permisos.VENTAS_CREAR + "')")
    public ElectronicDocumentResponse reintentar(@PathVariable Long id) {
        return service.reintentar(id);
    }

    @GetMapping("/api/electronic-documents/{id}/pdf")
    @PreAuthorize("hasAuthority('" + Permisos.VENTAS_CONSULTAR + "')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return recurso(id, "pdf");
    }

    @GetMapping("/api/electronic-documents/{id}/xml")
    @PreAuthorize("hasAuthority('" + Permisos.VENTAS_CONSULTAR + "')")
    public ResponseEntity<byte[]> xml(@PathVariable Long id) {
        return recurso(id, "xml");
    }

    @GetMapping("/api/electronic-documents/{id}/cdr")
    @PreAuthorize("hasAuthority('" + Permisos.VENTAS_CONSULTAR + "')")
    public ResponseEntity<byte[]> cdr(@PathVariable Long id) {
        return recurso(id, "cdr");
    }

    private ResponseEntity<byte[]> recurso(Long id, String resource) {
        ElectronicInvoicingResource file = service.descargar(id, resource);
        MediaType mediaType = MediaType.parseMediaType(file.contentType());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentLength(file.content().length);
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.fileName()).build());
        return new ResponseEntity<>(file.content(), headers, 200);
    }
}

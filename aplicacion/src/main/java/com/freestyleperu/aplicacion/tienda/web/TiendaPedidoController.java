package com.freestyleperu.aplicacion.tienda.web;

import com.freestyleperu.aplicacion.pedido.dto.request.CrearPedidoRequest;
import com.freestyleperu.aplicacion.pedido.dto.response.PedidoResponse;
import com.freestyleperu.aplicacion.pedido.dto.response.PedidoResumenResponse;
import com.freestyleperu.aplicacion.pedido.service.PedidoService;
import com.freestyleperu.aplicacion.facturacion.dto.response.ElectronicDocumentResponse;
import com.freestyleperu.aplicacion.facturacion.port.ElectronicInvoicingResource;
import com.freestyleperu.aplicacion.facturacion.service.ElectronicDocumentService;
import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Pedidos del propio cliente autenticado — ver también {@code pedido.web.PedidoController} para la vista de staff. */
@RestController
@PreAuthorize("hasAuthority('" + Permisos.ROLE_CUSTOMER + "') and @modulos.activo('TIENDA')")
public class TiendaPedidoController {

    private final PedidoService pedidoService;
    private final ElectronicDocumentService electronicDocumentService;

    public TiendaPedidoController(PedidoService pedidoService, ElectronicDocumentService electronicDocumentService) {
        this.pedidoService = pedidoService;
        this.electronicDocumentService = electronicDocumentService;
    }

    @PostMapping("/api/store/orders")
    public ResponseEntity<PedidoResponse> crear(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CrearPedidoRequest request) {
        PedidoResponse creado = pedidoService.crear(request, currentUser.id());
        return ResponseEntity.created(URI.create("/api/store/orders/" + creado.id())).body(creado);
    }

    @GetMapping("/api/store/orders")
    public PageResponse<PedidoResumenResponse> listarPropios(
            @AuthenticationPrincipal AuthenticatedUser currentUser, Pageable pageable) {
        return pedidoService.listarPropios(currentUser.id(), pageable);
    }

    @GetMapping("/api/store/orders/{id}")
    public PedidoResponse obtenerPropio(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return pedidoService.obtenerPropio(id, currentUser.id());
    }

    @PostMapping("/api/store/orders/{id}/payment-proof")
    public PedidoResponse subirComprobante(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam("file") MultipartFile file) {
        return pedidoService.subirComprobante(id, currentUser.id(), file);
    }

    @GetMapping("/api/store/orders/{id}/electronic-documents")
    public java.util.List<ElectronicDocumentResponse> listarComprobantes(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return electronicDocumentService.listarPorPedidoPropio(id, currentUser.id());
    }

    @GetMapping("/api/store/orders/{orderId}/electronic-documents/{documentId}/{resource}")
    public ResponseEntity<byte[]> descargarComprobante(
            @PathVariable Long orderId,
            @PathVariable Long documentId,
            @PathVariable String resource,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        ElectronicInvoicingResource file = electronicDocumentService.descargarPorPedidoPropio(
                orderId, documentId, currentUser.id(), resource);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentLength(file.content().length);
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.fileName()).build());
        return new ResponseEntity<>(file.content(), headers, 200);
    }
}

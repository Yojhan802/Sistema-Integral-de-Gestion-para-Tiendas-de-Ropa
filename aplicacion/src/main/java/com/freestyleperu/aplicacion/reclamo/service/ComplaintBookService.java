package com.freestyleperu.aplicacion.reclamo.service;

import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.reclamo.domain.ComplaintBookEntry;
import com.freestyleperu.aplicacion.reclamo.domain.ComplaintStatus;
import com.freestyleperu.aplicacion.reclamo.dto.request.CreateComplaintRequest;
import com.freestyleperu.aplicacion.reclamo.dto.request.RespondComplaintRequest;
import com.freestyleperu.aplicacion.reclamo.dto.response.ComplaintReceiptResponse;
import com.freestyleperu.aplicacion.reclamo.dto.response.ComplaintResponse;
import com.freestyleperu.aplicacion.reclamo.dto.response.PublicComplaintResponse;
import com.freestyleperu.aplicacion.reclamo.repository.ComplaintBookEntryRepository;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.shared.security.TenantContext;
import com.freestyleperu.aplicacion.shared.util.SequenceService;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ComplaintBookService {

    private final ComplaintBookEntryRepository repository;
    private final CompanySettingsRepository companySettingsRepository;
    private final UsuarioRepository usuarioRepository;
    private final SequenceService sequenceService;
    private final AuditService auditService;

    public ComplaintBookService(ComplaintBookEntryRepository repository,
            CompanySettingsRepository companySettingsRepository, UsuarioRepository usuarioRepository,
            SequenceService sequenceService, AuditService auditService) {
        this.repository = repository;
        this.companySettingsRepository = companySettingsRepository;
        this.usuarioRepository = usuarioRepository;
        this.sequenceService = sequenceService;
        this.auditService = auditService;
    }

    /**
     * Plazo máximo de respuesta al consumidor (D.S. 011-2011-PCM, Art. 5, modificado
     * por el D.S. 006-2014-PCM). Se calcula sobre días calendario.
     */
    private static final int PLAZO_RESPUESTA_DIAS = 30;

    @Transactional
    public ComplaintReceiptResponse createAndIssueReceipt(CreateComplaintRequest request) {
        return toReceipt(createEntry(request));
    }

    private ComplaintBookEntry createEntry(CreateComplaintRequest request) {
        CompanySettings company = companySettingsRepository.findById(TenantContext.getOrDefault())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Configuración de empresa", TenantContext.getOrDefault()));
        ComplaintBookEntry entry = new ComplaintBookEntry();
        entry.setEntryNumber(sequenceService.next("RECLAMO", "RC", 8));
        entry.setType(request.type());
        entry.setStatus(ComplaintStatus.PENDIENTE);
        entry.setProviderName(company.getName());
        entry.setProviderRuc(company.getRuc());
        entry.setProviderAddress(company.getAddress());
        entry.setConsumerName(request.consumerName().trim());
        entry.setConsumerDocument(blankToNull(request.consumerDocument()));
        entry.setConsumerEmail(request.consumerEmail().trim().toLowerCase());
        entry.setConsumerPhone(blankToNull(request.consumerPhone()));
        entry.setConsumerAddress(request.consumerAddress().trim());
        entry.setOrderNumber(blankToNull(request.orderNumber()));
        entry.setProductServiceDescription(request.productServiceDescription().trim());
        entry.setAmount(request.amount());
        entry.setDetail(request.detail().trim());
        entry.setConsumerRequest(request.consumerRequest().trim());
        ComplaintBookEntry saved = repository.save(entry);
        auditService.log("RECLAMO_CREADO", "RECLAMO", saved.getId(), null,
                new Object[] { saved.getEntryNumber(), saved.getType() }, AuditResult.SUCCESS);
        return saved;
    }

    public PageResponse<ComplaintResponse> list(Pageable pageable) {
        return PageResponse.of(repository.findAllByOrderByCreatedAtDesc(pageable), this::toResponse);
    }

    public ComplaintResponse getByNumber(String entryNumber) {
        return toResponse(repository.findByEntryNumber(entryNumber.trim().toUpperCase())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Hoja de reclamación", entryNumber)));
    }

    public PublicComplaintResponse getPublicByNumber(String entryNumber) {
        ComplaintBookEntry entry = repository.findByEntryNumber(entryNumber.trim().toUpperCase())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Hoja de reclamación", entryNumber));
        return new PublicComplaintResponse(entry.getEntryNumber(), entry.getType(), entry.getStatus(),
                entry.getProviderName(), entry.getResponse(), entry.getCreatedAt(), entry.getRespondedAt());
    }

    @Transactional
    public ComplaintResponse respond(Long id, RespondComplaintRequest request, Long userId) {
        ComplaintBookEntry entry = repository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Hoja de reclamación", id));
        if (entry.getStatus() == ComplaintStatus.CERRADO) {
            throw new ReglaDeNegocioException("La hoja de reclamación ya está cerrada");
        }
        Usuario user = usuarioRepository.findById(userId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Usuario", userId));
        entry.setResponse(request.response().trim());
        entry.setRespondedAt(LocalDateTime.now());
        entry.setRespondedBy(user);
        entry.setStatus(request.close() ? ComplaintStatus.CERRADO : ComplaintStatus.RESPONDIDO);
        auditService.log("RECLAMO_RESPONDIDO", "RECLAMO", entry.getId(), null, entry.getStatus(), AuditResult.SUCCESS);
        return toResponse(entry);
    }

    private ComplaintResponse toResponse(ComplaintBookEntry entry) {
        return new ComplaintResponse(entry.getId(), entry.getEntryNumber(), entry.getType(), entry.getStatus(),
                entry.getProviderName(), entry.getProviderRuc(), entry.getProviderAddress(), entry.getConsumerName(),
                entry.getConsumerDocument(), entry.getConsumerEmail(), entry.getConsumerPhone(),
                entry.getConsumerAddress(), entry.getOrderNumber(),
                entry.getSaleNumber(), entry.getProductServiceDescription(), entry.getAmount(), entry.getDetail(),
                entry.getConsumerRequest(), entry.getResponse(), entry.getCreatedAt(), entry.getRespondedAt());
    }

    private ComplaintReceiptResponse toReceipt(ComplaintBookEntry entry) {
        return new ComplaintReceiptResponse(entry.getEntryNumber(), entry.getType(), entry.getStatus(),
                entry.getProviderName(), entry.getProviderRuc(), entry.getProviderAddress(), entry.getConsumerName(),
                entry.getConsumerDocument(), entry.getConsumerEmail(), entry.getConsumerPhone(),
                entry.getConsumerAddress(), entry.getOrderNumber(), entry.getProductServiceDescription(),
                entry.getAmount(), entry.getDetail(), entry.getConsumerRequest(), entry.getCreatedAt(),
                entry.getCreatedAt().toLocalDate().plusDays(PLAZO_RESPUESTA_DIAS));
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

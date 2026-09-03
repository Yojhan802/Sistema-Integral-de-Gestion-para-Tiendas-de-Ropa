package com.freestyleperu.aplicacion.catalogo.service;

import com.freestyleperu.aplicacion.catalogo.domain.Attribute;
import com.freestyleperu.aplicacion.catalogo.domain.AttributeValue;
import com.freestyleperu.aplicacion.catalogo.dto.request.AttributeRequest;
import com.freestyleperu.aplicacion.catalogo.dto.request.AttributeValueRequest;
import com.freestyleperu.aplicacion.catalogo.dto.response.AttributeResponse;
import com.freestyleperu.aplicacion.catalogo.dto.response.AttributeValueResponse;
import com.freestyleperu.aplicacion.catalogo.repository.AttributeRepository;
import com.freestyleperu.aplicacion.catalogo.repository.AttributeValueRepository;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.RecursoDuplicadoException;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.tienda.service.StoreCatalogSyncService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de atributos configurables por tenant ("Color", "Talla", "Voltaje"...) — reemplaza
 * {@code ColorService}/{@code SizeService} para negocios que no son de ropa. Esas dos clases y
 * las tablas {@code colors}/{@code sizes} siguen vivas en paralelo (ver plan aprobado) hasta que
 * un tenant no-ropa quede probado de punta a punta.
 */
@Service
@Transactional(readOnly = true)
public class AtributoService {

    private final AttributeRepository attributeRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final StoreCatalogSyncService storeCatalogSyncService;

    public AtributoService(AttributeRepository attributeRepository, AttributeValueRepository attributeValueRepository,
            StoreCatalogSyncService storeCatalogSyncService) {
        this.attributeRepository = attributeRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.storeCatalogSyncService = storeCatalogSyncService;
    }

    public List<AttributeResponse> listar() {
        return attributeRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    public AttributeResponse obtener(Long id) {
        return toResponse(buscarOFallar(id));
    }

    @Transactional
    public AttributeResponse crear(AttributeRequest request) {
        if (attributeRepository.existsByNameIgnoreCase(request.name())) {
            throw new RecursoDuplicadoException("Ya existe un atributo llamado " + request.name());
        }
        Attribute attribute = new Attribute();
        attribute.setName(request.name());
        attribute.setInputType(request.inputType());
        AttributeResponse response = toResponse(attributeRepository.save(attribute));
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    public AttributeResponse actualizar(Long id, AttributeRequest request) {
        Attribute attribute = buscarOFallar(id);
        if (!attribute.getName().equalsIgnoreCase(request.name()) && attributeRepository.existsByNameIgnoreCase(request.name())) {
            throw new RecursoDuplicadoException("Ya existe un atributo llamado " + request.name());
        }
        attribute.setName(request.name());
        attribute.setInputType(request.inputType());
        AttributeResponse response = toResponse(attribute);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    public AttributeResponse cambiarEstado(Long id, EstadoGeneral status) {
        Attribute attribute = buscarOFallar(id);
        attribute.setStatus(status);
        AttributeResponse response = toResponse(attribute);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    public AttributeValueResponse crearValor(Long attributeId, AttributeValueRequest request) {
        Attribute attribute = buscarOFallar(attributeId);
        if (attributeValueRepository.existsByAttributeIdAndValueIgnoreCase(attributeId, request.value())) {
            throw new RecursoDuplicadoException("Ya existe el valor " + request.value() + " para " + attribute.getName());
        }
        AttributeValue value = new AttributeValue();
        value.setAttribute(attribute);
        value.setValue(request.value());
        value.setHexCode(request.hexCode());
        value.setSortOrder(request.sortOrder());
        AttributeValueResponse response = toValueResponse(attributeValueRepository.save(value));
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    public AttributeValueResponse actualizarValor(Long valueId, AttributeValueRequest request) {
        AttributeValue value = buscarValorOFallar(valueId);
        boolean mismoNombre = value.getValue().equalsIgnoreCase(request.value());
        if (!mismoNombre && attributeValueRepository.existsByAttributeIdAndValueIgnoreCase(value.getAttribute().getId(), request.value())) {
            throw new RecursoDuplicadoException("Ya existe el valor " + request.value() + " para " + value.getAttribute().getName());
        }
        value.setValue(request.value());
        value.setHexCode(request.hexCode());
        value.setSortOrder(request.sortOrder());
        AttributeValueResponse response = toValueResponse(value);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    public AttributeValueResponse cambiarEstadoValor(Long valueId, EstadoGeneral status) {
        AttributeValue value = buscarValorOFallar(valueId);
        value.setStatus(status);
        AttributeValueResponse response = toValueResponse(value);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    private Attribute buscarOFallar(Long id) {
        return attributeRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Atributo", id));
    }

    private AttributeValue buscarValorOFallar(Long id) {
        return attributeValueRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Valor de atributo", id));
    }

    private AttributeResponse toResponse(Attribute attribute) {
        List<AttributeValueResponse> valores = attributeValueRepository
                .findAllByAttributeIdOrderBySortOrderAscValueAsc(attribute.getId())
                .stream().map(this::toValueResponse).toList();
        return new AttributeResponse(attribute.getId(), attribute.getName(), attribute.getInputType(), attribute.getStatus(), valores);
    }

    private AttributeValueResponse toValueResponse(AttributeValue value) {
        return new AttributeValueResponse(value.getId(), value.getAttribute().getId(), value.getValue(),
                value.getHexCode(), value.getSortOrder(), value.getStatus());
    }
}

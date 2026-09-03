package com.freestyleperu.aplicacion.catalogo.service;

import com.freestyleperu.aplicacion.catalogo.domain.Brand;
import com.freestyleperu.aplicacion.catalogo.dto.request.BrandRequest;
import com.freestyleperu.aplicacion.catalogo.dto.response.BrandResponse;
import com.freestyleperu.aplicacion.catalogo.repository.BrandRepository;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.RecursoDuplicadoException;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.tienda.service.StoreCatalogSyncService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brandRepository;
    private final StoreCatalogSyncService storeCatalogSyncService;

    public BrandService(BrandRepository brandRepository, StoreCatalogSyncService storeCatalogSyncService) {
        this.brandRepository = brandRepository;
        this.storeCatalogSyncService = storeCatalogSyncService;
    }

    public List<BrandResponse> listar() {
        return brandRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    public BrandResponse obtener(Long id) {
        return toResponse(buscarOFallar(id));
    }

    @Transactional
    public BrandResponse crear(BrandRequest request) {
        if (brandRepository.existsByNameIgnoreCase(request.name())) {
            throw new RecursoDuplicadoException("Ya existe una marca llamada " + request.name());
        }
        Brand brand = new Brand();
        brand.setName(request.name());
        BrandResponse response = toResponse(brandRepository.save(brand));
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    public BrandResponse actualizar(Long id, BrandRequest request) {
        Brand brand = buscarOFallar(id);
        if (!brand.getName().equalsIgnoreCase(request.name()) && brandRepository.existsByNameIgnoreCase(request.name())) {
            throw new RecursoDuplicadoException("Ya existe una marca llamada " + request.name());
        }
        brand.setName(request.name());
        BrandResponse response = toResponse(brand);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    @Transactional
    public BrandResponse cambiarEstado(Long id, EstadoGeneral status) {
        Brand brand = buscarOFallar(id);
        brand.setStatus(status);
        BrandResponse response = toResponse(brand);
        storeCatalogSyncService.requestRefresh();
        return response;
    }

    private Brand buscarOFallar(Long id) {
        return brandRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Marca", id));
    }

    private BrandResponse toResponse(Brand brand) {
        return new BrandResponse(brand.getId(), brand.getName(), brand.getStatus());
    }
}

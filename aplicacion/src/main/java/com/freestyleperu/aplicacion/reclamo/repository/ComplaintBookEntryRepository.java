package com.freestyleperu.aplicacion.reclamo.repository;

import com.freestyleperu.aplicacion.reclamo.domain.ComplaintBookEntry;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintBookEntryRepository extends JpaRepository<ComplaintBookEntry, Long> {

    @EntityGraph(attributePaths = "respondedBy")
    Page<ComplaintBookEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "respondedBy")
    Optional<ComplaintBookEntry> findByEntryNumber(String entryNumber);
}

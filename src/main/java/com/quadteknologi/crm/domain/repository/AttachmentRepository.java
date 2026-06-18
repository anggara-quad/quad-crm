package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    Optional<Attachment> findByPublicId(UUID publicId);

    List<Attachment> findByLeadIdOrderByCreatedAtDesc(Long leadId);

    List<Attachment> findByOpportunityIdOrderByCreatedAtDesc(Long opportunityId);

    List<Attachment> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<Attachment> findByPersonIdOrderByCreatedAtDesc(Long personId);
}

package com.smartcloudbrain.ripple.repository;

import com.smartcloudbrain.ripple.entity.EvidenceChain;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceChainRepository extends JpaRepository<EvidenceChain, Long> {

  Optional<EvidenceChain> findByDecisionId(String decisionId);

  List<EvidenceChain> findByPatientIdOrderByCreatedAtAsc(Long patientId);

  List<EvidenceChain> findAllByOrderByIdAsc();

  Optional<EvidenceChain> findTopByOrderByIdDesc();
}

package com.smartcloudbrain.ripple.repository;

import com.smartcloudbrain.ripple.entity.MdtConsultation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MdtConsultationRepository extends JpaRepository<MdtConsultation, Long> {

  List<MdtConsultation> findByPatientIdOrderByCreatedAtDesc(Long patientId);
}

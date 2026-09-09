package com.smartcloudbrain.ripple.repository;

import com.smartcloudbrain.ripple.entity.ChronoTrigger;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChronoTriggerRepository extends JpaRepository<ChronoTrigger, Long> {

  List<ChronoTrigger> findByPatientIdOrderByNextTriggerAtAsc(Long patientId);

  List<ChronoTrigger> findByStatusAndNextTriggerAtLessThanEqualOrderByNextTriggerAtAsc(String status, LocalDateTime now);

  List<ChronoTrigger> findByPatientIdAndStatusOrderByNextTriggerAtAsc(Long patientId, String status);
}

package com.smartcloudbrain.ripple.repository;

import com.smartcloudbrain.ripple.entity.ChronoTrigger;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChronoTriggerRepository extends JpaRepository<ChronoTrigger, Long> {

  List<ChronoTrigger> findByPatientIdOrderByNextTriggerAtAsc(Long patientId);

  List<ChronoTrigger> findByStatusAndNextTriggerAtLessThanEqualOrderByNextTriggerAtAsc(String status, LocalDateTime now);

  List<ChronoTrigger> findByPatientIdAndStatusOrderByNextTriggerAtAsc(Long patientId, String status);

  /** 全部出现过守护触达的患者ID（守护队列聚合用）。 */
  @Query("select distinct t.patientId from ChronoTrigger t where t.patientId is not null")
  List<Long> findDistinctPatientIds();

  long countByChronoTypeAndStatusIn(String chronoType, List<String> statuses);

  long countByFeedbackStatus(String feedbackStatus);
}

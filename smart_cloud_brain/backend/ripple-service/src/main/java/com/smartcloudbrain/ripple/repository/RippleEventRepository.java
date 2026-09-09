package com.smartcloudbrain.ripple.repository;

import com.smartcloudbrain.ripple.entity.RippleEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RippleEventRepository extends JpaRepository<RippleEvent, Long> {

  List<RippleEvent> findByPatientIdOrderByCreatedAtDesc(Long patientId);
}

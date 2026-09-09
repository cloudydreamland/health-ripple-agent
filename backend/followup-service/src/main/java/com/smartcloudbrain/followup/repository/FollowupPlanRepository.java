package com.smartcloudbrain.followup.repository;

import com.smartcloudbrain.followup.entity.FollowupPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowupPlanRepository extends JpaRepository<FollowupPlan, Long> {

  List<FollowupPlan> findByPatientId(Long patientId);
}

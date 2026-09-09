package com.smartcloudbrain.followup.repository;

import com.smartcloudbrain.followup.entity.FollowupRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowupRecordRepository extends JpaRepository<FollowupRecord, Long> {

  List<FollowupRecord> findByPlanId(Long planId);
}

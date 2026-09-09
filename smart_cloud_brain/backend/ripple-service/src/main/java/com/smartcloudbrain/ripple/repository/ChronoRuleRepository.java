package com.smartcloudbrain.ripple.repository;

import com.smartcloudbrain.ripple.entity.ChronoRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChronoRuleRepository extends JpaRepository<ChronoRule, Long> {

  /** 查找缺失 Timing Card 循证字段的规则（用于存量数据回填升级）。 */
  List<ChronoRule> findByEvidenceBasisIsNull();
}

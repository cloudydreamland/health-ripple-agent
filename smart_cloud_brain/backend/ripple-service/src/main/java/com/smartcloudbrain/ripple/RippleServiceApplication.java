package com.smartcloudbrain.ripple;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 健康事件涟漪守护领域服务（DuMate 参赛核心创新落地）。
 *
 * 核心能力：
 * 1. 涟漪推演 API：一个健康事件触发多维度连锁影响推演（药物-生活冲突/复查窗口/并发症信号/家属注意/时间学触达）
 * 2. 哈希链证据存储：每个决策生成含反事实决策树的证据链，链式哈希防篡改，可审计可申诉
 * 3. 医疗时间学引擎：窗口期/节律/周期/季节四类时间规则的 nextTriggerAt 计算与到期调度
 * 4. 多智能体 MDT 会诊：五 Agent 多视角聚合
 * 5. 事件驱动涟漪闭环：推演完成发布 ripple.derived 领域事件，通知服务自动触达医生/患者
 */
@SpringBootApplication(scanBasePackages = "com.smartcloudbrain")
@EnableScheduling
public class RippleServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(RippleServiceApplication.class, args);
  }
}

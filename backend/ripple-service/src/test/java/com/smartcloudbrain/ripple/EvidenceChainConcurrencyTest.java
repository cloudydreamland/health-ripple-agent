package com.smartcloudbrain.ripple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartcloudbrain.ripple.repository.EvidenceChainRepository;
import com.smartcloudbrain.ripple.service.EvidenceChainService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 哈希链并发正确性测试（回归护栏）。
 *
 * 修复背景：append 曾是 synchronized + @Transactional——锁在方法返回时释放、
 * 事务在代理层提交更晚，两个并发写入会读到同一 prevHash 导致链分叉、verify 永久失败。
 * 现行为锁内 REQUIRES_NEW 事务（锁释放时事务已提交）。
 * 本测试并发写入后必须：全链可验证、条数精确、无分叉。
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ripple_test;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.rabbitmq.listener.simple.auto-startup=false"
})
@ActiveProfiles("test")
class EvidenceChainConcurrencyTest {

  @Autowired
  private EvidenceChainService evidenceChainService;
  @Autowired
  private EvidenceChainRepository evidenceChainRepository;

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentAppendsMustProduceSingleLinearChain() throws Exception {
    evidenceChainRepository.deleteAll();

    int threads = 4;
    int perThread = 5;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    for (int t = 0; t < threads; t++) {
      final int threadNo = t;
      pool.submit(() -> {
        ready.countDown();
        try {
          start.await();
          for (int i = 0; i < perThread; i++) {
            evidenceChainService.append("RIPPLE_DERIVATION", (long) threadNo,
                Map.of("thread", threadNo, "seq", i), List.of("factor"),
                Map.of("out", threadNo + "-" + i), Map.of("chosenPath", "p"), 0.9,
                "CONCURRENCY_TEST", "health-ripple-agent");
          }
        } catch (Exception e) {
          throw new IllegalStateException(e);
        } finally {
          done.countDown();
        }
      });
    }
    assertTrue(ready.await(5, TimeUnit.SECONDS));
    start.countDown();
    assertTrue(done.await(30, TimeUnit.SECONDS), "并发追加应在时限内完成");
    pool.shutdown();

    // 全部提交完成后再校验：单条线性链、条数精确、verify 通过
    assertEquals(threads * perThread, evidenceChainRepository.count(), "并发写入条数必须精确");
    Map<String, Object> verdict = evidenceChainService.verify();
    assertEquals(Boolean.TRUE, verdict.get("valid"),
        "并发写入后哈希链必须仍然完整（链分叉=失败）: " + verdict);
    assertEquals(threads * perThread, ((Number) verdict.get("count")).intValue());
  }
}

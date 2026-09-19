package com.smartcloudbrain.ripple.controller;

import com.smartcloudbrain.common.result.Result;
import com.smartcloudbrain.ripple.security.PatientOwnershipGuard;
import com.smartcloudbrain.ripple.service.CommunityRadarService;
import com.smartcloudbrain.ripple.service.FamilyShareService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 家属守护圈 + 社区涟漪雷达（第九轮创新）。
 *
 * - POST /api/health-event/share/link — 生成家属只读分享令牌（患者本人/医生，归属校验）
 * - GET  /api/health-event/share/public/{token} — 家属只读视图（免JWT：HMAC令牌即凭证，
 *   经网关 PUBLIC_PATH_PREFIXES 放行；最小披露，不含姓名/诊断/决策记录）
 * - GET  /api/health-event/community-radar — 社区涟漪雷达（仅医生/管理员）：
 *   个体的涟漪汇成社区的潮汐——跨患者聚合近7天守护信号
 */
@RestController
@RequestMapping("/api/health-event")
public class ShareController {

  private final FamilyShareService familyShareService;
  private final CommunityRadarService communityRadarService;
  private final PatientOwnershipGuard ownershipGuard;

  public ShareController(FamilyShareService familyShareService,
      CommunityRadarService communityRadarService,
      PatientOwnershipGuard ownershipGuard) {
    this.familyShareService = familyShareService;
    this.communityRadarService = communityRadarService;
    this.ownershipGuard = ownershipGuard;
  }

  @PostMapping("/share/link")
  public Result<?> createLink(@RequestParam Long patientId,
      @RequestParam(required = false, defaultValue = "7") int days) {
    ownershipGuard.checkAccess(patientId);
    return Result.success(familyShareService.createLink(patientId, days));
  }

  /** 家属只读视图：公开路径（网关前缀放行），令牌即凭证。 */
  @GetMapping("/share/public/{token}")
  public Result<?> publicView(@PathVariable String token) {
    Long patientId = familyShareService.resolveToken(token);
    return Result.success(familyShareService.familyView(patientId));
  }

  /** 社区涟漪雷达：跨患者守护信号聚合（仅医生/管理员，患者403由服务内角色校验）。 */
  @GetMapping("/community-radar")
  public Result<?> communityRadar() {
    return Result.success(communityRadarService.radar());
  }
}

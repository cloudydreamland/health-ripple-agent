package com.smartcloudbrain.ripple.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcloudbrain.ripple.entity.EvidenceChain;
import com.smartcloudbrain.ripple.repository.EvidenceChainRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 哈希链证据服务（核心创新：tamper-evident evidence chain）。
 *
 * 每条证据 hash = SHA-256(prevHash + "|" + decisionId + "|" + contentFingerprint)，
 * 首条 prevHash = "GENESIS"。任何记录被篡改后，verify() 从该条起全部校验失败——
 * 与区块链防篡改原理一致，为医疗合规提供"可审计、可申诉、可复盘"的硬保证。
 */
@Service
public class EvidenceChainService {

  public static final String GENESIS = "GENESIS";
  private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final EvidenceChainRepository repository;
  private final ObjectMapper objectMapper;

  public EvidenceChainService(EvidenceChainRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  /**
   * 追加一条哈希链证据（含反事实决策树）。
   *
   * @param decisionType      决策类型（RIPPLE_DERIVATION / MDT_CONSULTATION / PRESCRIPTION_BLOCK ...）
   * @param patientId         患者ID（可空）
   * @param inputs            触发输入
   * @param consideredFactors 考虑因素
   * @param decision          决策输出摘要
   * @param counterfactualTree 反事实决策树 {chosenPath, alternativePaths:[...]}
   * @param confidence        置信度
   * @param actionTaken       智能体动作
   * @param agentId           决策Agent标识
   */
  @Transactional
  public synchronized Map<String, Object> append(
      String decisionType,
      Long patientId,
      Map<String, Object> inputs,
      List<String> consideredFactors,
      Map<String, Object> decision,
      Map<String, Object> counterfactualTree,
      double confidence,
      String actionTaken,
      String agentId
  ) {
    try {
      LocalDateTime now = LocalDateTime.now();
      String decisionId = decisionType + "-" + now.format(ID_FORMAT) + "-"
          + sha256Hex(toJson(inputs)).substring(0, 6);

      EvidenceChain last = repository.findTopByOrderByIdDesc().orElse(null);
      String prevHash = last == null ? GENESIS : last.getHash();

      String chosenPath = counterfactualTree == null ? ""
          : String.valueOf(counterfactualTree.getOrDefault("chosenPath", ""));
      List<Map<String, Object>> alternativePaths = counterfactualTree == null ? List.of()
          : asMapList(counterfactualTree.get("alternativePaths"));

      String contentFingerprint = sha256Hex(
          toJson(inputs) + "|" + toJson(decision) + "|" + toJson(consideredFactors) + "|"
              + chosenPath + "|" + toJson(alternativePaths) + "|" + canonicalConfidence(confidence)
              + "|" + actionTaken + "|" + agentId);
      String hash = sha256Hex(prevHash + "|" + decisionId + "|" + contentFingerprint);

      EvidenceChain evidence = new EvidenceChain();
      evidence.setDecisionId(decisionId);
      evidence.setDecisionType(decisionType);
      evidence.setPatientId(patientId);
      evidence.setChosenPath(chosenPath);
      evidence.setAlternativePathsJson(toJson(alternativePaths));
      evidence.setInputsJson(toJson(inputs));
      evidence.setConsideredFactorsJson(toJson(consideredFactors));
      evidence.setDecisionJson(toJson(decision));
      evidence.setConfidence(java.math.BigDecimal.valueOf(confidence));
      evidence.setActionTaken(actionTaken);
      evidence.setAgentId(agentId);
      evidence.setPrevHash(prevHash);
      evidence.setHash(hash);
      EvidenceChain saved = repository.save(evidence);

      return view(saved, alternativePaths);
    } catch (Exception e) {
      throw new IllegalStateException("证据链写入失败: " + e.getMessage(), e);
    }
  }

  public Map<String, Object> findByDecisionId(String decisionId) {
    EvidenceChain evidence = repository.findByDecisionId(decisionId).orElse(null);
    if (evidence == null) {
      return null;
    }
    return view(evidence, asMapList(fromJson(evidence.getAlternativePathsJson())));
  }

  /**
   * 导出为 HL7 FHIR R4 Provenance 兼容 JSON（对齐 AI Transparency on FHIR IG, 2026 ballot）。
   *
   * 使防篡改证据链从"自造概念"升级为"国际标准对齐"：医院信息系统可按 FHIR
   * Provenance 标准消费我们的 AI 决策审计记录（who/when/what/依据/签名），
   * AI 参与者以 agent 表达，哈希链以 signature 表达。
   */
  public Map<String, Object> exportFhirProvenance(String decisionId) {
    EvidenceChain evidence = repository.findByDecisionId(decisionId)
        .orElseThrow(() -> new IllegalArgumentException("证据不存在: " + decisionId));
    String recorded = evidence.getCreatedAt() == null ? ""
        : java.time.OffsetDateTime.of(evidence.getCreatedAt(), java.time.ZoneOffset.ofHours(8)).toString();

    Map<String, Object> provenance = new LinkedHashMap<>();
    provenance.put("resourceType", "Provenance");
    provenance.put("id", evidence.getDecisionId());
    provenance.put("recorded", recorded);
    provenance.put("activity", Map.of("coding", List.of(Map.of(
        "system", "http://terminology.hl7.org/CodeSystem/v3-DataOperation",
        "code", "CREATE",
        "display", evidence.getDecisionType()))));

    // AI 参与者以 agent 表达（AI Transparency on FHIR：AI 作为 provenance participant）
    provenance.put("agent", List.of(Map.of(
        "type", Map.of("coding", List.of(Map.of(
            "system", "http://terminology.hl7.org/CodeSystem/provenance-participant-type",
            "code", "assembler",
            "display", "AI Agent (autonomous decision)"))),
        "who", Map.of("display", evidence.getAgentId()),
        "extension", List.of(
            Map.of("url", "https://smartcloudbrain.org/fhir/StructureDefinition/ai-confidence",
                "valueDecimal", evidence.getConfidence() == null ? 0.0 : evidence.getConfidence()),
            Map.of("url", "https://smartcloudbrain.org/fhir/StructureDefinition/ai-chosen-path",
                "valueString", evidence.getChosenPath())))));

    // 决策依据以 entity 表达（输入/考虑因素/反事实替代路径均可追溯）
    provenance.put("entity", List.of(Map.of(
        "role", "source",
        "what", Map.of("display", "decision-inputs-and-counterfactuals"),
        "detail", List.of(
            Map.of("type", "inputs", "valueString", evidence.getInputsJson()),
            Map.of("type", "considered-factors", "valueString", evidence.getConsideredFactorsJson()),
            Map.of("type", "alternative-paths", "valueString", evidence.getAlternativePathsJson())))));

    // 哈希链防篡改签名以 signature 表达（prevHash 链式关联，篡改即刻暴露）
    provenance.put("signature", List.of(Map.of(
        "type", List.of(Map.of(
            "system", "urn:ietf:rfc:3986",
            "code", "urn:iso:std:iso:18435:sha-256",
            "display", "SHA-256 hash chain (tamper-evident)")),
        "when", recorded,
        "who", Map.of("display", evidence.getAgentId()),
        "data", evidence.getHash(),
        "extension", List.of(Map.of(
            "url", "https://smartcloudbrain.org/fhir/StructureDefinition/prev-hash",
            "valueString", String.valueOf(evidence.getPrevHash()))))));

    provenance.put("fhirCompatibility",
        "HL7 FHIR R4 Provenance + AI Transparency on FHIR IG (2026 ballot)");
    return provenance;
  }

  public List<Map<String, Object>> findByPatient(Long patientId) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (EvidenceChain evidence : repository.findByPatientIdOrderByCreatedAtAsc(patientId)) {
      result.add(view(evidence, asMapList(fromJson(evidence.getAlternativePathsJson()))));
    }
    return result;
  }

  /**
   * 哈希链完整性校验：逐条重算 hash 并比对 prevHash 链。
   * 返回 {valid, count, brokenAt}，brokenAt 非空即从该条被篡改。
   */
  @Transactional
  public Map<String, Object> verify() {
    List<EvidenceChain> all = repository.findAllByOrderByIdAsc();
    String prevHash = GENESIS;
    for (EvidenceChain evidence : all) {
      String contentFingerprint = sha256Hex(
          evidence.getInputsJson() + "|" + evidence.getDecisionJson() + "|"
              + evidence.getConsideredFactorsJson() + "|" + evidence.getChosenPath() + "|"
              + evidence.getAlternativePathsJson() + "|"
              + canonicalConfidence(evidence.getConfidence() == null ? 0.0 : evidence.getConfidence().doubleValue())
              + "|" + evidence.getActionTaken() + "|" + evidence.getAgentId());
      String expected = sha256Hex(prevHash + "|" + evidence.getDecisionId() + "|" + contentFingerprint);
      if (!expected.equals(evidence.getHash())) {
        return Map.of(
            "valid", false,
            "count", all.size(),
            "brokenAt", evidence.getDecisionId(),
            "message", "证据链在第 " + evidence.getDecisionId() + " 条处校验失败，可能被篡改"
        );
      }
      prevHash = evidence.getHash();
    }
    return Map.of(
        "valid", true,
        "count", all.size(),
        "message", "证据链完整，共 " + all.size() + " 条决策记录，链式哈希校验全部通过"
    );
  }

  /* ================= 内部工具 ================= */

  private Map<String, Object> view(EvidenceChain e, List<Map<String, Object>> alternativePaths) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("decisionId", e.getDecisionId());
    view.put("decisionType", e.getDecisionType());
    view.put("patientId", e.getPatientId());
    view.put("timestamp", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
    view.put("inputs", fromJson(e.getInputsJson()));
    view.put("consideredFactors", fromJson(e.getConsideredFactorsJson()));
    view.put("decision", fromJson(e.getDecisionJson()));
    view.put("confidence", e.getConfidence());
    view.put("actionTaken", e.getActionTaken());
    view.put("agentId", e.getAgentId());
    view.put("chosenPath", e.getChosenPath());
    view.put("alternativePaths", alternativePaths);
    view.put("prevHash", e.getPrevHash());
    view.put("hash", e.getHash());
    return view;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception e) {
      return "{}";
    }
  }

  private Object fromJson(String json) {
    try {
      if (json == null || json.isBlank()) {
        return Map.of();
      }
      return objectMapper.readValue(json, Object.class);
    } catch (Exception e) {
      return Map.of();
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> asMapList(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        result.add((Map<String, Object>) map);
      }
    }
    return result;
  }

  static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 置信度规范化为 4 位小数字符串，保证 append 与 verify 的指纹一致。 */
  static String canonicalConfidence(double value) {
    return String.format(java.util.Locale.ROOT, "%.4f", value);
  }
}

package com.smartcloudbrain.ai.triage;

import java.util.List;

/**
 * 否定感知的症状匹配器（分诊规则引擎/安全网共用）。
 *
 * 医疗分诊最经典的误判是否定语境："无胸痛""否认发热"不能作为阳性症状命中。
 * 规则：症状词前紧邻否定线索（无/否认/未见/不伴/没有/排除/denies/no）即视为否定，
 * 该次出现不计数；仅当症状存在至少一次非否定出现时才认为"提及"。
 */
public final class SymptomMatcher {

  private static final List<String> NEGATION_CUES =
      List.of("否认", "排除", "未见", "不伴", "没有", "无", "denies", "denied", "no ");

  private SymptomMatcher() {
  }

  /** 症状文本中是否存在任一关键词的"非否定"出现。 */
  public static boolean mentions(String text, List<String> keywords) {
    String normalized = normalize(text);
    for (String keyword : keywords) {
      if (hasAffirmativeOccurrence(normalized, normalize(keyword))) {
        return true;
      }
    }
    return false;
  }

  /** 关键词的全部出现是否都被否定（用于把"无胸痛"从阳性证据中剔除）。 */
  private static boolean hasAffirmativeOccurrence(String text, String keyword) {
    int index = text.indexOf(keyword);
    while (index >= 0) {
      if (!isNegated(text, index)) {
        return true;
      }
      index = text.indexOf(keyword, index + keyword.length());
    }
    return false;
  }

  /** 关键词出现点之前紧邻（≤8字符窗口内，跳过空格后以否定线索结尾）即判定否定（"否认胸痛"/"denies chest pain"）。 */
  private static boolean isNegated(String text, int keywordIndex) {
    int from = Math.max(0, keywordIndex - 9);
    String prefix = text.substring(from, keywordIndex).stripTrailing();
    for (String cue : NEGATION_CUES) {
      if (prefix.endsWith(cue)) {
        return true;
      }
    }
    return false;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase().replace("，", ",").replace("。", ".");
  }
}

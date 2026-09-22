/** 涟漪推演响应契约（与 ripple-service 在线模式 / Skill 降级模式一致）。 */
export interface ScoreBreakdown {
  severity: number;
  urgency: number;
  actionability: number;
  decay: number;
}

export interface RippleNode {
  drug?: string;
  conflict?: string;
  risk?: string;
  severity?: string;
  advice?: string;
  counterfactualNote?: string;
  diagnosis?: string;
  signal?: string;
  complication?: string;
  action?: string;
  urgency?: string;
  item?: string;
  timing?: string;
  chronoType?: string;
  attention?: string;
  event?: string;
  triggerTime?: string;
  ring: number;
  ringName: string;
  intensity: number;
  scoreBreakdown: ScoreBreakdown;
  [key: string]: unknown;
}

export interface RippleDimensions {
  drugLifestyleConflicts: RippleNode[];
  recheckWindows: RippleNode[];
  complicationSignals: RippleNode[];
  familyAttentions: RippleNode[];
  chronoTriggers: RippleNode[];
}

export interface TopRisk {
  dimension: string;
  label: string;
  ring: number;
  ringName: string;
  intensity: number;
}

export interface RippleIntensity {
  index: number;
  level: "RED" | "ORANGE" | "YELLOW" | string;
  levelLabel: string;
  radius: number;
  topRisks: TopRisk[];
  model: string;
}

export interface CounterfactualPath {
  path: string;
  wasRejected: boolean;
  rejectionReason: string;
  counterfactualOutcome: string;
  riskIfChosen: string;
  evidence: string;
  guardrailVerdict?: "FLAGGED" | "SAFE" | string;
  guardrailNote?: string;
}

export interface GuardrailSummary {
  policy: string;
  auditedPaths: number;
  flaggedPaths: number;
  reference: string;
}

export interface CounterfactualTree {
  chosenPath: string;
  alternativePaths: CounterfactualPath[];
  counterfactualCount: number;
  guardrailSummary?: GuardrailSummary;
}

export interface TimingCard {
  evidenceBasis: string;
  missCost: string;
  evidenceLevel: string;
}

export interface ChronoTriggerView {
  triggerId: number;
  patientId: number;
  chronoType: string;
  event: string;
  triggerTime: string;
  nextTriggerAt: string;
  status: string;
  action: string;
  timingCard: TimingCard;
  /** 医生审定（人机共驾终审）：APPROVED/ADJUSTED/VETOED，空=待审定 */
  reviewStatus?: string;
  reviewNote?: string;
  reviewer?: string;
}

export interface EvidenceView {
  decisionId: string;
  decisionType: string;
  patientId: number | null;
  timestamp: string;
  inputs: Record<string, unknown>;
  consideredFactors: string[];
  confidence: number;
  actionTaken: string;
  agentId: string;
  chosenPath: string;
  alternativePaths: CounterfactualPath[];
  prevHash: string;
  hash: string;
  [key: string]: unknown;
}

export interface RippleResponse {
  healthEvent: { diagnosis: string; drugs: string[]; pastHistory: string };
  dimensions: RippleDimensions;
  summary: { totalNodes: number; highRiskCount: number; rippleIntensity?: RippleIntensity };
  rippleIntensity?: RippleIntensity;
  rippleEventId?: number;
  counterfactualTree: CounterfactualTree;
  chronoTriggers: ChronoTriggerView[];
  proactiveAssessment: {
    isProactive: boolean;
    proactiveAction: string;
    reason: string;
    recommendMdt: boolean;
  };
  evidenceChain: EvidenceView;
  degraded?: boolean;
  degradedReason?: string;
}

export interface MdtResponse {
  consultation: Record<string, Record<string, unknown>>;
  consensusNotes: string[];
  evidenceChain: EvidenceView;
  safetyBoundary?: Record<string, unknown>;
  degraded?: boolean;
}

export interface ChainVerifyResult {
  valid: boolean | null; // null = 离线快照模式（无法实时校验，不冒充结论）
  count: number;
  brokenAt?: string;
  message?: string;
  offline?: boolean;
}

export const DIMENSION_META: Record<string, { label: string; color: string; en: string; icon: string }> = {
  drugLifestyleConflicts: { label: "药物-生活冲突", color: "var(--red)", en: "CONFLICT", icon: " Rx" },
  complicationSignals: { label: "并发症早期信号", color: "var(--orange)", en: "SIGNAL", icon: " Sig" },
  recheckWindows: { label: "复查窗口", color: "var(--cyan)", en: "RECHECK", icon: " Lab" },
  chronoTriggers: { label: "时间学触达", color: "var(--violet)", en: "CHRONO", icon: " Chr" },
  familyAttentions: { label: "家属注意事项", color: "var(--green)", en: "FAMILY", icon: " Fam" },
};

/** 五Agent 学科配色（与弦图/底部角色卡共用）——黛蓝/朱砂/青瓷/竹青/紫藤（夜航版）。 */
export const AGENT_META: Record<string, { label: string; en: string; char: string; color: string }> = {
  triageView: { label: "分诊 Agent", en: "TRIAGE", char: "分", color: "var(--blue)" },
  prescriptionView: { label: "处方 Agent", en: "RX SAFETY", char: "方", color: "var(--red)" },
  recordView: { label: "病历 Agent", en: "RECORD", char: "历", color: "var(--cyan)" },
  followupView: { label: "随访 Agent", en: "FOLLOW-UP", char: "随", color: "var(--green)" },
  rippleView: { label: "涟漪守护 Agent", en: "RIPPLE", char: "守", color: "var(--violet)" },
};

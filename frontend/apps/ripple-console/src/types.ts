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
  valid: boolean;
  count: number;
  brokenAt?: string;
  message?: string;
}

export const DIMENSION_META: Record<string, { label: string; color: string; en: string; icon: string }> = {
  drugLifestyleConflicts: { label: "药物-生活冲突", color: "#bd4033", en: "CONFLICT", icon: " Rx" },
  complicationSignals: { label: "并发症早期信号", color: "#c07a1d", en: "SIGNAL", icon: " Sig" },
  recheckWindows: { label: "复查窗口", color: "#37808a", en: "RECHECK", icon: " Lab" },
  chronoTriggers: { label: "时间学触达", color: "#7a63a8", en: "CHRONO", icon: " Chr" },
  familyAttentions: { label: "家属注意事项", color: "#41795f", en: "FAMILY", icon: " Fam" },
};

/** 五Agent 学科配色（与弦图/底部角色卡共用）——黛青/朱砂/竹青/青瓷/紫藤。 */
export const AGENT_META: Record<string, { label: string; en: string; char: string; color: string }> = {
  triageView: { label: "分诊 Agent", en: "TRIAGE", char: "分", color: "#33628f" },
  prescriptionView: { label: "处方 Agent", en: "RX SAFETY", char: "方", color: "#bd4033" },
  recordView: { label: "病历 Agent", en: "RECORD", char: "历", color: "#37808a" },
  followupView: { label: "随访 Agent", en: "FOLLOW-UP", char: "随", color: "#41795f" },
  rippleView: { label: "涟漪守护 Agent", en: "RIPPLE", char: "守", color: "#7a63a8" },
};

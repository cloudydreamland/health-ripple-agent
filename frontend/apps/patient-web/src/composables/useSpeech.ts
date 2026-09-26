import { onBeforeUnmount, onMounted, ref } from "vue";

/**
 * 语音输入（适老无障碍，第九轮）：把"说"变成"字"。
 * - Chrome/Edge 走 Web Speech API（zh-CN），Safari/Firefox 或无网环境自动隐藏按钮（优雅降级）；
 * - 识别结果通过回调交给调用方拼接进输入框——本地回显，不经任何第三方存储；
 * - listening 状态驱动按钮的呼吸动效，老人能看清"正在听"。
 */
export function useSpeechRecognition() {
  const supported = ref(false);
  const listening = ref(false);
  const error = ref("");
  let recognition: any = null;

  onMounted(() => {
    const SR = (window as any).SpeechRecognition ?? (window as any).webkitSpeechRecognition;
    supported.value = typeof SR === "function";
    if (supported.value) {
      recognition = new SR();
      recognition.lang = "zh-CN";
      recognition.interimResults = false;
      recognition.maxAlternatives = 1;
      recognition.continuous = false;
      recognition.onend = () => { listening.value = false; };
      recognition.onerror = (event: { error?: string }) => {
        listening.value = false;
        error.value = event.error === "not-allowed" || event.error === "service-not-allowed"
          ? "麦克风权限未开启，请在浏览器中允许使用后重试。"
          : event.error === "no-speech"
            ? "没有听清，请靠近麦克风再试一次。"
            : "语音输入暂时不可用，可以直接输入文字。";
      };
    }
  });
  onBeforeUnmount(() => {
    try { recognition?.stop(); } catch { /* 忽略 */ }
  });

  /** 开始听一句话；结果（最接近的候选）通过 onText 返回。 */
  function start(onText: (text: string) => void) {
    if (!recognition || listening.value) {
      return;
    }
    error.value = "";
    recognition.onresult = (event: any) => {
      const text = String(event.results?.[0]?.[0]?.transcript ?? "").trim();
      if (text) {
        error.value = "";
        onText(text);
      }
    };
    try {
      recognition.start();
      listening.value = true;
    } catch {
      listening.value = false;
      error.value = "语音输入暂时不可用，可以直接输入文字。";
    }
  }

  function stop() {
    try { recognition?.stop(); } catch { /* 忽略 */ }
    listening.value = false;
  }

  return { supported, listening, error, start, stop };
}

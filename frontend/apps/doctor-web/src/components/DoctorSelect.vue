<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";

type Option = { value: string; label: string };
const props = defineProps<{ modelValue: string; options: Option[]; controlLabel: string }>();
const emit = defineEmits<{ "update:modelValue": [value: string] }>();
const trigger = ref<HTMLButtonElement | null>(null);
const menu = ref<HTMLElement | null>(null);
const open = ref(false);
const active = ref(0);
const position = ref<Record<string, string>>({});
const selected = computed(() => props.options.find((item) => item.value === props.modelValue));
const id = `doctor-select-${Math.random().toString(36).slice(2)}`;

function place() {
  if (!trigger.value) return;
  const rect = trigger.value.getBoundingClientRect();
  const height = Math.min(props.options.length * 38 + 8, 250);
  const below = window.innerHeight - rect.bottom >= height + 12 || rect.top < height + 12;
  position.value = {
    left: `${Math.max(8, Math.min(rect.left, window.innerWidth - Math.max(rect.width, 170) - 8))}px`,
    top: `${below ? rect.bottom + 5 : rect.top - height - 5}px`,
    minWidth: `${Math.max(rect.width, 170)}px`,
  };
}
function close() { open.value = false; }
function toggle() {
  open.value = !open.value;
  if (open.value) {
    active.value = Math.max(0, props.options.findIndex((item) => item.value === props.modelValue));
    nextTick(place);
  }
}
function choose(option: Option) {
  emit("update:modelValue", option.value);
  close();
  nextTick(() => trigger.value?.focus());
}
function onKeydown(event: KeyboardEvent) {
  if (event.key === "Escape" && open.value) { event.preventDefault(); close(); trigger.value?.focus(); return; }
  if (event.key === "ArrowDown" || event.key === "ArrowUp") {
    event.preventDefault();
    if (!open.value) { open.value = true; active.value = Math.max(0, props.options.findIndex((item) => item.value === props.modelValue)); nextTick(place); }
    else active.value = (active.value + (event.key === "ArrowDown" ? 1 : -1) + props.options.length) % props.options.length;
  } else if ((event.key === "Enter" || event.key === " ") && open.value) {
    event.preventDefault(); choose(props.options[active.value]);
  }
}
function outside(event: PointerEvent) {
  const target = event.target as Node;
  if (!trigger.value?.contains(target) && !menu.value?.contains(target)) close();
}
function onScroll() { if (open.value) place(); }
watch(open, (value) => {
  if (value) { document.addEventListener("pointerdown", outside); window.addEventListener("resize", onScroll); window.addEventListener("scroll", onScroll, true); }
  else { document.removeEventListener("pointerdown", outside); window.removeEventListener("resize", onScroll); window.removeEventListener("scroll", onScroll, true); }
});
onBeforeUnmount(() => { document.removeEventListener("pointerdown", outside); window.removeEventListener("resize", onScroll); window.removeEventListener("scroll", onScroll, true); });
</script>

<template>
  <button ref="trigger" type="button" class="doctor-select-trigger" :aria-label="controlLabel" aria-haspopup="listbox" :aria-expanded="open" :aria-controls="id" @click="toggle" @keydown="onKeydown">
    <span>{{ selected?.label || '请选择' }}</span><svg viewBox="0 0 16 16" aria-hidden="true"><path d="m3 6 5 5 5-5" /></svg>
  </button>
  <Teleport to="body">
    <div v-if="open" :id="id" ref="menu" class="doctor-select-menu" role="listbox" :aria-label="controlLabel" :style="position" @keydown="onKeydown">
      <button v-for="(option, index) in options" :key="option.value" type="button" role="option" :aria-selected="modelValue === option.value" :class="{ active: active === index, selected: modelValue === option.value }" @mouseenter="active = index" @click="choose(option)"><span>{{ option.label }}</span><b v-if="modelValue === option.value" aria-hidden="true">✓</b></button>
    </div>
  </Teleport>
</template>

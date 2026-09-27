<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
const props = defineProps<{ modelValue: string; options: string[]; controlLabel: string; placeholder?: string }>();
const emit = defineEmits<{ "update:modelValue": [value: string] }>();
const input = ref<HTMLInputElement | null>(null);
const menu = ref<HTMLElement | null>(null);
const open = ref(false);
const active = ref(0);
const position = ref<Record<string, string>>({});
const matches = computed(() => [...new Set(props.options)].filter((name) => name.toLowerCase().includes(props.modelValue.toLowerCase())).slice(0, 8));
const id = `doctor-combo-${Math.random().toString(36).slice(2)}`;
function place() {
  if (!input.value) return;
  const rect = input.value.getBoundingClientRect();
  const height = Math.min(matches.value.length * 38 + 8, 312);
  const below = window.innerHeight - rect.bottom >= height + 12 || rect.top < height + 12;
  position.value = { left: `${Math.max(8, Math.min(rect.left, window.innerWidth - rect.width - 8))}px`, top: `${below ? rect.bottom + 5 : rect.top - height - 5}px`, width: `${Math.min(rect.width, window.innerWidth - 16)}px` };
}
function onInput(event: Event) { emit("update:modelValue", (event.target as HTMLInputElement).value); active.value = 0; open.value = true; nextTick(place); }
function choose(value: string) { emit("update:modelValue", value); open.value = false; nextTick(() => input.value?.focus()); }
function onKeydown(event: KeyboardEvent) {
  if (event.key === "Escape") { open.value = false; return; }
  if (event.key === "ArrowDown" || event.key === "ArrowUp") { event.preventDefault(); if (!open.value) { open.value = true; nextTick(place); } else if (matches.value.length) active.value = (active.value + (event.key === "ArrowDown" ? 1 : -1) + matches.value.length) % matches.value.length; }
  if (event.key === "Enter" && open.value && matches.value[active.value]) { event.preventDefault(); choose(matches.value[active.value]); }
}
function outside(event: PointerEvent) { const target = event.target as Node; if (!input.value?.contains(target) && !menu.value?.contains(target)) open.value = false; }
function onScroll() { if (open.value) place(); }
watch(open, (value) => { if (value) { document.addEventListener("pointerdown", outside); window.addEventListener("resize", onScroll); window.addEventListener("scroll", onScroll, true); } else { document.removeEventListener("pointerdown", outside); window.removeEventListener("resize", onScroll); window.removeEventListener("scroll", onScroll, true); } });
onBeforeUnmount(() => { document.removeEventListener("pointerdown", outside); window.removeEventListener("resize", onScroll); window.removeEventListener("scroll", onScroll, true); });
</script>
<template>
  <div class="doctor-combobox">
    <input ref="input" :value="modelValue" :placeholder="placeholder" role="combobox" aria-autocomplete="list" aria-haspopup="listbox" :aria-label="controlLabel" :aria-expanded="open && matches.length > 0" :aria-controls="id" @input="onInput" @focus="open = true; nextTick(place)" @keydown="onKeydown" />
    <span aria-hidden="true">⌄</span>
  </div>
  <Teleport to="body"><div v-if="open && matches.length" :id="id" ref="menu" class="doctor-select-menu doctor-combo-menu" role="listbox" :aria-label="controlLabel" :style="position"><button v-for="(name, index) in matches" :key="name" type="button" role="option" :aria-selected="name === modelValue" :class="{ active: active === index }" @mouseenter="active = index" @click="choose(name)">{{ name }}</button></div></Teleport>
</template>

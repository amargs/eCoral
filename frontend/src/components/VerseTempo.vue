<script setup>
import { computed } from 'vue'
import { SEASONS, activeMark, markLabel } from '../songs.js'

const props = defineProps({
  song: { type: Object, required: true },
  fallback: { type: String, default: '' },
  fallbackLabel: { type: String, default: '' },
})

const emit = defineEmits(['change'])

const tempo = computed(() => (props.song.verseSeasons || []).length > 0)
const sunday = computed(() => (props.song.verseSundays || []).length > 0)
const varied = computed(() => tempo.value || sunday.value)
const start = computed(() => props.fallback || (tempo.value ? 'comum' : props.song.verseSundays?.[0] || ''))
const current = computed(() => {
  if (props.fallback) {
    if (tempo.value) return props.song.showTempo || props.fallback
    if (sunday.value) return props.song.showSunday || props.fallback
  }
  return activeMark(props.song)
})
const changed = computed(() => current.value !== start.value)
const label = computed(() => {
  if (props.fallback && current.value === props.fallback && props.fallbackLabel) return props.fallbackLabel
  return markLabel(props.song, current.value)
})

const options = computed(() => {
  if (tempo.value) return SEASONS.map((item) => ({ id: item.id, label: item.label }))
  const seen = []
  for (const id of props.song.verseSundays || []) {
    if (!seen.some((item) => item.id === id)) seen.push({ id, label: markLabel(props.song, id) })
  }
  if (props.fallback && !seen.some((item) => item.id === props.fallback)) {
    seen.unshift({ id: props.fallback, label: props.fallbackLabel || markLabel(props.song, props.fallback) })
  }
  return seen
})

function choose(event) {
  const value = event.target.value
  const cleared = value === start.value ? undefined : value
  if (tempo.value) emit('change', { showTempo: cleared })
  else emit('change', { showSunday: cleared })
}
</script>

<template>
  <select
    v-if="varied"
    class="choice-token"
    :class="{ 'is-changed': changed }"
    :value="current"
    :aria-label="(tempo ? 'Tempo ' : 'Domingo ') + label"
    :title="label"
    @change="choose"
  >
    <option v-for="item in options" :key="item.id" :value="item.id">{{ item.label }}</option>
  </select>
</template>

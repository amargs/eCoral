<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { estrofeCountLabel, shownPlan } from '../songs.js'

const props = defineProps({
  song: { type: Object, required: true },
  compact: { type: Boolean, default: false },
  open: { type: Boolean, default: false },
})

const emit = defineEmits(['change', 'open', 'close'])
const root = ref(null)
const menu = ref(null)
const trigger = ref(null)
const menuStyle = ref({ visibility: 'hidden' })
const narrow = ref(false)
let listening = false
const titleId = computed(() => `estrofes-title-${props.song.id}`)
const panelId = computed(() => `estrofes-panel-${props.song.id}`)

const plan = computed(() => shownPlan(props.song))
const numbers = computed(() => plan.value?.rows?.map((row) => row.n) || [])
const total = computed(() => numbers.value.length)
const chosen = computed(() => {
  const raw = props.song.showEstrofes
  if (Array.isArray(raw)) {
    const picked = raw.filter((n) => numbers.value.includes(n))
    return picked.length ? picked : numbers.value
  }
  if (Number.isInteger(raw) && raw > 0 && raw < total.value) {
    return numbers.value.filter((n) => n <= raw)
  }
  return numbers.value
})
const shortLabel = computed(() => estrofeCountLabel(props.song))
const changed = computed(() => shortLabel.value !== '' && shortLabel.value !== 'Todas as estrofes')
const menuTitle = computed(() => shortLabel.value)
const triggerLabel = computed(() => `Selecionar estrofes. ${menuTitle.value}`)

function preview(text) {
  const line = (text || '').trim()
  if (!line || line.endsWith('...') || line.endsWith('…')) return line
  return `${line}...`
}

function included(n) {
  return chosen.value.includes(n)
}

function locked(n) {
  return included(n) && chosen.value.length === 1
}

function switchName(row) {
  const name = `Estrofe ${row.n}, ${preview(row.line)}`
  return locked(row.n) ? `${name}. A última estrofe fica.` : name
}

function toggle(n, on) {
  if (locked(n)) return false
  const next = numbers.value.filter((item) => (item === n ? on : included(item)))
  if (!next.length) return false
  emit('change', { showEstrofes: next.length === total.value ? undefined : next })
  return true
}

function onSwitch(n, event) {
  if (!toggle(n, event.target.checked)) event.target.checked = true
}

function titleLeft(button) {
  const title = button.closest('li')?.querySelector('.moment-song, .song-name')
  return title ? title.getBoundingClientRect().left : null
}

function place() {
  const button = trigger.value
  const panel = menu.value
  if (!button || !panel) return
  const rect = button.getBoundingClientRect()
  const row = button.closest('li')
  const tight = window.innerWidth <= 520
  narrow.value = tight
  let left
  let width
  let top
  if (tight && row) {
    const rowRect = row.getBoundingClientRect()
    const title = row.querySelector('.moment-song, .song-name')
    const titleBottom = title ? title.getBoundingClientRect().bottom : rect.bottom
    left = rowRect.left
    width = rowRect.width
    top = Math.max(rect.bottom, titleBottom) + 4
  } else {
    const start = titleLeft(button)
    const rowRect = row ? row.getBoundingClientRect() : rect
    left = start == null || start > rowRect.right - 160 ? Math.max(8, rowRect.right - 256) : start
    width = Math.max(160, rowRect.right - left)
    top = rect.bottom + 4
  }
  const box = {
    left: `${left}px`,
    width: `${width}px`,
    minWidth: `${width}px`,
    maxWidth: `${width}px`,
  }
  const finish = (height) => {
    let nextTop = top
    if (nextTop + height > window.innerHeight - 8 && rect.top - height - 4 >= 8) {
      nextTop = rect.top - height - 4
    }
    menuStyle.value = { ...box, top: `${nextTop}px`, visibility: 'visible' }
  }
  if (menuStyle.value.visibility === 'visible') {
    finish(panel.offsetHeight)
    return
  }
  menuStyle.value = { ...box, top: `${top}px`, visibility: 'hidden' }
  nextTick(() => {
    if (!menu.value) return
    finish(menu.value.offsetHeight)
  })
}

function onPointer(event) {
  if (root.value?.contains(event.target)) return
  emit('close')
}

function onKey(event) {
  if (event.key !== 'Escape') return
  emit('close')
  nextTick(() => trigger.value?.focus())
}

function listen(on) {
  if (on === listening) return
  listening = on
  const method = on ? 'addEventListener' : 'removeEventListener'
  document[method]('mousedown', onPointer, true)
  document[method]('keydown', onKey)
  window[method]('resize', place)
  document[method]('scroll', place, true)
}

watch(() => props.open, (open) => {
  if (!open) {
    listen(false)
    return
  }
  menuStyle.value = { visibility: 'hidden' }
  nextTick(() => {
    place()
    listen(true)
  })
})

onBeforeUnmount(() => listen(false))
</script>

<template>
  <div v-if="compact && plan" ref="root" class="estrofe-menu-anchor">
    <button
      ref="trigger"
      type="button"
      class="choice-token"
      :class="{ 'is-changed': changed }"
      :aria-expanded="open"
      aria-haspopup="true"
      :aria-controls="open ? panelId : undefined"
      :aria-label="triggerLabel"
      @click="emit('open')"
    >
      {{ shortLabel }}
    </button>
    <div
      v-if="open"
      :id="panelId"
      ref="menu"
      class="estrofe-menu"
      :class="{ narrow }"
      role="group"
      :aria-labelledby="titleId"
      :style="menuStyle"
    >
      <p :id="titleId" class="estrofe-menu-title">{{ menuTitle }}</p>
      <ul>
        <li v-for="row in plan.rows" :key="row.n">
          <div class="estrofe" :class="{ locked: locked(row.n) }" @click="toggle(row.n, !included(row.n))">
            <input
              class="switch small"
              type="checkbox"
              :checked="included(row.n)"
              :disabled="locked(row.n)"
              :aria-label="switchName(row)"
              @click.stop
              @change="onSwitch(row.n, $event)"
            />
            <span class="estrofe-n" aria-hidden="true">{{ row.n }}</span>
            <span class="estrofe-line">{{ preview(row.line) }}</span>
          </div>
        </li>
        <li class="estrofe-refrain">
          <span>Refrão</span>
          <span class="estrofe-line">{{ preview(plan.refrain) }}</span>
          <span class="estrofe-where">{{ plan.where }}</span>
        </li>
      </ul>
    </div>
  </div>
  <div v-else-if="plan" class="estrofes">
    <p class="estrofe-label">Estrofes</p>
    <ul>
      <li v-for="row in plan.rows" :key="row.n">
        <div class="estrofe" :class="{ locked: locked(row.n) }" @click="toggle(row.n, !included(row.n))">
          <input
            class="switch small"
            type="checkbox"
            :checked="included(row.n)"
            :disabled="locked(row.n)"
            :aria-label="switchName(row)"
            @click.stop
            @change="onSwitch(row.n, $event)"
          />
          <span class="estrofe-n" aria-hidden="true">{{ row.n }}</span>
          <span class="estrofe-line">{{ preview(row.line) }}</span>
        </div>
      </li>
      <li class="estrofe-refrain">
        <span>Refrão</span>
        <span class="estrofe-line">{{ preview(plan.refrain) }}</span>
        <span class="estrofe-where">{{ plan.where }}</span>
      </li>
    </ul>
  </div>
</template>

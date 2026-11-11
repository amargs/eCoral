<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import Estrofes from './Estrofes.vue'
import SongShow from './SongShow.vue'
import { songControlName } from '../songs.js'

const props = defineProps({
  songs: { type: Array, required: true },
})

const emit = defineEmits(['remove', 'up', 'down', 'clear', 'show'])
const estrofesOpen = ref(null)
const list = ref(null)
const stage = ref(null)
const emptyEl = ref(null)
const displaySongs = ref(props.songs)
const holdEmpty = ref(false)
const emptyEntering = ref(false)
const live = new Set()
const rowMoves = new Set()
let flight = 0
let moveToken = 0

const ease = 'cubic-bezier(0.16, 1, 0.3, 1)'

watch(() => props.songs, (songs, previous) => {
  const before = previous?.length ?? 0
  const after = songs.length
  if (before === after) {
    if (holdEmpty.value || emptyEntering.value) return
    const reorder = previous && !sameOrder(previous, songs) && sameMembers(previous, songs)
    const beforeTops = reorder && !prefersReduced() ? rowTops() : null
    displaySongs.value = songs
    if (beforeTops) slideRows(beforeTops)
    return
  }
  cancelRowMoves()
  cancelLive()
  const token = ++flight
  if (before === 0 && after > 0) {
    lockStage()
    displaySongs.value = songs
    emptyEntering.value = false
    holdEmpty.value = true
    settleArrival(token)
    return
  }
  if (before > 0 && after === 0) {
    unlockStage()
    holdEmpty.value = false
    emptyEntering.value = true
    settleDepart(token)
    return
  }
  holdEmpty.value = false
  emptyEntering.value = false
  unlockStage()
  displaySongs.value = songs
  if (after > before) scrollChosen()
}, { flush: 'pre' })

onBeforeUnmount(() => {
  flight += 1
  moveToken += 1
  cancelRowMoves()
  cancelLive()
  unlockStage()
})

function sameOrder(a, b) {
  return a.every((song, index) => song.id === b[index].id)
}

function sameMembers(a, b) {
  if (a.length !== b.length) return false
  const ids = new Set(a.map((song) => song.id))
  return b.every((song) => ids.has(song.id))
}

function rowTops() {
  const tops = new Map()
  for (const row of list.value?.querySelectorAll('.chosen-song') ?? []) {
    tops.set(row.dataset.id, row.getBoundingClientRect().top)
  }
  return tops
}

async function slideRows(beforeTops) {
  const token = ++moveToken
  await nextTick()
  if (token !== moveToken) return
  cancelRowMoves()
  const rows = list.value?.querySelectorAll('.chosen-song') ?? []
  for (const row of rows) {
    row.classList.remove('is-sliding')
    row.style.willChange = ''
  }
  for (const row of rows) {
    const previousTop = beforeTops.get(row.dataset.id)
    if (previousTop == null) continue
    const delta = previousTop - row.getBoundingClientRect().top
    if (Math.abs(delta) < 0.5) continue
    row.classList.add('is-sliding')
    row.style.willChange = 'transform'
    const animation = row.animate(
      [{ transform: `translateY(${delta}px)` }, { transform: 'translateY(0px)' }],
      { duration: 220, easing: ease },
    )
    rowMoves.add(animation)
    animation.finished.finally(() => {
      rowMoves.delete(animation)
      if (token !== moveToken) return
      row.classList.remove('is-sliding')
      row.style.willChange = ''
    }).catch(() => {})
  }
}

function cancelRowMoves() {
  for (const animation of rowMoves) animation.cancel()
  rowMoves.clear()
}

function showEstrofes(id) {
  estrofesOpen.value = estrofesOpen.value === id ? null : id
}

function prefersReduced() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function lockStage() {
  const el = stage.value
  if (!el) return
  el.style.height = `${el.getBoundingClientRect().height}px`
}

function unlockStage() {
  if (stage.value) stage.value.style.height = ''
}

function track(animation) {
  live.add(animation)
  return animation
}

function cancelLive() {
  for (const animation of live) animation.cancel()
  live.clear()
}

async function scrollChosen() {
  await nextTick()
  const el = list.value
  if (!el || el.scrollHeight <= el.clientHeight) return
  el.scrollTop = el.scrollHeight - el.clientHeight
}

async function settleArrival(token) {
  try {
    await nextTick()
    if (token !== flight) return
    await runArrival()
  } catch {
    // A newer change cancelled this flight.
  } finally {
    if (token !== flight) return
    holdEmpty.value = false
    await nextTick()
    if (token !== flight) return
    cancelLive()
    unlockStage()
    clearWillChange()
  }
}

async function settleDepart(token) {
  try {
    await nextTick()
    if (token !== flight) return
    await runDepart()
  } catch {
    // A newer change cancelled this flight.
  } finally {
    if (token !== flight) return
    displaySongs.value = []
    emptyEntering.value = false
    await nextTick()
    if (token !== flight) return
    cancelLive()
    clearWillChange()
  }
}

function clearWillChange() {
  if (list.value) list.value.style.willChange = ''
  const song = list.value?.querySelector('.chosen-song')
  if (song) song.style.willChange = ''
  if (emptyEl.value) emptyEl.value.style.willChange = ''
}

function fadePair(empty, song, direction) {
  const reduced = prefersReduced()
  const duration = reduced ? 160 : 240
  empty.style.willChange = reduced ? 'opacity' : 'opacity, filter'
  song.style.willChange = 'opacity'
  const emptyFrames = reduced
    ? [{ opacity: direction === 'in' ? 0 : 1 }, { opacity: direction === 'in' ? 1 : 0 }]
    : [
        { opacity: direction === 'in' ? 0 : 1, filter: direction === 'in' ? 'blur(2px)' : 'blur(0px)' },
        { opacity: direction === 'in' ? 1 : 0, filter: direction === 'in' ? 'blur(0px)' : 'blur(2px)' },
      ]
  return Promise.all([
    track(empty.animate(emptyFrames, { duration, easing: ease, fill: 'forwards' })).finished,
    track(song.animate(
      [{ opacity: direction === 'in' ? 1 : 0 }, { opacity: direction === 'in' ? 0 : 1 }],
      { duration, easing: ease, fill: 'both' },
    )).finished,
  ])
}

async function runArrival() {
  const empty = emptyEl.value
  const song = list.value?.querySelector('.chosen-song')
  if (!empty || !song) return
  await fadePair(empty, song, 'out')
}

async function runDepart() {
  const empty = emptyEl.value
  const songs = list.value
  if (!empty || !songs) return
  await fadePair(empty, songs, 'in')
}
</script>

<template>
  <section class="panel">
    <div class="panel-head">
      <h2>Cânticos escolhidos</h2>
      <button type="button" class="clear" :disabled="displaySongs.length === 0" @click="emit('clear')">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 6h9M4 12h9M4 18h6M16 15l4 4M20 15l-4 4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
        Limpar
      </button>
    </div>
    <div ref="stage" class="chosen-stage">
    <div
      v-if="displaySongs.length === 0 || holdEmpty || emptyEntering"
      ref="emptyEl"
      class="empty-chosen"
      :class="{ departing: holdEmpty, entering: emptyEntering }"
      :aria-hidden="holdEmpty ? 'true' : undefined"
    >
      <div class="song-scaffold" aria-hidden="true">
        <div v-for="n in 3" :key="n" class="scaffold-row">
          <span class="scaffold-badge"></span>
          <span class="scaffold-title"></span>
          <span class="scaffold-actions"></span>
        </div>
      </div>
      <p>Ainda não há cânticos.</p>
      <div class="song-scaffold" aria-hidden="true">
        <div v-for="n in 3" :key="n" class="scaffold-row">
          <span class="scaffold-badge"></span>
          <span class="scaffold-title"></span>
          <span class="scaffold-actions"></span>
        </div>
      </div>
    </div>
    <ul
      v-if="displaySongs.length"
      ref="list"
      class="songs"
      :class="{ arriving: holdEmpty, 'songs-departing': emptyEntering }"
      :aria-hidden="emptyEntering ? 'true' : undefined"
    >
      <li v-for="(song, index) in displaySongs" :key="song.id" :data-id="song.id" class="chosen-song">
        <div class="chosen-body">
          <span class="song-label">
            <span class="book-badge">{{ song.bookId || '#' }}</span>
            <span class="song-name">
              <span>{{ song.title }}</span>
              <span class="choice-tokens">
                <SongShow :song="song" @change="emit('show', index, $event)" />
                <Estrofes
                  compact
                  :song="song"
                  :open="estrofesOpen === song.id"
                  @open="showEstrofes(song.id)"
                  @close="estrofesOpen = null"
                  @change="emit('show', index, $event)"
                />
              </span>
            </span>
          </span>
        </div>
        <span class="row-actions">
          <button type="button" class="icon" :aria-label="songControlName('Subir', song)" :title="songControlName('Subir', song)" :disabled="index === 0" @click="emit('up', index)">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 19V5M6 11l6-6 6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
          <button type="button" class="icon" :aria-label="songControlName('Descer', song)" :title="songControlName('Descer', song)" :disabled="index === displaySongs.length - 1" @click="emit('down', index)">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 5v14M6 13l6 6 6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
          <button type="button" class="icon row-remove" :aria-label="songControlName('Retirar', song)" :title="songControlName('Retirar', song)" @click="emit('remove', index)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M6 6l12 12M18 6 6 18" />
            </svg>
          </button>
        </span>
      </li>
    </ul>
    </div>
  </section>
</template>

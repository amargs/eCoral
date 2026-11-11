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
const arrivingIds = ref([])
const leavingIds = ref([])
const leavingOffsets = ref({})
const leavingFloat = ref(false)
const live = new Set()
const rowMoves = new Set()
let flight = 0
let moveToken = 0
let scrollToken = 0

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
  releaseArrivals()
  cancelRowMoves()
  leavingIds.value = []
  leavingFloat.value = false
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
  if (after < before) {
    const reduced = prefersReduced()
    const tops = rowTops()
    const offsets = {}
    for (const row of list.value?.querySelectorAll('.chosen-song') ?? []) {
      offsets[row.dataset.id] = {
        top: row.offsetTop,
        left: row.offsetLeft,
        width: row.offsetWidth,
      }
    }
    const nextIds = new Set(songs.map((song) => song.id))
    leavingIds.value = previous.filter((song) => !nextIds.has(song.id)).map((song) => song.id)
    leavingOffsets.value = offsets
    leavingFloat.value = !reduced
    if (leavingIds.value.includes(estrofesOpen.value)) estrofesOpen.value = null
    displaySongs.value = previous
    settleRemove(token, tops)
    return
  }
  holdEmpty.value = false
  emptyEntering.value = false
  unlockStage()
  if (after > before) {
    arrivingIds.value = songs.filter((song) => !previous.some((item) => item.id === song.id)).map((song) => song.id)
    displaySongs.value = songs
    settleAppend(token)
    return
  }
  displaySongs.value = songs
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

function songIndex(song) {
  return props.songs.findIndex((item) => item.id === song.id)
}

function leavingStyle(id) {
  if (!leavingFloat.value || !leavingIds.value.includes(id)) return undefined
  const place = leavingOffsets.value[String(id)]
  if (!place) return undefined
  return {
    top: `${place.top}px`,
    left: `${place.left}px`,
    width: `${place.width}px`,
  }
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

function releaseArrivals() {
  for (const row of list.value?.querySelectorAll('.chosen-song.is-arriving') ?? []) {
    row.classList.remove('is-arriving')
  }
  arrivingIds.value = []
  cancelLive()
}

async function settleAppend(token) {
  try {
    await nextTick()
    if (token !== flight) return
    await fadeArrivals()
  } catch {
    // A newer change cancelled this flight.
  } finally {
    if (token !== flight) return
    arrivingIds.value = []
    await nextTick()
    if (token !== flight) return
    cancelLive()
    clearWillChange()
  }
}

async function settleRemove(token, tops) {
  try {
    await nextTick()
    if (token !== flight) return
    await Promise.all([fadeLeaving(), slideRemaining(tops)])
  } catch {
    // A newer change cancelled this flight.
  } finally {
    if (token !== flight) return
    leavingIds.value = []
    leavingFloat.value = false
    displaySongs.value = props.songs
    await nextTick()
    if (token !== flight) return
    cancelLive()
    clearWillChange()
  }
}

async function fadeLeaving() {
  const rows = [...(list.value?.querySelectorAll('.chosen-song.is-leaving') ?? [])]
  if (!rows.length) return
  const duration = prefersReduced() ? 160 : 240
  await Promise.all(rows.map((row) => {
    row.style.willChange = 'opacity'
    return track(row.animate(
      [{ opacity: 1 }, { opacity: 0 }],
      { duration, easing: ease, fill: 'forwards' },
    )).finished
  }))
}

function slideRemaining(tops) {
  if (prefersReduced() || !list.value) return Promise.resolve()
  const animations = []
  for (const row of list.value.querySelectorAll('.chosen-song:not(.is-leaving)')) {
    const previousTop = tops.get(row.dataset.id)
    if (previousTop == null) continue
    const delta = previousTop - row.getBoundingClientRect().top
    if (Math.abs(delta) < 0.5) continue
    row.classList.add('is-sliding')
    row.style.willChange = 'transform'
    const animation = row.animate(
      [{ transform: `translateY(${delta}px)` }, { transform: 'translateY(0px)' }],
      { duration: 240, easing: ease },
    )
    rowMoves.add(animation)
    animations.push(animation.finished.finally(() => {
      rowMoves.delete(animation)
      row.classList.remove('is-sliding')
      row.style.willChange = ''
    }))
  }
  return Promise.all(animations)
}

async function fadeArrivals() {
  const rows = [...(list.value?.querySelectorAll('.chosen-song.is-arriving') ?? [])]
  if (!rows.length) return
  const reduced = prefersReduced()
  const duration = reduced ? 160 : 240
  const fades = rows.map((row) => {
    row.style.willChange = 'opacity'
    return track(row.animate(
      [{ opacity: 0 }, { opacity: 1 }],
      { duration, easing: ease, fill: 'both' },
    )).finished
  })
  await Promise.all([...fades, easeScroll(duration, reduced)])
}

function easeScroll(duration, reduced) {
  const el = list.value
  if (!el) return Promise.resolve()
  const token = ++scrollToken
  const target = el.scrollHeight - el.clientHeight
  const from = el.scrollTop
  if (target <= from + 1) return Promise.resolve()
  if (reduced) {
    el.scrollTop = target
    return Promise.resolve()
  }
  const curve = fadeEase()
  const start = performance.now()
  return new Promise((resolve) => {
    function frame(now) {
      if (!list.value || token !== scrollToken) {
        resolve()
        return
      }
      const t = Math.min(1, (now - start) / duration)
      el.scrollTop = from + (target - from) * curve(t)
      if (t < 1) requestAnimationFrame(frame)
      else resolve()
    }
    requestAnimationFrame(frame)
  })
}

function fadeEase() {
  const x1 = 0.16
  const y1 = 1
  const x2 = 0.3
  const y2 = 1
  const cx = 3 * x1
  const bx = 3 * (x2 - x1) - cx
  const ax = 1 - cx - bx
  const cy = 3 * y1
  const by = 3 * (y2 - y1) - cy
  const ay = 1 - cy - by
  const sampleX = (t) => ((ax * t + bx) * t + cx) * t
  const sampleY = (t) => ((ay * t + by) * t + cy) * t
  const sampleDX = (t) => (3 * ax * t + 2 * bx) * t + cx
  return (x) => {
    let t = x
    for (let i = 0; i < 6; i += 1) {
      const slope = sampleDX(t)
      if (Math.abs(slope) < 1e-4) break
      t -= (sampleX(t) - x) / slope
    }
    return sampleY(Math.min(1, Math.max(0, t)))
  }
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
  for (const row of list.value?.querySelectorAll('.chosen-song') ?? []) {
    row.style.willChange = ''
  }
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
      <li
        v-for="song in displaySongs"
        :key="song.id"
        :data-id="song.id"
        class="chosen-song"
        :class="{
          'is-arriving': arrivingIds.includes(song.id),
          'is-leaving': leavingIds.includes(song.id),
          'is-leaving-float': leavingFloat && leavingIds.includes(song.id),
        }"
        :style="leavingStyle(song.id)"
        :aria-hidden="leavingIds.includes(song.id) ? 'true' : undefined"
      >
        <div class="chosen-body">
          <span class="song-label">
            <span class="book-badge">{{ song.bookId || '#' }}</span>
            <span class="song-name">
              <span>{{ song.title }}</span>
            </span>
          </span>
        </div>
        <span class="choice-tokens">
          <SongShow :song="song" @change="emit('show', songIndex(song), $event)" />
          <Estrofes
            compact
            :song="song"
            :open="estrofesOpen === song.id"
            @open="showEstrofes(song.id)"
            @close="estrofesOpen = null"
            @change="emit('show', songIndex(song), $event)"
          />
        </span>
        <span class="row-actions">
          <button type="button" class="icon" :aria-label="songControlName('Subir', song)" :title="songControlName('Subir', song)" :disabled="songIndex(song) <= 0" @click="emit('up', songIndex(song))">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 19V5M6 11l6-6 6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
          <button type="button" class="icon" :aria-label="songControlName('Descer', song)" :title="songControlName('Descer', song)" :disabled="songIndex(song) < 0 || songIndex(song) === songs.length - 1" @click="emit('down', songIndex(song))">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 5v14M6 13l6 6 6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
          <button type="button" class="icon row-remove" :aria-label="songControlName('Retirar', song)" :title="songControlName('Retirar', song)" :disabled="songIndex(song) < 0" @click="emit('remove', songIndex(song))">
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

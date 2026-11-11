<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { vClipTitle } from '../clipTitle.js'
import VerseList from './VerseList.vue'
import { SEASONS, byBookId, languageName, matches, otherLanguage } from '../songs.js'

const props = defineProps({
  songs: { type: Array, required: true },
  mode: { type: String, default: 'songs' },
})

const emit = defineEmits(['saved'])

const query = ref('')
const editing = ref(false)
const songId = ref(null)
const title = ref('')
const titleAlt = ref('')
const bookId = ref('')
const verses = ref([])
const altVerses = ref([])
const secondLanguage = ref(false)
const lang = ref('pt')
const langAlt = ref('la')
const defaultLyrics = ref('content')
const varies = ref('')
const baseline = ref('')
const busy = ref(false)
const error = ref('')
const savedNotice = ref(false)
const savedSong = ref(false)
const leavePrompt = ref(false)
const songList = ref(null)
const root = ref(null)
let songScroll = 0
const sundays = ref([])
const sundayQuery = ref('')
const sundayOrder = ref('liturgical')
const sundayFill = ref('all')
const sundayKind = ref('all')
const currentCycle = liturgicalCycle()
const years = reactive({
  A: currentCycle === 'A',
  B: currentCycle === 'B',
  C: currentCycle === 'C',
})
const sunday = ref(null)
const primeira = ref('')
const primeiraRef = ref('')
const segunda = ref('')
const segundaRef = ref('')
const salmo = ref('')
const evangelho = ref('')
const evangelhoRef = ref('')
const louvor = ref('')
const sundayBaseline = ref('')
const savedMessage = ref('')
const loadingSundays = ref(false)
const sundayList = ref(null)
let sundayScroll = 0
let sundayRequest = 0
let leaveReturn = null
let nextKey = 1

const lectionary = computed(() => props.mode === 'lectionary')

const heading = computed(() => (lectionary.value ? 'Leccionário' : 'Cancioneiro'))

const inEditor = computed(() => (lectionary.value ? sunday.value : editing.value))

const seasonLabel = computed(() => {
  const id = sunday.value?.season
  return SEASONS.find((item) => item.id === id)?.label || id || ''
})

const sundayDirty = computed(() => sunday.value && sundaySnapshot() !== sundayBaseline.value)

const canSaveSunday = computed(() => sundayDirty.value && !busy.value)

const sundayFiltersActive = computed(() =>
  sundayQuery.value.trim() !== '' ||
  sundayOrder.value !== 'liturgical' ||
  sundayFill.value !== 'all' ||
  sundayKind.value !== 'all' ||
  years.A !== (currentCycle === 'A') ||
  years.B !== (currentCycle === 'B') ||
  years.C !== (currentCycle === 'C'))

const SEASON_ORDER = ['advento', 'natal', 'quaresma', 'ramos', 'pascoa', 'pentecostes', 'comum']

const visibleSundays = computed(() => {
  const q = sundayQuery.value.trim().toLocaleLowerCase('pt')
  const anyYear = years.A || years.B || years.C
  const rows = sundays.value.filter((day) => {
    if (day.ano) {
      if (!years[day.ano]) return false
    } else if (!anyYear) {
      return false
    }
    if (sundayKind.value === 'sunday' && day.solemnity) return false
    if (sundayKind.value === 'solemnity' && !day.solemnity) return false
    if (sundayFill.value === 'empty' && !day.incomplete) return false
    if (sundayFill.value === 'filled' && day.incomplete) return false
    if (q && !(day.name || '').toLocaleLowerCase('pt').includes(q)) return false
    return true
  })
  const copy = rows.slice()
  if (sundayOrder.value === 'name') {
    copy.sort((a, b) => (a.name || '').localeCompare(b.name || '', 'pt'))
  } else if (sundayOrder.value === 'season') {
    copy.sort((a, b) => {
      const left = SEASON_ORDER.indexOf(a.season)
      const right = SEASON_ORDER.indexOf(b.season)
      const bySeason = (left < 0 ? 99 : left) - (right < 0 ? 99 : right)
      return bySeason || liturgicalKey(a) - liturgicalKey(b)
    })
  } else {
    copy.sort((a, b) => liturgicalKey(a) - liturgicalKey(b))
  }
  return copy
})

const openSundayLabel = computed(() => {
  if (!sundays.value.length || !visibleSundays.value.length) return ''
  const count = visibleSundays.value.filter((day) => day.incomplete).length
  if (count === 0) return 'todos preenchidos'
  return `${count} por preencher`
})

function blankReadings(day) {
  const names = []
  if (!String(day.primeira || '').trim()) names.push('Primeira leitura')
  if (!String(day.segunda || '').trim()) names.push('Segunda leitura')
  if (!String(day.salmo || '').trim()) names.push('Salmo')
  if (day.lenten && !String(day.louvor || '').trim()) names.push('Estrofe da aclamação')
  if (!String(day.evangelho || '').trim()) names.push('Evangelho')
  if (names.length < 2) return names[0] || 'por preencher'
  return `${names.slice(0, -1).join(', ')} e ${names[names.length - 1]}`
}

const singleYear = computed(() => {
  const selected = ['A', 'B', 'C'].filter((year) => years[year])
  return selected.length === 1 ? selected[0] : ''
})

const sundayGroups = computed(() => {
  const days = visibleSundays.value
  if (sundayOrder.value === 'name') return [{ id: 'name', label: '', days }]
  const buckets = new Map()
  for (const day of days) {
    const id = day.season || ''
    if (!buckets.has(id)) buckets.set(id, [])
    buckets.get(id).push(day)
  }
  return [...buckets.keys()]
    .sort((a, b) => {
      const left = SEASON_ORDER.indexOf(a)
      const right = SEASON_ORDER.indexOf(b)
      return (left < 0 ? 99 : left) - (right < 0 ? 99 : right)
    })
    .map((id) => ({
      id: id || 'season',
      label: seasonName(id),
      days: buckets.get(id).slice().sort((a, b) => {
        const byPlace = (Number(a.place) || 0) - (Number(b.place) || 0)
        return byPlace || liturgicalKey(a) - liturgicalKey(b)
      }),
    }))
})

function showSeason(day) {
  return sundayOrder.value === 'name' && !!seasonName(day.season)
}

function showYear(day) {
  return !!day.ano && !singleYear.value
}

function liturgicalKey(day) {
  const cycle = day.ano || (years.A ? 'A' : years.B ? 'B' : 'C')
  const rank = cycle === 'B' ? 1 : cycle === 'C' ? 2 : 0
  return rank * 1000 + (Number(day.place) || 0)
}

const visible = computed(() =>
  props.songs.filter((song) => matches(song, query.value)).sort(byBookId))

const dirty = computed(() => formSnapshot() !== baseline.value)

const canSave = computed(() => dirty.value && title.value.trim() && !busy.value)

function verse(markup, mark = '') {
  return { key: nextKey++, markup, mark }
}

function formSnapshot() {
  return JSON.stringify({
    title: title.value,
    titleAlt: titleAlt.value,
    bookId: bookId.value,
    verses: verses.value.map((item) => item.markup),
    secondLanguage: secondLanguage.value,
    lang: secondLanguage.value ? lang.value : '',
    langAlt: secondLanguage.value ? langAlt.value : '',
    defaultLyrics: secondLanguage.value ? defaultLyrics.value : 'content',
    altVerses: secondLanguage.value ? altVerses.value.map((item) => item.markup) : [],
    varies: varies.value,
    marks: varies.value ? verses.value.map((item) => item.mark || '') : [],
  })
}

function remember() {
  baseline.value = formSnapshot()
}

function sundaySnapshot() {
  const body = {
    primeira: primeira.value,
    primeiraRef: primeiraRef.value,
    segunda: segunda.value,
    segundaRef: segundaRef.value,
    salmo: salmo.value,
    evangelho: evangelho.value,
    evangelhoRef: evangelhoRef.value,
  }
  if (sunday.value?.lenten) body.louvor = louvor.value
  return JSON.stringify(body)
}

function rememberSunday() {
  sundayBaseline.value = sundaySnapshot()
}

function seasonName(id) {
  return SEASONS.find((item) => item.id === id)?.label || id || ''
}

async function readPayload(response) {
  const text = await response.text()
  if (!text.trim()) return {}
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function failureMessage(error, fallback) {
  const message = error instanceof Error ? error.message : ''
  if (!message || /json|fetch|network|Failed to|Unexpected/i.test(message)) return fallback
  return message
}

async function loadSundays() {
  const request = ++sundayRequest
  loadingSundays.value = true
  error.value = ''
  try {
    const response = await fetch('/api/sundays')
    const payload = await readPayload(response)
    if (request !== sundayRequest) return
    if (!response.ok || !Array.isArray(payload)) {
      throw new Error(payload?.error || 'Não foi possível ler os domingos.')
    }
    sundays.value = payload
  } catch (e) {
    if (request !== sundayRequest) return
    error.value = failureMessage(e, 'Não foi possível ler os domingos.')
  } finally {
    if (request === sundayRequest) loadingSundays.value = false
  }
}

function clearSundayFilters() {
  sundayQuery.value = ''
  sundayOrder.value = 'liturgical'
  sundayFill.value = 'all'
  sundayKind.value = 'all'
  years.A = currentCycle === 'A'
  years.B = currentCycle === 'B'
  years.C = currentCycle === 'C'
}

function liturgicalCycle(date = new Date()) {
  const year = date.getFullYear()
  const advent = new Date(year, 10, 27)
  advent.setDate(advent.getDate() + ((7 - advent.getDay()) % 7))
  advent.setHours(0, 0, 0, 0)
  const today = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const cycleYear = today < advent ? year : year + 1
  const mod = ((cycleYear % 3) + 3) % 3
  return mod === 1 ? 'A' : mod === 2 ? 'B' : 'C'
}

function showSunday(payload) {
  sunday.value = payload
  primeira.value = payload.primeira || ''
  primeiraRef.value = payload.primeiraRef || ''
  segunda.value = payload.segunda || ''
  segundaRef.value = payload.segundaRef || ''
  salmo.value = payload.salmo || ''
  evangelho.value = payload.evangelho || ''
  evangelhoRef.value = payload.evangelhoRef || ''
  louvor.value = payload.louvor || ''
  error.value = ''
  savedNotice.value = false
  rememberSunday()
}

function openSunday(day) {
  if (!day || busy.value) return
  sundayScroll = sundayList.value?.scrollTop || 0
  showSunday(day)
}

function closeSunday() {
  sunday.value = null
  leavePrompt.value = false
  error.value = ''
  loadSundays().then(() => {
    nextTick(() => {
      if (sundayList.value) sundayList.value.scrollTop = sundayScroll
    })
  })
}

function askToLeave() {
  leaveReturn = document.activeElement
  leavePrompt.value = true
  nextTick(() => {
    root.value?.querySelector('#leave-continue')?.focus()
  })
}

function stayEditing() {
  leavePrompt.value = false
  nextTick(() => {
    if (leaveReturn instanceof HTMLElement) leaveReturn.focus()
  })
}

function requestCloseSunday() {
  if (busy.value) return
  if (sundayDirty.value) {
    askToLeave()
    return
  }
  closeSunday()
}

function leaveEditor() {
  if (busy.value) return
  if (editing.value) requestCloseEditor()
  else requestCloseSunday()
}

async function saveSunday() {
  if (!canSaveSunday.value || !sunday.value) return
  error.value = ''
  savedNotice.value = false
  busy.value = true
  const body = {
    key: sunday.value.key,
    primeira: primeira.value.trim(),
    primeiraRef: primeiraRef.value.trim(),
    segunda: segunda.value.trim(),
    segundaRef: segundaRef.value.trim(),
    salmo: salmo.value.trim(),
    evangelho: evangelho.value.trim(),
    evangelhoRef: evangelhoRef.value.trim(),
  }
  if (sunday.value.lenten) body.louvor = louvor.value.trim()
  try {
    const response = await fetch('/api/sundays', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const payload = await readPayload(response)
    if (payload == null || !response.ok) {
      throw new Error(payload?.error || 'Não foi possível guardar o domingo.')
    }
    const index = sundays.value.findIndex((row) => row.key === payload.key)
    if (index >= 0) sundays.value[index] = payload
    sunday.value = null
    savedMessage.value = 'Domingo guardado.'
    savedSong.value = false
    savedNotice.value = true
    await loadSundays()
  } catch (e) {
    error.value = failureMessage(e, 'Não foi possível guardar o domingo.')
  } finally {
    busy.value = false
  }
}

function startNew() {
  songScroll = songList.value?.scrollTop || 0
  songId.value = null
  title.value = ''
  titleAlt.value = ''
  bookId.value = ''
  verses.value = [verse('')]
  altVerses.value = []
  secondLanguage.value = false
  lang.value = 'pt'
  langAlt.value = 'la'
  defaultLyrics.value = 'content'
  varies.value = ''
  error.value = ''
  savedNotice.value = false
  editing.value = true
  remember()
}

async function openSong(song) {
  songScroll = songList.value?.scrollTop || 0
  error.value = ''
  savedNotice.value = false
  savedSong.value = false
  try {
    const response = await fetch(`/api/songs/${song.id}`)
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível abrir o cântico.')
    }
    songId.value = payload.id
    title.value = payload.title || ''
    titleAlt.value = payload.titleAlt || ''
    bookId.value = payload.bookId || ''
    const content = Array.isArray(payload.content) && payload.content.length ? payload.content : ['']
    const alt = Array.isArray(payload.contentAlt) ? payload.contentAlt : []
    const seasons = Array.isArray(payload.verseSeasons) ? payload.verseSeasons : []
    const markedSundays = Array.isArray(payload.verseSundays) ? payload.verseSundays : []
    varies.value = seasons.length ? 'tempo' : markedSundays.length ? 'sunday' : ''
    const marks = varies.value === 'tempo' ? seasons : markedSundays
    const hasAlt = alt.some((line) => String(line).trim())
    if (hasAlt) {
      const aligned = alignLanguages(content, alt, marks)
      verses.value = aligned.primary
      altVerses.value = aligned.secondary
      secondLanguage.value = true
      lang.value = payload.lang || 'pt'
      langAlt.value = payload.langAlt && payload.langAlt !== lang.value
        ? payload.langAlt
        : otherLanguage(lang.value)
      defaultLyrics.value = payload.defaultLyrics === 'contentAlt' ? 'contentAlt' : 'content'
    } else {
      verses.value = content.map((markup, index) => verse(markup, marks[index] || ''))
      altVerses.value = []
      secondLanguage.value = false
      lang.value = 'pt'
      langAlt.value = 'la'
      defaultLyrics.value = 'content'
    }
    editing.value = true
    remember()
  } catch (e) {
    error.value = e.message
  }
}

function alignLanguages(primaryLines, secondaryLines, marks = []) {
  const primary = primaryLines.length ? primaryLines : ['']
  const secondary = secondaryLines.slice()
  const count = Math.max(primary.length, secondary.length)
  const primaryRows = []
  const secondaryRows = []
  for (let i = 0; i < count; i++) {
    primaryRows.push(verse(primary[i] ?? '', marks[i] || primaryRows[i - 1]?.mark || ''))
    secondaryRows.push(verse(secondary[i] ?? primary[i] ?? ''))
  }
  return { primary: primaryRows, secondary: secondaryRows }
}

function setVaries(mode) {
  const fallback = mode === 'sunday' ? (sundays.value[0]?.key || 'advent-1/A') : 'comum'
  const known = mode === 'tempo'
    ? new Set(SEASONS.map((item) => item.id))
    : new Set(sundays.value.map((day) => day.key))
  verses.value = verses.value.map((item) => ({
    ...item,
    mark: mode && known.has(item.mark) ? item.mark : mode ? fallback : '',
  }))
  varies.value = mode
}

function setSecondLanguage(on) {
  if (on) {
    if (!lang.value) lang.value = 'pt'
    if (!langAlt.value || langAlt.value === lang.value) langAlt.value = otherLanguage(lang.value)
    const aligned = alignLanguages(
      verses.value.map((item) => item.markup),
      altVerses.value.map((item) => item.markup),
      verses.value.map((item) => item.mark || ''),
    )
    verses.value = aligned.primary
    altVerses.value = aligned.secondary
    if (defaultLyrics.value !== 'contentAlt') defaultLyrics.value = 'content'
  } else {
    defaultLyrics.value = 'content'
  }
  secondLanguage.value = on
}

function closeEditor() {
  editing.value = false
  error.value = ''
  leavePrompt.value = false
  nextTick(() => {
    if (songList.value) songList.value.scrollTop = songScroll
  })
}

function requestCloseEditor() {
  if (dirty.value) {
    askToLeave()
    return
  }
  closeEditor()
}

function confirmLeave() {
  if (lectionary.value && sunday.value) closeSunday()
  else closeEditor()
}

function dismissSaved() {
  savedNotice.value = false
  savedSong.value = false
}

function returnToSongs() {
  dismissSaved()
  closeEditor()
}

function onKeydown(event) {
  if (event.key !== 'Escape' || !root.value || root.value.offsetParent === null) return
  if (leavePrompt.value) {
    stayEditing()
    return
  }
  if (savedNotice.value) {
    dismissSaved()
    return
  }
  if (lectionary.value && sunday.value) requestCloseSunday()
}

async function save() {
  if (!canSave.value) return
  error.value = ''
  savedNotice.value = false
  busy.value = true
  const rows = verses.value
    .map((item, index) => ({
      markup: item.markup,
      alt: secondLanguage.value ? (altVerses.value[index]?.markup || '') : '',
      mark: item.mark || (varies.value === 'sunday' ? (sundays.value[0]?.key || '') : 'comum'),
    }))
    .filter((row) => row.markup.trim() || row.alt.trim())
    .map((row) => ({
      markup: row.markup.trim() ? row.markup : row.alt,
      alt: row.alt.trim() ? row.alt : row.markup,
      mark: row.mark,
    }))
  const body = {
    title: title.value,
    content: rows.map((row) => row.markup),
  }
  if (varies.value === 'tempo') body.verseSeasons = rows.map((row) => row.mark || 'comum')
  if (varies.value === 'sunday') body.verseSundays = rows.map((row) => row.mark)
  const altTitle = titleAlt.value.trim()
  if (altTitle) {
    body.titleAlt = altTitle
  }
  const alt = secondLanguage.value ? rows.map((row) => row.alt) : []
  if (alt.some((line) => line.trim())) {
    body.contentAlt = alt
    body.lang = lang.value
    body.langAlt = langAlt.value
    if (defaultLyrics.value === 'contentAlt') body.defaultLyrics = 'contentAlt'
  }
  const number = bookId.value.trim()
  if (number) {
    body.bookId = number
  }
  if (songId.value != null) {
    body.id = songId.value
  }
  try {
    const response = await fetch('/api/songs', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível guardar o cântico.')
    }
    emit('saved')
    if (payload.id != null) songId.value = payload.id
    remember()
    savedMessage.value = 'Cântico guardado.'
    savedSong.value = true
    savedNotice.value = true
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  loadSundays()
})
onUnmounted(() => {
  sundayRequest += 1
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <main ref="root" class="page" :class="{ 'catalog-list': !inEditor }">
    <header class="catalog-head">
      <h1>
        {{ heading }}<span v-if="lectionary && !inEditor && openSundayLabel" class="sunday-open"> · {{ openSundayLabel }}</span>
      </h1>
      <button v-if="inEditor" type="button" class="text-action" :disabled="busy" @click="leaveEditor">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15 6 9 12l6 6" />
        </svg>
        Voltar
      </button>
    </header>
    <div v-if="lectionary && sunday" class="sunday-lead">
      <p class="sunday-title">{{ sunday.name }}<template v-if="sunday.ano"> · Ano {{ sunday.ano }}</template></p>
      <p class="sunday-note">
        <template v-if="seasonLabel">{{ seasonLabel }}. </template>
        <template v-if="sunday.ano">Estas frases ficam neste domingo do ano {{ sunday.ano }}, para qualquer data em que ele calhe.</template>
        <template v-else>Estas frases ficam nesta solenidade, para qualquer ano.</template>
        A próxima Eucaristia lê-as, salvo se as substituir só para essa apresentação.
      </p>
    </div>
    <section v-if="!lectionary && editing" class="panel catalog-editor">
      <h2>{{ songId == null ? 'Novo cântico' : 'Editar cântico' }}</h2>
      <div class="catalog-fields">
        <label class="field">
          <span>Título</span>
          <input v-model="title" type="text" />
        </label>
        <label class="field">
          <span>Número do cancioneiro</span>
          <input v-model="bookId" type="text" placeholder="118, A1, RP3" />
        </label>
        <label class="field title-alt-field">
          <span>Título alternativo (opcional)</span>
          <input v-model="titleAlt" type="text" />
        </label>
      </div>
      <div class="field verses-field">
        <span>Estrofes e refrões</span>
        <div class="verse-language">
          <label class="cover-toggle">
            <input
              class="switch small"
              type="checkbox"
              :checked="secondLanguage"
              @change="setSecondLanguage($event.target.checked)"
            />
            Língua secundária
          </label>
          <div class="default-language">
            <span id="varies-label">As estrofes mudam</span>
            <div class="default-choices" role="radiogroup" aria-labelledby="varies-label">
              <label>
                <input type="radio" name="varies" value="" :checked="varies === ''" @change="setVaries('')" />
                Não
              </label>
              <label>
                <input type="radio" name="varies" value="tempo" :checked="varies === 'tempo'" @change="setVaries('tempo')" />
                Com o tempo
              </label>
              <label>
                <input type="radio" name="varies" value="sunday" :checked="varies === 'sunday'" @change="setVaries('sunday')" />
                Com o domingo
              </label>
            </div>
          </div>
          <div v-if="secondLanguage" class="default-language">
            <span id="default-language-label">Língua por omissão</span>
            <div class="default-choices" role="radiogroup" aria-labelledby="default-language-label">
              <label>
                <input v-model="defaultLyrics" type="radio" value="content" />
                {{ languageName(lang) }}
              </label>
              <label>
                <input v-model="defaultLyrics" type="radio" value="contentAlt" />
                {{ languageName(langAlt) }}
              </label>
            </div>
          </div>
        </div>
        <VerseList
          v-model="verses"
          :alt="secondLanguage ? altVerses : null"
          :lang="lang"
          :lang-alt="langAlt"
          :marks="varies"
          :sundays="sundays"
          @update:alt="altVerses = $event"
          @update:lang="lang = $event"
          @update:lang-alt="langAlt = $event"
        />
      </div>
      <div class="catalog-actions">
        <button type="button" :disabled="busy" @click="requestCloseEditor">Cancelar</button>
        <button class="primary" type="button" :disabled="!canSave" @click="save">
          {{ busy ? 'A guardar…' : 'Guardar' }}
        </button>
      </div>
      <p v-if="error" class="status error">{{ error }}</p>
    </section>
    <section v-else-if="lectionary && sunday" class="panel catalog-editor">
      <h2>Frases</h2>
      <div class="readings">
        <div class="reading">
          <span>Primeira leitura</span>
          <div class="reading-pair">
            <input v-model="primeiraRef" type="text" placeholder="Referência" aria-label="Referência da primeira leitura" />
            <input v-model="primeira" type="text" placeholder="Citação" aria-label="Primeira leitura" />
          </div>
        </div>
        <div class="reading">
          <span>Segunda leitura</span>
          <div class="reading-pair">
            <input v-model="segundaRef" type="text" placeholder="Referência" aria-label="Referência da segunda leitura" />
            <input v-model="segunda" type="text" placeholder="Citação" aria-label="Segunda leitura" />
          </div>
        </div>
        <label class="reading">
          <span>Salmo</span>
          <input v-model="salmo" type="text" placeholder="Citação" aria-label="Salmo" />
        </label>
        <label v-if="sunday.lenten" class="reading">
          <span>Estrofe da aclamação</span>
          <input v-model="louvor" type="text" placeholder="Citação" aria-label="Estrofe da aclamação" />
        </label>
        <div class="reading">
          <span>Evangelho</span>
          <div class="reading-pair">
            <input v-model="evangelhoRef" type="text" placeholder="Referência" aria-label="Referência do Evangelho" />
            <input v-model="evangelho" type="text" placeholder="Citação" aria-label="Evangelho" />
          </div>
        </div>
      </div>
      <div class="catalog-actions">
        <button type="button" :disabled="busy" @click="requestCloseSunday">Cancelar</button>
        <button class="primary" type="button" :disabled="!canSaveSunday" @click="saveSunday">
          {{ busy ? 'A guardar…' : 'Guardar' }}
        </button>
      </div>
      <p v-if="error" class="status error" role="alert">{{ error }}</p>
    </section>
    <section v-else-if="!lectionary" class="panel">
      <div class="panel-head">
        <input v-model="query" class="search" type="search" placeholder="Título ou número" />
        <button class="primary" type="button" @click="startNew">Novo cântico</button>
      </div>
      <ul ref="songList" class="songs">
          <li v-for="song in visible" :key="song.id">
            <button type="button" class="catalog-song" @click="openSong(song)">
              <span class="song-label">
                <span class="book-badge">{{ song.bookId || '#' }}</span>
                <span class="song-name">
                  {{ song.title }}
                  <span v-clip-title class="title-alt">{{ song.titleAlt ? `(${song.titleAlt})` : '' }}</span>
                </span>
              </span>
              <span v-if="!song.hasVerses" class="missing">sem letra</span>
              <span v-else-if="!song.bookId" class="missing">sem número</span>
            </button>
          </li>
        </ul>
      <p v-if="error" class="status error" role="alert">{{ error }}</p>
    </section>
    <section v-else class="panel">
        <div class="sunday-filters">
          <input v-model="sundayQuery" class="search" type="search" placeholder="Nome do domingo ou solenidade" aria-label="Nome do domingo ou solenidade" />
          <div class="year-toggles" role="group" aria-label="Anos">
            <button type="button" :aria-pressed="years.A" @click="years.A = !years.A">Ano A</button>
            <button type="button" :aria-pressed="years.B" @click="years.B = !years.B">Ano B</button>
            <button type="button" :aria-pressed="years.C" @click="years.C = !years.C">Ano C</button>
          </div>
          <label>
            <span>Leituras</span>
            <span class="select-size">
              <select v-model="sundayFill" aria-label="Leituras">
                <option value="all">Todas</option>
                <option value="empty">Por preencher</option>
                <option value="filled">Preenchidas</option>
              </select>
              <span aria-hidden="true">Por preencher</span>
            </span>
          </label>
          <label>
            <span>Ordem</span>
            <span class="select-size">
              <select v-model="sundayOrder" aria-label="Ordem">
                <option value="liturgical">Ano litúrgico</option>
                <option value="name">Nome</option>
                <option value="season">Tempo</option>
              </select>
              <span aria-hidden="true">Ano litúrgico</span>
            </span>
          </label>
          <label>
            <span>Mostrar</span>
            <span class="select-size">
              <select v-model="sundayKind" aria-label="Mostrar">
                <option value="all">Tudo</option>
                <option value="sunday">Domingos</option>
                <option value="solemnity">Solenidades</option>
              </select>
              <span aria-hidden="true">Solenidades</span>
            </span>
          </label>
        </div>
        <ul ref="sundayList" class="songs" :class="{ 'is-empty': !visibleSundays.length }">
          <template v-for="group in sundayGroups" :key="group.id">
            <li v-if="group.label" class="sunday-group">
              <h2>{{ group.label }}</h2>
            </li>
            <li v-for="day in group.days" :key="day.key">
              <button type="button" class="catalog-song" @click="openSunday(day)">
                <span class="mark" :class="{ filled: !day.incomplete }" aria-hidden="true"></span>
                <span class="sunday-label">
                  <span>{{ day.name }}</span>
                  <span v-if="showSeason(day) || showYear(day)" class="sunday-meta">
                    <template v-if="showSeason(day)">{{ seasonName(day.season) }}</template><template v-if="showSeason(day) && showYear(day)"> · </template><template v-if="showYear(day)">Ano {{ day.ano }}</template>
                  </span>
                  <span v-if="day.incomplete" class="sunday-blanks">{{ blankReadings(day) }}</span>
                  <span v-else class="sunday-state">preenchido</span>
                </span>
                <span v-if="day.solemnity" class="solemnity-badge">Solenidade</span>
              </button>
            </li>
          </template>
        </ul>
        <p v-if="loadingSundays && !sundays.length" class="sunday-note" role="status">A ler os domingos…</p>
        <p v-else-if="!visibleSundays.length && (sundays.length || !error)" class="sunday-note">Nenhum domingo com estes filtros.</p>
        <p v-if="error" class="status error" role="alert">{{ error }}</p>
        <div v-if="error || sundayFiltersActive" class="sunday-recovery">
          <button v-if="error" type="button" :disabled="loadingSundays" @click="loadSundays">Tentar de novo</button>
          <button v-if="sundayFiltersActive" type="button" @click="clearSundayFilters">Limpar filtros</button>
        </div>
    </section>
    <div v-if="leavePrompt" class="modal-backdrop" @click.self="stayEditing">
      <div class="modal confirm leave-prompt" role="dialog" aria-modal="true" aria-labelledby="leave-changes">
        <p id="leave-changes">Há alterações por guardar. Deseja descartá-las?</p>
        <div class="open-actions">
          <button id="leave-continue" type="button" @click="stayEditing">Continuar a editar</button>
          <button class="primary" type="button" @click="confirmLeave">Sair sem guardar</button>
        </div>
      </div>
    </div>
    <div v-if="savedNotice" class="modal-backdrop" @click.self="dismissSaved">
      <div class="modal" :class="{ 'confirm leave-prompt': savedSong }" role="dialog" aria-modal="true" aria-labelledby="song-saved">
        <button class="icon close" type="button" aria-label="Fechar" @click="dismissSaved">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>
        <div class="done-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="9" />
            <path d="m8 12.2 2.6 2.6L16.2 9.2" />
          </svg>
        </div>
        <p id="song-saved">{{ savedMessage }}</p>
        <div class="open-actions">
          <template v-if="savedSong">
            <button type="button" @click="dismissSaved">Continuar a editar</button>
            <button class="primary" type="button" @click="returnToSongs">Voltar para a lista de cânticos</button>
          </template>
          <button v-else class="primary" type="button" @click="dismissSaved">Fechar</button>
        </div>
      </div>
    </div>
  </main>
</template>

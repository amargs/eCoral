<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import SongList from './SongList.vue'
import { ACCENTS, COVERS, MOMENTS, SEASONS, comingSunday, estrofeCountLabel, keepEstrofes, languageLabel, preferredLyrics, rememberedChoice, slideChoice, withShow } from '../songs.js'
import Estrofes from './Estrofes.vue'
import SongShow from './SongShow.vue'

const props = defineProps({
  songs: { type: Array, required: true },
  folder: { type: String, required: true },
  draft: { type: Object, default: null },
})

const emit = defineEmits(['choose-folder', 'generated', 'draft', 'restored', 'other-shape'])

const SONG_MOMENTS = new Set(MOMENTS.map((moment) => moment.id))
const INTERVALS = new Set(['vazio', 'imagem', 'texto', 'texto+imagem'])
const FORM_FIELDS = ['domingo', 'frase', 'versiculo', 'primeira', 'primeiraRef', 'segunda', 'segundaRef', 'salmo', 'louvor']

const date = ref(props.draft?.date || comingSunday())
const liturgy = ref(null)
const liturgyError = ref('')
const loading = ref(false)
const form = reactive({
  domingo: '',
  frase: '',
  versiculo: '',
  primeira: '',
  primeiraRef: '',
  segunda: '',
  segundaRef: '',
  salmo: '',
  louvor: '',
})
const useSolemnity = ref(false)
const readingsOpen = ref(false)
const readingsDialog = ref(null)
const cover = ref('')
const accent = ref('accent1')
const accentTouched = ref(false)
const slots = reactive({})
const active = ref('entrada')
const estrofesOpen = ref('')
const query = ref('')
const credo = ref('longo')
const oracao = ref('')
const interval = ref('vazio')
const intervalText = ref('')
const intervalImage = ref('')
const output = ref('')
const outputEdited = ref(false)
const outputInput = ref(null)
const error = ref('')
const fileOpen = ref('')
const busy = ref(false)
let liturgyRequest = 0
const missingPrompt = ref([])
const replaceName = ref('')
let saveReady = props.draft == null
let restorePending = props.draft != null
let pendingSlots = false
const restoring = ref(props.draft != null)
let saveTimer = 0
let persistTail = Promise.resolve()

const rows = computed(() => {
  const list = []
  for (const moment of MOMENTS) {
    if (moment.id === 'gloria' && omitsGloria.value) {
      continue
    }
    if (moment.id === 'aleluia' && lenten.value) {
      list.push({ id: 'louvor', label: 'Louvor a vós', fixed: true })
      continue
    }
    list.push({
      ...moment,
      optional: moment.id === 'gloria' || moment.id === 'paiNosso',
    })
  }
  return list
})

const moments = computed(() => rows.value.filter((moment) => !moment.fixed))

const placedIds = computed(() =>
  Object.values(slots).filter(Boolean).map((song) => song.id))

const intervalReady = computed(() => {
  if (interval.value === 'imagem' || interval.value === 'texto+imagem') {
    if (!intervalImage.value) {
      return false
    }
  }
  if (interval.value === 'texto' || interval.value === 'texto+imagem') {
    if (!intervalText.value.trim()) {
      return false
    }
  }
  return true
})

const seasonLabel = computed(() => {
  const season = chosenCover.value?.season || liturgy.value?.season
  return SEASONS.find((item) => item.id === season)?.label || season || ''
})

const chosenCover = computed(() => COVERS.find((item) => item.id === cover.value) || null)

const omitsGloria = computed(() => (
  chosenCover.value?.season ? !!chosenCover.value.omitsGloria : !!liturgy.value?.omitsGloria
))

const lenten = computed(() => (
  chosenCover.value?.season ? !!chosenCover.value.lenten : !!liturgy.value?.lenten
))

const accentChoice = computed(() => ACCENTS.find((item) => item.id === accent.value) || ACCENTS[0])

const celebrations = ref([])
const celebrationSpan = ref({ from: '', until: '' })
const dayOpen = ref(false)
const dayFiltering = ref(false)
const dayCursor = ref(0)
const dayMenuWidth = ref(0)
let daySnapshot = ''

const dayLine = computed(() => {
  const parts = []
  if (liturgy.value?.ano) {
    parts.push(`Ano ${liturgy.value.ano}`)
  }
  if (seasonLabel.value) {
    parts.push(seasonLabel.value)
  }
  if (liturgy.value) {
    parts.push(accentChoice.value.name.toLowerCase())
  }
  return parts.join(' · ')
})

function foldDay(value) {
  return (value || '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()
}

const dayOrder = computed(() => {
  const days = celebrations.value
  const now = new Date()
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  const start = days.findIndex((day) => day.date >= today)
  if (start <= 0) {
    return days
  }
  return days.slice(start).concat(days.slice(0, start))
})

const dayMatches = computed(() => {
  if (!dayFiltering.value) {
    return dayOrder.value
  }
  const words = foldDay(form.domingo).split(/\s+/).filter(Boolean)
  if (!words.length) {
    return dayOrder.value
  }
  return dayOrder.value.filter((day) => {
    const tokens = foldDay(dayLabel(day)).split(/\s+/).filter(Boolean)
    return words.every((word) => tokens.some((token) => dayToken(token, word)))
  })
})

const dayHint = computed(() => (
  dayFiltering.value && !dayMatches.value.length ? 'Nenhum dia com esse nome.' : ''
))

function dayLabel(day) {
  return day.ano ? `${day.name} · Ano ${day.ano}` : day.name
}

function dayToken(token, word) {
  if (/^[ivx]+$/.test(word)) {
    return token === word
  }
  return token.startsWith(word)
}

const coverLabel = computed(() => COVERS.find((item) => item.id === cover.value)?.label || cover.value)

const liturgyUrl = computed(() => {
  const [year, month, day] = (date.value || '').split('-')
  if (!year || !month || !day) {
    return ''
  }
  return `https://www.liturgia.pt/liturgiadiaria/dia.php?data=${year}-${Number(month)}-${Number(day)}`
})

const openCount = computed(() =>
  moments.value.filter((moment) => !moment.optional && !slots[moment.id]).length)

const openMoments = computed(() =>
  rows.value.filter((moment) => !moment.fixed && !moment.optional && !slots[moment.id]))

const armedMoment = computed(() => rows.value.find((moment) => moment.id === active.value))

const railHeading = computed(() => {
  if (!armedMoment.value) {
    return 'Procurar'
  }
  if (armedMoment.value.id === 'aleluia') {
    return 'aleluia'
  }
  return `Escolher ${armedMoment.value.label}`
})

const assignLabel = computed(() =>
  armedMoment.value ? `Pôr em ${armedMoment.value.label}` : 'Adicionar')

const openLabel = computed(() => {
  if (!liturgy.value) {
    return ''
  }
  const count = openCount.value
  if (count === 0) {
    return 'todos escolhidos'
  }
  return `${count} por escolher`
})

const folderLeaf = computed(() => {
  const parts = props.folder.split(/[/\\]/).filter(Boolean)
  return parts.at(-1) || ''
})

const credoGlance = computed(() => (credo.value === 'breve' ? 'Credo breve' : 'Credo longo'))

const intervalGlance = computed(() => {
  if (interval.value === 'imagem') {
    return 'intervalo com imagem'
  }
  if (interval.value === 'texto') {
    return 'intervalo com texto'
  }
  if (interval.value === 'texto+imagem') {
    return 'intervalo com texto e imagem'
  }
  return 'intervalo em branco'
})

const canGenerate = computed(() =>
  liturgy.value && !liturgyError.value && output.value.trim() && intervalReady.value && !loading.value)

watch(rows, (list) => {
  if (!list.some((moment) => moment.id === active.value && !moment.fixed)) {
    active.value = list.find((moment) => !moment.fixed)?.id || 'entrada'
  }
})

watch(date, () => {
  loadLiturgy()
}, { immediate: true })

watch(useSolemnity, (on) => {
  const day = liturgy.value
  if (!day?.solemnity) {
    return
  }
  form.domingo = on ? day.solemnity.title : (day.domingo || '')
  if (!outputEdited.value) {
    output.value = (on ? day.solemnity.fileName : day.fileName) || output.value
  }
  cover.value = on ? day.solemnity.cover : (day.cover || '')
  if (!accentTouched.value) {
    showAccent(on ? day.solemnity.accent : day.accent)
  }
  if (on && day.solemnity.outranks) {
    fillReadings(day.solemnity)
  } else {
    fillReadings(day)
  }
}, { flush: 'sync' })

function fillReadings(payload) {
  form.frase = payload.frase || ''
  form.versiculo = payload.versiculo || ''
  form.primeira = payload.primeira?.citacao || ''
  form.primeiraRef = payload.primeira?.referencia || ''
  form.segunda = payload.segunda?.citacao || ''
  form.segundaRef = payload.segunda?.referencia || ''
  form.salmo = payload.salmo || ''
  form.louvor = payload.louvor || ''
}

function storedSentence(value) {
  return !!(value && String(value).trim())
}

function automaticCover() {
  const day = liturgy.value
  if (useSolemnity.value && day?.solemnity?.cover) {
    return day.solemnity.cover
  }
  return day?.cover || ''
}

function sameText(current, original) {
  return (current || '').trim() === (original || '').trim()
}

function shown(value) {
  const text = (value || '').trim()
  return text || '-'
}

function hasStoredSentences(source) {
  if (!source) {
    return false
  }
  if (source.primeira && storedSentence(source.primeira.citacao)) {
    return true
  }
  if (source.segunda && storedSentence(source.segunda.citacao)) {
    return true
  }
  if (storedSentence(source.salmo) || storedSentence(source.frase)) {
    return true
  }
  return !!(source.lenten && storedSentence(source.louvor))
}

const scripturesStored = computed(() => {
  const day = liturgy.value
  if (!day) {
    return false
  }
  if (day.persists === false) {
    return true
  }
  if (useSolemnity.value && day.solemnity?.outranks) {
    return hasStoredSentences(day.solemnity)
  }
  return hasStoredSentences(day)
})

function liturgyReadings(day) {
  return {
    frase: day.frase || '',
    versiculo: day.versiculo || '',
    primeira: day.primeira?.citacao || '',
    primeiraRef: day.primeira?.referencia || '',
    segunda: day.segunda?.citacao || '',
    segundaRef: day.segunda?.referencia || '',
    salmo: day.salmo || '',
    louvor: day.louvor || '',
  }
}

/** Readings the date and solemnity choice fill in before anyone edits them. */
function expectedReadings() {
  const day = liturgy.value
  if (!day) {
    return null
  }
  if (useSolemnity.value && day.solemnity?.outranks) {
    return liturgyReadings(day.solemnity)
  }
  return liturgyReadings(day)
}

function scripturesAltered() {
  const expected = expectedReadings()
  if (!expected) {
    return false
  }
  return Object.entries(expected).some(([key, value]) => !sameText(form[key], value))
}

const readingsDirty = computed(() => {
  const day = liturgy.value
  if (!day) {
    return false
  }
  if (cover.value !== automaticCover()) {
    return true
  }
  return scripturesAltered()
})

function onCoverPick() {
  if (accentTouched.value) {
    return
  }
  const chosen = COVERS.find((item) => item.id === cover.value)
  if (chosen) {
    showAccent(chosen.accent)
  }
}

function revertReadings() {
  const day = liturgy.value
  const expected = expectedReadings()
  if (!day || !expected) {
    return
  }
  for (const [key, value] of Object.entries(expected)) {
    form[key] = value
  }
  cover.value = automaticCover()
  if (!accentTouched.value) {
    showAccent(useSolemnity.value && day.solemnity ? day.solemnity.accent : day.accent)
  }
}

watch(readingsOpen, (open) => {
  if (!open) {
    return
  }
  nextTick(() => readingsDialog.value?.focus())
})

function showAccent(id) {
  const swatch = ACCENTS.find((item) => item.id === id)
  if (!swatch) {
    return
  }
  accent.value = swatch.id
}

function currentDayIndex() {
  const index = dayMatches.value.findIndex((day) => day.date === date.value)
  return index < 0 ? 0 : index
}

async function loadCelebrations(forDate) {
  if (!forDate) {
    return
  }
  const span = celebrationSpan.value
  if (span.from && forDate >= span.from && forDate <= span.until && celebrations.value.length) {
    return
  }
  try {
    const response = await fetch('/api/celebrations?date=' + encodeURIComponent(forDate))
    if (!response.ok) {
      return
    }
    const payload = await response.json()
    celebrations.value = payload.days || []
    celebrationSpan.value = { from: payload.from || '', until: payload.until || '' }
    if (dayOpen.value && !dayFiltering.value) {
      placeDayCursor(currentDayIndex())
    }
  } catch {
    // The date still fills the day when the list cannot be read.
  }
}

function placeDayCursor(index) {
  const count = dayMatches.value.length
  dayCursor.value = count === 0 ? 0 : Math.min(Math.max(index, 0), count - 1)
  nextTick(() => {
    document.getElementById('day-matches')?.querySelector('[aria-selected="true"]')?.scrollIntoView({ block: 'center' })
  })
}

function sizeDayMenu() {
  nextTick(() => {
    if (!dayOpen.value || dayFiltering.value) {
      return
    }
    const menu = document.getElementById('day-matches')
    if (!menu) {
      return
    }
    dayMenuWidth.value = menu.offsetWidth
  })
}

function onDayFocus(event) {
  daySnapshot = form.domingo
  dayFiltering.value = false
  dayOpen.value = true
  event.target.select()
  sizeDayMenu()
  nextTick(() => {
    if (dayFiltering.value) {
      return
    }
    placeDayCursor(currentDayIndex())
  })
}

function onDayInput() {
  if (form.domingo === daySnapshot) {
    return
  }
  dayFiltering.value = true
  dayOpen.value = true
  placeDayCursor(0)
}

function onDayToggle() {
  const field = document.getElementById('day-field')
  if (document.activeElement !== field) {
    field?.focus()
    return
  }
  dayOpen.value = !dayOpen.value
  if (!dayOpen.value) {
    return
  }
  dayFiltering.value = false
  sizeDayMenu()
  placeDayCursor(currentDayIndex())
}

function onDayBlur() {
  window.setTimeout(() => {
    const root = document.querySelector('.day-search')
    if (!root?.contains(document.activeElement)) {
      dayOpen.value = false
    }
  }, 0)
}

function pickDay(item) {
  dayOpen.value = false
  if (item.date !== date.value) {
    date.value = item.date
    return
  }
  form.domingo = daySnapshot
}

function onDayKey(event) {
  const matches = dayMatches.value
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    dayOpen.value = true
    dayCursor.value = Math.min(dayCursor.value + 1, Math.max(matches.length - 1, 0))
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    dayOpen.value = true
    dayCursor.value = Math.max(dayCursor.value - 1, 0)
  } else if (event.key === 'Enter' && dayOpen.value && matches[dayCursor.value]) {
    event.preventDefault()
    pickDay(matches[dayCursor.value])
  } else if (event.key === 'Escape') {
    form.domingo = daySnapshot
    dayOpen.value = false
  }
}

async function loadLiturgy() {
  const requested = date.value
  const token = ++liturgyRequest
  loadCelebrations(requested)
  loading.value = true
  accentTouched.value = false
  readingsOpen.value = false
  useSolemnity.value = false
  liturgyError.value = ''
  error.value = ''
  try {
    const response = await fetch('/api/liturgy?date=' + encodeURIComponent(requested))
    const payload = await response.json()
    if (token !== liturgyRequest) {
      return
    }
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível ler a liturgia.')
    }
    liturgy.value = payload
    cover.value = payload.cover || ''
    showAccent(payload.accent)
    form.domingo = payload.domingo || ''
    fillReadings(payload)
    if (!outputEdited.value && payload.fileName) {
      output.value = payload.fileName
    }
    if (payload.solemnity?.outranks) {
      useSolemnity.value = true
    }
    if (restorePending && requested === props.draft?.date) {
      applyDraft(props.draft)
    }
    restorePending = false
    finishRestore()
  } catch (e) {
    if (token !== liturgyRequest) {
      return
    }
    liturgy.value = null
    liturgyError.value = e instanceof Error ? e.message : 'Não foi possível ler a liturgia.'
    if (restorePending && requested === props.draft?.date) {
      applyDraft(props.draft)
    }
    restorePending = false
    finishRestore()
  } finally {
    if (token === liturgyRequest) {
      loading.value = false
      saveReady = true
      schedulePersist()
    }
  }
}

function finishRestore() {
  if (!restoring.value || pendingSlots) {
    return
  }
  restoring.value = false
  emit('restored')
}

function applyDraft(draft) {
  outputEdited.value = draft.outputEdited === true
  accentTouched.value = draft.accentTouched === true
  useSolemnity.value = draft.useSolemnity === true
  if (COVERS.some((item) => item.id === draft.cover)) {
    cover.value = draft.cover
  }
  if (ACCENTS.some((item) => item.id === draft.accent)) {
    showAccent(draft.accent)
  }
  const savedForm = draft.form || {}
  for (const key of FORM_FIELDS) {
    if (typeof savedForm[key] === 'string') {
      form[key] = savedForm[key]
    }
  }
  if (props.songs.length || !draft.slots || !Object.keys(draft.slots).length) {
    applySlots(draft.slots)
    pendingSlots = false
  } else {
    pendingSlots = true
  }
  credo.value = draft.credo === 'breve' ? 'breve' : 'longo'
  oracao.value = typeof draft.oracao === 'string' ? draft.oracao : ''
  interval.value = INTERVALS.has(draft.interval) ? draft.interval : 'vazio'
  intervalText.value = typeof draft.intervalText === 'string' ? draft.intervalText : ''
  intervalImage.value = typeof draft.intervalImage === 'string' ? draft.intervalImage : ''
  if (outputEdited.value && typeof draft.output === 'string') {
    output.value = draft.output
  }
}

function applySlots(saved) {
  for (const key of Object.keys(slots)) {
    delete slots[key]
  }
  const byId = new Map(props.songs.map((song) => [song.id, song]))
  for (const [moment, raw] of Object.entries(saved || {})) {
    const id = Number.isInteger(raw) ? raw : raw?.id
    if (!SONG_MOMENTS.has(moment) || !Number.isInteger(id)) {
      continue
    }
    const song = byId.get(id)
    if (!song) {
      continue
    }
    const previous = Number.isInteger(raw) ? null : {
      showLyrics: raw.lyrics === 'contentAlt' || raw.lyrics === 'content' ? raw.lyrics : undefined,
      showEstrofes: Array.isArray(raw.estrofes) || Number.isInteger(raw.estrofes) ? raw.estrofes : undefined,
    }
    slots[moment] = withShow(song, previous)
  }
}

function draftBody() {
  const slotIds = {}
  for (const [moment, song] of Object.entries(slots)) {
    if (SONG_MOMENTS.has(moment) && Number.isInteger(song?.id)) {
      slotIds[moment] = { id: song.id, ...rememberedChoice(song) }
    }
  }
  const savedForm = {}
  for (const key of FORM_FIELDS) {
    savedForm[key] = form[key]
  }
  return {
    date: date.value,
    form: savedForm,
    useSolemnity: useSolemnity.value,
    cover: cover.value,
    accent: accent.value,
    accentTouched: accentTouched.value,
    slots: slotIds,
    credo: credo.value,
    oracao: oracao.value,
    interval: interval.value,
    intervalText: intervalText.value,
    intervalImage: intervalImage.value,
    output: output.value,
    outputEdited: outputEdited.value,
  }
}

function hasUserWork() {
  if (Object.values(slots).some((song) => song?.id)) {
    return true
  }
  return scripturesAltered()
}

function queuePersist() {
  const run = persistTail.then(() => persistDraft())
  persistTail = run.then(() => {}, () => {})
  return run
}

function schedulePersist() {
  window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(queuePersist, 250)
}

async function persistDraft() {
  if (!saveReady || loading.value) {
    return
  }
  const keep = hasUserWork()
  emit('draft', keep)
  try {
    if (!keep) {
      await fetch('/api/eucharist-draft', { method: 'DELETE' })
      return
    }
    await fetch('/api/eucharist-draft', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(draftBody()),
    })
  } catch {
    // The next edit tries again.
  }
}

function flushDraft() {
  window.clearTimeout(saveTimer)
  if (!saveReady || loading.value) {
    return
  }
  const keep = hasUserWork()
  emit('draft', keep)
  const url = '/api/eucharist-draft'
  if (!keep) {
    fetch(url, { method: 'DELETE', keepalive: true })
    return
  }
  fetch(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(draftBody()),
    keepalive: true,
  })
}

watch(() => props.songs, (songs) => {
  const byId = new Map(songs.map((song) => [song.id, song]))
  for (const key of Object.keys(slots)) {
    const current = slots[key]
    const next = current && byId.get(current.id)
    if (next) {
      slots[key] = withShow(next, current)
    }
  }
  if (!pendingSlots || !props.draft || !songs.length) {
    return
  }
  applySlots(props.draft.slots)
  pendingSlots = false
  finishRestore()
  if (saveReady) {
    schedulePersist()
  }
})

watch(() => JSON.stringify(draftBody()), () => {
  if (!saveReady) {
    return
  }
  schedulePersist()
})

onMounted(() => window.addEventListener('pagehide', flushDraft))
onUnmounted(() => {
  window.removeEventListener('pagehide', flushDraft)
  flushDraft()
})

function pick(song) {
  if (!song.bookId || !song.hasVerses || rows.value.find((moment) => moment.id === active.value)?.fixed) {
    return
  }
  slots[active.value] = withShow(song)
}

function setShow(moment, patch) {
  const song = slots[moment]
  if (!song) return
  const next = { ...song, ...patch }
  if (patch.showLyrics && patch.showLyrics !== shownLyrics(song)) {
    next.showEstrofes = keepEstrofes(next, song.showEstrofes)
  }
  slots[moment] = next
}

function shownLyrics(song) {
  return song?.showLyrics || preferredLyrics(song)
}

function unpick(id) {
  for (const key of Object.keys(slots)) {
    if (slots[key]?.id === id) {
      delete slots[key]
    }
  }
}

function chooseMoment(id) {
  if (active.value !== id) estrofesOpen.value = ''
  active.value = id
}

function showEstrofes(id) {
  active.value = id
  estrofesOpen.value = estrofesOpen.value === id ? '' : id
}

function closeEstrofes() {
  estrofesOpen.value = ''
}

function clearMoment(id) {
  delete slots[id]
  if (estrofesOpen.value === id) estrofesOpen.value = ''
}

async function chooseImage() {
  error.value = ''
  try {
    const response = await fetch('/api/choose-image', { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível escolher a imagem.')
    }
    if (payload.path) {
      intervalImage.value = payload.path
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Não foi possível escolher a imagem.'
  }
}

function changed(current, original) {
  const next = (current || '').trim()
  const previous = (original || '').trim()
  return next !== previous && next
}

const OPTIONAL_SONGS = new Set(['gloria', 'paiNosso'])

function missingSongLabels() {
  return moments.value
    .filter((moment) => !moment.fixed && !OPTIONAL_SONGS.has(moment.id) && !slots[moment.id])
    .map((moment) => moment.label)
}

function listed(names) {
  if (names.length < 2) {
    return names[0] || ''
  }
  return `${names.slice(0, -1).join(', ')} e ${names[names.length - 1]}`
}

async function pickAnotherName() {
  replaceName.value = ''
  await nextTick()
  outputInput.value?.focus()
  outputInput.value?.select()
}

async function generate(force = false, replace = false) {
  const missing = missingSongLabels()
  if (force !== true && missing.length) {
    missingPrompt.value = missing
    return
  }
  missingPrompt.value = []
  replaceName.value = ''
  error.value = ''
  fileOpen.value = ''
  window.clearTimeout(saveTimer)
  await queuePersist()
  busy.value = true
  const songs = {}
  for (const moment of moments.value) {
    const song = slots[moment.id]
    if (song?.bookId) {
      const choice = slideChoice(song)
      songs[moment.id] = Object.keys(choice).length ? { bookId: song.bookId, ...choice } : song.bookId
    }
  }
  const day = liturgy.value
  const baseline = expectedReadings() || liturgyReadings(day)
  const leituras = {}
  const referencias = {}
  if (changed(form.primeira, baseline.primeira)) {
    leituras.primeira = form.primeira.trim()
  }
  if (day.segunda && changed(form.segunda, baseline.segunda)) {
    leituras.segunda = form.segunda.trim()
  }
  if (changed(form.salmo, baseline.salmo)) {
    leituras.salmo = form.salmo.trim()
  }
  if (changed(form.frase, baseline.frase)) {
    leituras.evangelho = form.frase.trim()
  }
  if (lenten.value && changed(form.louvor, baseline.louvor)) {
    leituras.louvor = form.louvor.trim()
  }
  if (day.primeira && changed(form.primeiraRef, baseline.primeiraRef)) {
    referencias.primeira = form.primeiraRef.trim()
  }
  if (day.segunda && changed(form.segundaRef, baseline.segundaRef)) {
    referencias.segunda = form.segundaRef.trim()
  }
  const opcoes = { credo: credo.value, interval: interval.value }
  if (oracao.value.trim()) {
    opcoes.oracao = oracao.value.trim()
  }
  if (interval.value === 'imagem' || interval.value === 'texto+imagem') {
    opcoes.intervalImage = intervalImage.value
  }
  if (interval.value === 'texto' || interval.value === 'texto+imagem') {
    opcoes.intervalText = intervalText.value.trim()
  }
  const body = {
    mode: 'mass',
    date: date.value,
    output: output.value.trim(),
    domingo: form.domingo.trim(),
    versiculo: form.versiculo.trim(),
    songs,
    leituras,
    opcoes,
    accent: accent.value,
  }
  if (replace === true) {
    body.replace = true
  }
  if (scripturesStored.value && readingsDirty.value) {
    body.manual = true
  }
  if (cover.value && cover.value !== automaticCover()) {
    body.cover = cover.value
  }
  if (!form.versiculo.trim()) {
    delete body.versiculo
  }
  if (Object.keys(referencias).length) {
    body.referencias = referencias
  }
  if (useSolemnity.value && day?.solemnity) {
    body.solemnity = true
  }
  if (props.folder) {
    body.directory = props.folder
  }
  try {
    const response = await fetch('/api/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const payload = await response.json()
    if (payload.exists) {
      replaceName.value = payload.name || output.value.trim()
      return
    }
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível gerar.')
    }
    emit('generated', payload.output)
  } catch (e) {
    const message = e instanceof Error ? e.message : 'Não foi possível gerar.'
    if (message.includes('ficheiro está aberto')) {
      fileOpen.value = message
    } else {
      error.value = message
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="deck">
    <header class="mass-head">
      <div class="specimen">
        <div class="day-search" :class="{ open: dayOpen }" :style="dayOpen && dayMenuWidth ? { minWidth: dayMenuWidth + 'px' } : undefined">
          <h1>
            <input
              id="day-field"
              v-model="form.domingo"
              type="text"
              role="combobox"
              :aria-expanded="dayOpen"
              aria-controls="day-matches"
              aria-autocomplete="list"
              :disabled="!liturgy"
              :aria-label="useSolemnity ? 'Título' : 'Domingo, escrever para procurar outro'"
              :placeholder="loading ? 'A ler a liturgia…' : 'Domingo'"
              @focus="onDayFocus"
              @input="onDayInput"
              @click="dayOpen = true"
              @blur="onDayBlur"
              @keydown="onDayKey"
            />
            <button
              type="button"
              class="day-toggle"
              :aria-expanded="dayOpen"
              aria-controls="day-matches"
              aria-label="Procurar um domingo"
              :disabled="!liturgy"
              @mousedown.prevent
              @click="onDayToggle"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6 9l6 6 6-6" />
              </svg>
            </button>
          </h1>
          <Transition name="day-menu">
          <ul v-show="dayOpen" id="day-matches" class="day-matches" role="listbox">
            <li v-if="dayHint" class="day-hint" @mousedown.prevent>{{ dayHint }}</li>
            <li v-for="(item, index) in dayMatches" :key="item.date + item.key">
              <button
                type="button"
                role="option"
                :aria-selected="index === dayCursor"
                @mousedown.prevent
                @click="pickDay(item)"
              >
                <span>{{ dayLabel(item) }}</span>
              </button>
            </li>
          </ul>
          </Transition>
        </div>
        <p class="catalog">
          <label>
            Data
            <input v-model.lazy="date" type="date" />
          </label>
          <span v-if="dayLine">{{ dayLine }}</span>
          <span>{{ credoGlance }} · {{ intervalGlance }}</span>
        </p>
      </div>
    </header>

    <section v-if="(loading && !liturgy) || liturgyError || liturgy?.solemnity" class="panel">
      <p v-if="loading" class="status">A ler a liturgia…</p>
      <p v-else-if="liturgyError" class="status error">{{ liturgyError }}</p>
      <div v-else-if="liturgy?.solemnity" class="solemnity">
        <p>{{ liturgy.solemnity.warning }}</p>
        <label v-if="liturgy.solemnity.outranks != null" class="check">
          <input v-model="useSolemnity" class="switch" type="checkbox" />
          <span>Usar {{ liturgy.solemnity.title }}</span>
        </label>
      </div>
    </section>

    <section class="panel export">
      <div class="export-fields">
        <label class="field">
          <span>Ficheiro</span>
          <input ref="outputInput" :value="output" type="text" @input="outputEdited = true; output = $event.target.value" />
        </label>
        <div class="field">
          <span>Pasta</span>
          <div class="path-row">
            <input :value="folderLeaf" :title="folder" type="text" readonly placeholder="Nenhuma pasta escolhida" />
            <button class="browse" type="button" title="Escolher pasta" aria-label="Escolher pasta" @click="emit('choose-folder')">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5v-10z" />
              </svg>
            </button>
          </div>
        </div>
        <button class="primary" type="button" :disabled="!canGenerate || busy" @click="generate">
          <span v-if="busy" class="spinner" aria-hidden="true"></span>
          {{ busy ? 'A gerar…' : 'Gerar' }}
        </button>
      </div>
      <p v-if="folder" class="path-full">{{ folder }}</p>
      <p v-if="openMoments.length" class="open-names">Ainda por preencher: {{ openMoments.map((moment) => moment.label).join(', ') }}.</p>
      <p class="shape-swap"><button type="button" @click="emit('other-shape')">Usar lista de cânticos</button></p>
      <p v-if="error" class="status error">{{ error }}</p>
    </section>

    <div class="columns mass-columns">
      <section class="panel">
        <h2>Momentos<span v-if="openLabel" class="open-count"> · {{ openLabel }}</span></h2>
        <ul class="moments">
          <li v-for="moment in rows" :key="moment.id">
            <div class="moment-pick">
              <button
                type="button"
                class="moment"
                :class="{ armed: !moment.fixed && active === moment.id }"
                :disabled="moment.fixed"
                @click="chooseMoment(moment.id)"
              >
                <span class="mark" :class="{ filled: moment.fixed || moment.optional || !!slots[moment.id] }" aria-hidden="true"></span>
                <span class="moment-name" :class="{ waiting: !moment.fixed && !moment.optional && !slots[moment.id] }">{{ moment.label }}<template v-if="active === moment.id && languageLabel(slots[moment.id])"> · {{ languageLabel(slots[moment.id]) }}</template><template v-if="active === moment.id && estrofeCountLabel(slots[moment.id])"> · {{ estrofeCountLabel(slots[moment.id]) }}</template></span>
                <span class="moment-book">
                  <Transition name="moment-swap">
                    <span v-if="slots[moment.id]" :key="slots[moment.id].id" class="book book-badge">{{ slots[moment.id].bookId || '#' }}</span>
                    <span v-else key="empty" class="book wait">—</span>
                  </Transition>
                </span>
                <span class="moment-song-slot">
                  <Transition name="moment-swap">
                    <span v-if="moment.fixed" key="fixed" class="moment-song">{{ form.louvor || 'O refrão fica no diapositivo' }}</span>
                    <span v-else-if="slots[moment.id]" :key="slots[moment.id].id" class="moment-song picked">{{ slots[moment.id].title }}</span>
                    <span v-else-if="moment.optional" key="rezado" class="moment-song">Rezado</span>
                    <span v-else key="empty" class="moment-song missing">Por escolher</span>
                  </Transition>
                </span>
              </button>
            </div>
            <SongShow v-if="!moment.fixed && active === moment.id && slots[moment.id]" :song="slots[moment.id]" @change="setShow(moment.id, $event)" />
            <Estrofes
              v-if="!moment.fixed && active === moment.id && slots[moment.id]"
              compact
              :song="slots[moment.id]"
              :open="estrofesOpen === moment.id"
              @open="showEstrofes(moment.id)"
              @close="closeEstrofes"
              @change="setShow(moment.id, $event)"
            />
            <button
              v-if="slots[moment.id]"
              type="button"
              class="icon row-remove"
              title="Remover cântico"
              :aria-label="'Retirar ' + moment.label"
              @click="clearMoment(moment.id)"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M6 6l12 12M18 6 6 18" />
              </svg>
            </button>
          </li>
        </ul>
      </section>
      <SongList
        class="song-rail"
        :heading="railHeading"
        :action-label="assignLabel"
        require-book-id
        :songs="songs"
        :query="query"
        :chosen-ids="placedIds"
        @update:query="query = $event"
        @add="pick"
        @remove-song="unpick"
      />
    </div>

    <section v-if="liturgy" class="panel cover">
      <div class="panel-head">
        <h2>Capa e leituras</h2>
        <div class="panel-actions">
          <button
            v-if="readingsDirty"
            class="text-action"
            type="button"
            title="Repor as leituras"
            aria-label="Repor as leituras"
            @click="revertReadings"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M3 12a9 9 0 1 0 2.5-6.2" />
              <path d="M3 4v5h5" />
            </svg>
          </button>
          <button class="text-action" type="button" @click="readingsOpen = true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M12 20h9" />
              <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
            </svg>
            Editar
          </button>
        </div>
      </div>
      <div v-if="liturgy.warnings?.length" class="warnings">
        <p v-for="(warning, index) in liturgy.warnings" :key="warning">
          {{ warning }}
          <a
            v-if="liturgyUrl && index === liturgy.warnings.length - 1"
            :href="liturgyUrl"
            target="_blank"
            rel="noopener noreferrer"
          >Abrir liturgia.pt para consultar</a>
        </p>
      </div>
      <div class="readings">
        <div class="reading">
          <span>Capa</span>
          <p>{{ coverLabel }}</p>
        </div>
        <div v-if="liturgy.primeira" class="reading">
          <span>Primeira leitura <em v-if="form.primeiraRef">{{ form.primeiraRef }}</em></span>
          <p :class="{ empty: !form.primeira.trim() }">{{ shown(form.primeira) }}</p>
        </div>
        <div class="reading">
          <span>Salmo</span>
          <p :class="{ empty: !form.salmo.trim() }">{{ shown(form.salmo) }}</p>
        </div>
        <div v-if="liturgy.segunda" class="reading">
          <span>Segunda leitura <em v-if="form.segundaRef">{{ form.segundaRef }}</em></span>
          <p :class="{ empty: !form.segunda.trim() }">{{ shown(form.segunda) }}</p>
        </div>
        <div v-if="lenten" class="reading">
          <span>Louvor</span>
          <p :class="{ empty: !form.louvor.trim() }">{{ shown(form.louvor) }}</p>
        </div>
        <div class="reading">
          <span>Evangelho <em v-if="form.versiculo">{{ form.versiculo }}</em></span>
          <p :class="{ empty: !form.frase.trim() }">{{ shown(form.frase) }}</p>
        </div>
      </div>
    </section>

    <section class="panel options">
      <h2>Opções</h2>
      <div class="stack">
        <label class="field">
          <span>Credo</span>
          <select v-model="credo">
            <option value="longo">Credo longo</option>
            <option value="breve">Credo breve</option>
          </select>
        </label>
        <label class="field wide">
          <span>Resposta da oração universal (opcional)</span>
          <input v-model="oracao" type="text" placeholder="Ouvi-nos, Senhor" />
        </label>
        <label class="field">
          <span>Intervalo entre slides</span>
          <select v-model="interval">
            <option value="vazio">Em branco</option>
            <option value="imagem">Imagem</option>
            <option value="texto">Texto</option>
            <option value="texto+imagem">Texto e imagem</option>
          </select>
        </label>
        <label v-if="interval === 'texto' || interval === 'texto+imagem'" class="field wide">
          <span>Texto do intervalo</span>
          <input v-model="intervalText" type="text" />
        </label>
        <div v-if="interval === 'imagem' || interval === 'texto+imagem'" class="field wide">
          <span>Imagem do intervalo</span>
          <div class="path-row">
            <input :value="intervalImage" type="text" readonly placeholder="Nenhuma imagem escolhida" />
            <button class="browse" type="button" title="Escolher imagem" aria-label="Escolher imagem" @click="chooseImage">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5v-10z" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </section>

    <div v-if="readingsOpen" class="modal-backdrop" @click.self="readingsOpen = false">
      <div
        ref="readingsDialog"
        class="modal readings-editor"
        role="dialog"
        aria-modal="true"
        aria-labelledby="readings-title"
        tabindex="-1"
        @keydown.esc="readingsOpen = false"
      >
        <button class="icon close" type="button" aria-label="Fechar" @click="readingsOpen = false">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>
        <h2 id="readings-title">Capa e leituras</h2>
        <div class="readings-scroll">
        <p v-if="scripturesStored" class="note">Estas alterações são válidas apenas para este domingo ou celebração e não substituem as ocorrências futuras.</p>
        <p v-else class="note">Ao gerar a apresentação, estas leituras ficam guardadas para a próxima vez que esta celebração se repetir.</p>
        <div class="readings">
          <label class="reading">
            <span>Capa</span>
            <select v-model="cover" aria-label="Capa" @change="onCoverPick">
              <option v-for="item in COVERS" :key="item.id" :value="item.id">{{ item.label }}</option>
            </select>
          </label>
          <div v-if="liturgy?.primeira" class="reading">
            <span>Primeira leitura</span>
            <input v-model="form.primeiraRef" type="text" placeholder="Referência" aria-label="Referência da primeira leitura" />
            <input v-model="form.primeira" type="text" placeholder="Citação" aria-label="Primeira leitura" />
          </div>
          <label class="reading">
            <span>Salmo</span>
            <input v-model="form.salmo" type="text" placeholder="Refrão" aria-label="Salmo" />
          </label>
          <div v-if="liturgy?.segunda" class="reading">
            <span>Segunda leitura</span>
            <input v-model="form.segundaRef" type="text" placeholder="Referência" aria-label="Referência da segunda leitura" />
            <input v-model="form.segunda" type="text" placeholder="Citação" aria-label="Segunda leitura" />
          </div>
          <label v-if="lenten" class="reading">
            <span>Louvor</span>
            <input v-model="form.louvor" type="text" placeholder="Citação" aria-label="Louvor" />
          </label>
          <div class="reading">
            <span>Evangelho</span>
            <input v-model="form.versiculo" type="text" placeholder="Referência" aria-label="Referência do Evangelho" />
            <input v-model="form.frase" type="text" placeholder="Citação" aria-label="Evangelho" />
          </div>
        </div>
        </div>
      </div>
    </div>
    <div v-if="missingPrompt.length" class="modal-backdrop" @click.self="missingPrompt = []">
      <div class="modal confirm" role="dialog" aria-modal="true" aria-labelledby="missing-songs">
        <p id="missing-songs">Faltam os seguintes cânticos: {{ listed(missingPrompt) }}.</p>
        <p>Quer gerar mesmo assim?</p>
        <div class="open-actions">
          <button type="button" @click="missingPrompt = []">Voltar a editar</button>
          <button class="primary" type="button" @click="generate(true)">Gerar mesmo assim</button>
        </div>
      </div>
    </div>
    <div v-if="fileOpen" class="modal-backdrop" @click.self="fileOpen = ''">
      <div class="modal confirm" role="dialog" aria-modal="true" aria-labelledby="file-open" @keydown.esc="fileOpen = ''">
        <p id="file-open" class="file-open">{{ fileOpen }}</p>
        <div class="open-actions">
          <button class="primary" type="button" @click="fileOpen = ''">Fechar</button>
        </div>
      </div>
    </div>
    <div v-if="replaceName" class="modal-backdrop" @click.self="pickAnotherName">
      <div class="modal confirm" role="dialog" aria-modal="true" aria-labelledby="replace-file">
        <p id="replace-file">Já existe um ficheiro com o nome {{ replaceName }}. Quer substituí-lo?</p>
        <div class="open-actions">
          <button type="button" @click="pickAnotherName">Escolher outro nome</button>
          <button class="primary" type="button" @click="generate(true, true)">Substituir</button>
        </div>
      </div>
    </div>
  </div>
</template>

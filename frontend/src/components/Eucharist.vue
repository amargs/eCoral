<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import SongList from './SongList.vue'
import { ACCENTS, COVERS, MOMENTS, SEASONS, comingSunday, keepEstrofes, preferredLyrics, rememberedChoice, slideChoice, withShow } from '../songs.js'
import Estrofes from './Estrofes.vue'
import SongShow from './SongShow.vue'
import VerseTempo from './VerseTempo.vue'
import ShapeLine from './ShapeLine.vue'
import FontNotice from './FontNotice.vue'

const props = defineProps({
  songs: { type: Array, required: true },
  folder: { type: String, required: true },
  draft: { type: Object, default: null },
  shape: { type: String, required: true },
  fonts: { type: Array, default: () => [] },
})

const emit = defineEmits(['choose-folder', 'generated', 'draft', 'restored', 'choose'])

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
const replaceName = ref('')
let saveReady = props.draft == null
let restorePending = props.draft != null
let pendingSlots = false
const restoring = ref(props.draft != null)
let saveTimer = 0
let persistTail = Promise.resolve()

const louvorSong = computed(() =>
  props.songs.find((song) => foldDay(song.title) === 'louvor a vos') || null)

const rows = computed(() => {
  const list = []
  for (const moment of MOMENTS) {
    if (moment.id === 'gloria' && omitsGloria.value) {
      continue
    }
    if (moment.id === 'aleluia' && lenten.value) {
      list.push({
        ...moment,
        fixed: true,
        song: louvorSong.value || { id: 'louvor-a-vos', title: 'Louvor a Vós', bookId: '' },
      })
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

const intervalWantsImage = computed(() => interval.value === 'imagem' || interval.value === 'texto+imagem')

const intervalWantsText = computed(() => interval.value === 'texto' || interval.value === 'texto+imagem')

const intervalMissing = computed(() => {
  const image = intervalWantsImage.value && !intervalImage.value
  const text = intervalWantsText.value && !intervalText.value.trim()
  if (image && text) {
    return 'Falta o texto e a imagem do intervalo.'
  }
  if (image) {
    return 'Falta a imagem do intervalo.'
  }
  if (text) {
    return 'Falta o texto do intervalo.'
  }
  return ''
})

const intervalReady = computed(() => !intervalMissing.value)

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

const celebrations = ref([])
const celebrationSpan = ref({ from: '', until: '' })
const daySearch = ref(null)
const dayOpen = ref(false)
const dayFiltering = ref(false)
const dayCursor = ref(0)
const daySpanning = ref(false)
let daySnapshot = ''
let daySpanTicket = 0

const massHead = ref(null)
const catalogLine = ref(null)
const seasonFact = ref(null)
const yearFact = ref(null)
const dateFact = ref(null)
const COLLAPSE = 72
let headScroll = null
let headFrame = 0

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

function daySeasonId(key) {
  key = (key || '').split('/')[0]
  if (key === 'palm-sunday') return 'ramos'
  if (key === 'pentecost') return 'pentecostes'
  if (key === 'christmas' || key === 'holy-family' || key === 'mary-mother-of-god' || key === 'epiphany' || key === 'baptism-of-the-lord') return 'natal'
  if (key === 'immaculate-conception' || key.startsWith('advent-')) return 'advento'
  if (key.startsWith('lent-')) return 'quaresma'
  if (key === 'easter-sunday' || key.startsWith('easter-')) return 'pascoa'
  if (key.startsWith('ordinary-') || key === 'trinity' || key === 'corpus-christi' || key === 'john-the-baptist' || key === 'peter-paul' || key === 'assumption' || key === 'all-saints') return 'comum'
  return ''
}

function dayTitle(day) {
  const season = SEASONS.find((item) => item.id === daySeasonId(day.key))?.label || ''
  const name = day.name || ''
  const parts = [name]
  if (season && !foldDay(name).includes(foldDay(season))) {
    parts.push(season)
  }
  if (day.ano) {
    parts.push(`Ano ${day.ano}`)
  }
  return parts.filter(Boolean).join(' · ')
}

function dayDate(day) {
  const [year, month, date] = (day.date || '').split('-')
  if (!year || !month || !date) {
    return ''
  }
  return `${date}/${month}/${year}`
}

function dayLabel(day) {
  return [dayTitle(day), dayDate(day)].filter(Boolean).join(' ')
}

function dayToken(token, word) {
  if (/^[ivx]+$/.test(word)) {
    return token === word
  }
  return token.startsWith(word)
}

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

const liturgyMissing = computed(() => {
  const day = liturgy.value
  if (!day) {
    return false
  }
  const source = useSolemnity.value && day.solemnity?.outranks ? day.solemnity : day
  return !hasStoredSentences(source)
})

const songNotices = computed(() => {
  const notices = []
  if (liturgyMissing.value) {
    notices.push({ key: 'liturgy', text: 'Não há liturgia para este dia.', readings: true })
  }
  if (openMoments.value.length) {
    notices.push({ key: 'empty', text: 'Há momentos sem cântico.', moment: openMoments.value[0].id })
  }
  return notices
})

const armedMoment = computed(() => rows.value.find((moment) => moment.id === active.value))

const railHeading = computed(() => {
  if (!armedMoment.value) {
    return 'Procurar'
  }
  return `Escolher ${armedMoment.value.label.toLocaleLowerCase('pt')}`
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

const canGenerate = computed(() =>
  liturgy.value && !liturgyError.value && output.value.trim() && intervalReady.value && !loading.value && !props.fonts.length)

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

const louvorRows = computed(() => {
  const lines = (form.louvor || '').split(/\r?\n/).length
  return Math.min(Math.max(lines, 2), 6)
})

const coverEffect = computed(() => {
  if (!liturgy.value) {
    return ''
  }
  if (lenten.value) {
    return 'Não se diz o Glória. A aclamação ao Evangelho é o Louvor a Vós'
  }
  if (omitsGloria.value) {
    return 'Não se diz o Glória.'
  }
  return ''
})

const readingNote = computed(() => {
  if (readingsDirty.value && scripturesStored.value) {
    return 'Estas alterações são válidas apenas para este domingo ou celebração e não substituem as ocorrências futuras.'
  }
  if (!scripturesStored.value) {
    return 'Ao gerar a apresentação, estas leituras ficam guardadas para a próxima vez que esta celebração se repetir.'
  }
  return ''
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
      sizeDayMenu()
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

function menuOuterWidth(menu) {
  const buttons = [...menu.querySelectorAll('button')]
  const previous = menu.style.width
  const widths = buttons.map((button) => button.style.width)
  menu.style.width = 'max-content'
  buttons.forEach((button) => {
    button.style.width = 'max-content'
  })
  const width = Math.ceil(menu.offsetWidth)
  menu.style.width = previous
  buttons.forEach((button, index) => {
    button.style.width = widths[index]
  })
  return width
}

function glideDaySpan(root, to) {
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const ticket = ++daySpanTicket
  if (reduced) {
    root.style.transition = ''
    root.style.width = to
    return ticket
  }
  const from = root.getBoundingClientRect().width
  root.style.transition = 'none'
  root.style.width = `${from}px`
  void root.offsetWidth
  root.style.transition = ''
  root.style.width = to
  return ticket
}

function sizeDayMenu() {
  if (!dayOpen.value || dayFiltering.value) {
    return
  }
  daySpanning.value = true
  nextTick(() => {
    const root = daySearch.value
    const menu = document.getElementById('day-matches')
    if (!root || !menu || !dayOpen.value || dayFiltering.value) {
      return
    }
    const current = Math.ceil(root.getBoundingClientRect().width)
    glideDaySpan(root, `${Math.max(menuOuterWidth(menu), current)}px`)
  })
}

function releaseDaySpan() {
  const root = daySearch.value
  if (!root || !daySpanning.value) {
    return
  }
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reduced) {
    daySpanning.value = false
    root.style.width = ''
    return
  }
  const from = root.getBoundingClientRect().width
  root.style.transition = 'none'
  root.style.width = 'max-content'
  const to = root.offsetWidth
  root.style.width = `${from}px`
  void root.offsetWidth
  if (Math.abs(from - to) < 1) {
    daySpanning.value = false
    root.style.transition = ''
    root.style.width = ''
    return
  }
  root.style.transition = ''
  const ticket = ++daySpanTicket
  root.style.width = `${to}px`
  const finish = (event) => {
    if (event.propertyName !== 'width') {
      return
    }
    root.removeEventListener('transitionend', finish)
    if (ticket !== daySpanTicket) {
      return
    }
    daySpanning.value = false
    root.style.width = ''
  }
  root.addEventListener('transitionend', finish)
  window.setTimeout(() => {
    if (ticket !== daySpanTicket || dayOpen.value) {
      return
    }
    root.removeEventListener('transitionend', finish)
    daySpanning.value = false
    root.style.width = ''
  }, 320)
}

watch(dayOpen, (open) => {
  if (!open) {
    nextTick(releaseDaySpan)
  }
})

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
    const root = daySearch.value
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
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    const opening = !dayOpen.value
    dayOpen.value = true
    if (opening) {
      sizeDayMenu()
    }
    dayCursor.value = event.key === 'ArrowDown'
      ? Math.min(dayCursor.value + 1, Math.max(matches.length - 1, 0))
      : Math.max(dayCursor.value - 1, 0)
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
      showTempo: typeof raw.season === 'string' ? raw.season : undefined,
      showSunday: typeof raw.sunday === 'string' ? raw.sunday : undefined,
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

function syncHead() {
  const head = massHead.value
  const scroller = headScroll
  if (!head || !scroller || dayOpen.value) {
    return
  }
  const p = Math.min(1, Math.max(0, scroller.scrollTop / COLLAPSE))
  head.style.setProperty('--p', p.toFixed(4))
  head.classList.toggle('is-compact', p > 0.92)
  const title = daySearch.value
  const catalog = catalogLine.value
  const facts = [seasonFact.value, yearFact.value, dateFact.value].filter(Boolean)
  if (!title || !catalog || !facts.length) {
    return
  }
  const dayInput = title.querySelector('input')
  const dateInput = dateFact.value?.querySelector('input')
  const locked = p > 0.92
  if (dayInput) dayInput.readOnly = locked
  if (dateInput) dateInput.readOnly = locked
  const lift = head.dataset.lift !== '0'
  catalog.style.marginTop = `${0.45 * (1 - (lift ? p : 0))}rem`
  catalog.style.marginBottom = lift ? `${-p * catalog.offsetHeight}px` : ''
  const name = title.querySelector('input')
  const nameBox = name?.getBoundingClientRect() || title.getBoundingClientRect()
  const toggleBox = title.querySelector('.day-toggle')?.getBoundingClientRect()
  const anchorRight = Math.max(nameBox.right, toggleBox?.width ? toggleBox.right : nameBox.right)
  const headBox = head.getBoundingClientRect()
  const shapeBox = head.querySelector('.shape-switch')?.getBoundingClientRect()
  const shapeBlocks = shapeBox && shapeBox.width > 0 && shapeBox.top < nameBox.bottom && shapeBox.bottom > nameBox.top
  const limit = (shapeBlocks ? shapeBox.left : headBox.right) - 8
  const titleMid = nameBox.top + nameBox.height / 2
  let x = anchorRight + 14
  const moves = []
  for (const el of facts) {
    const matrix = new DOMMatrix(getComputedStyle(el).transform)
    const box = el.getBoundingClientRect()
    const top = box.top - matrix.m42
    moves.push({
      el,
      tx: x - (box.left - matrix.m41),
      ty: titleMid - box.height / 2 - top,
    })
    x += box.width + 12
  }
  const fits = x <= limit
  head.dataset.lift = fits ? '1' : '0'
  if (fits !== lift) {
    catalog.style.marginTop = `${0.45 * (1 - (fits ? p : 0))}rem`
    catalog.style.marginBottom = fits ? `${-p * catalog.offsetHeight}px` : ''
  }
  for (const move of moves) {
    move.el.style.transform = fits ? `translate(${p * move.tx}px, ${p * move.ty}px)` : ''
  }
}

function openPinnedDate(event) {
  const input = event.currentTarget.closest('label')?.querySelector('input') || event.currentTarget
  try {
    input.showPicker()
  } catch {
    // The picker is already open, or this browser has none to show.
  }
}

function queueHead() {
  if (headFrame) {
    return
  }
  headFrame = requestAnimationFrame(() => {
    headFrame = 0
    syncHead()
  })
}

onMounted(() => {
  window.addEventListener('pagehide', flushDraft)
  headScroll = massHead.value?.closest('.app-body')
  headScroll?.addEventListener('scroll', queueHead, { passive: true })
  window.addEventListener('resize', queueHead)
  queueHead()
})
onUnmounted(() => {
  window.removeEventListener('pagehide', flushDraft)
  headScroll?.removeEventListener('scroll', queueHead)
  window.removeEventListener('resize', queueHead)
  if (headFrame) {
    cancelAnimationFrame(headFrame)
  }
  flushDraft()
})

watch(dayOpen, (open) => {
  if (!open) {
    queueHead()
  }
})

function pick(song) {
  if (!song.bookId || !song.hasVerses || rows.value.find((moment) => moment.id === active.value)?.fixed) {
    return
  }
  slots[active.value] = withShow(song)
}

function chooseShow(id, patch) {
  active.value = id
  if (estrofesOpen.value && estrofesOpen.value !== id) estrofesOpen.value = ''
  setShow(id, patch)
}

function setShow(moment, patch) {
  const song = slots[moment]
  if (!song) return
  const next = { ...song, ...patch }
  if ((patch.showLyrics && patch.showLyrics !== shownLyrics(song))
      || Object.prototype.hasOwnProperty.call(patch, 'showTempo')
      || Object.prototype.hasOwnProperty.call(patch, 'showSunday')) {
    next.showEstrofes = keepEstrofes(next, song.showEstrofes, chosenMark(next))
  }
  slots[moment] = next
}

function shownLyrics(song) {
  return song?.showLyrics || preferredLyrics(song)
}

function boxMark(song) {
  if (song?.verseSeasons?.length) return liturgy.value?.season || 'comum'
  if (song?.verseSundays?.length) return liturgy.value?.sunday || song.verseSundays[0]
  return ''
}

function chosenMark(song) {
  if (song?.verseSeasons?.length) return song.showTempo || boxMark(song)
  if (song?.verseSundays?.length) return song.showSunday || boxMark(song)
  return ''
}

function dayMarkLabel(song) {
  const mark = boxMark(song)
  if (!song?.verseSundays?.length) return ''
  if (song.verseSundayNames?.[mark]) return song.verseSundayNames[mark]
  const day = liturgy.value
  if (!day?.domingo) return ''
  return day.ano ? `${day.domingo} · Ano ${day.ano}` : day.domingo
}

function unpick(id) {
  for (const key of Object.keys(slots)) {
    if (slots[key]?.id === id) {
      delete slots[key]
    }
  }
}

function rowSong(moment) {
  return moment.song || slots[moment.id] || null
}

function chooseMoment(id) {
  if (active.value !== id) estrofesOpen.value = ''
  active.value = id
}

function showNotice(notice) {
  if (notice.readings) {
    nextTick(() => {
      document.getElementById('readings')?.scrollIntoView({ block: 'nearest' })
    })
    return
  }
  if (!notice.moment) {
    return
  }
  chooseMoment(notice.moment)
  nextTick(() => {
    document.getElementById(`moment-${notice.moment}`)?.scrollIntoView({ block: 'nearest' })
  })
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

async function pickAnotherName() {
  replaceName.value = ''
  await nextTick()
  outputInput.value?.focus()
  outputInput.value?.select()
}

async function generate(replace = false) {
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
      const choice = slideChoice(song, { mark: chosenMark(song), sendTempo: true })
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
    } else if (!(props.fonts.length && message.includes('Install the missing font'))) {
      error.value = message
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="deck">
    <header ref="massHead" class="mass-head">
      <div class="specimen">
        <div class="day-row">
        <div ref="daySearch" class="day-search" :class="{ open: dayOpen, spanning: daySpanning }">
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
                <span>{{ dayTitle(item) }}</span>
                <span v-if="dayDate(item)" class="day-date">{{ ' ' + dayDate(item) }}</span>
              </button>
            </li>
          </ul>
          </Transition>
        </div>
        <ShapeLine :shape="shape" group="missa-shape" @choose="emit('choose', $event)" />
        </div>
        <p ref="catalogLine" class="catalog">
          <span v-if="seasonLabel" ref="seasonFact" class="catalog-season">{{ seasonLabel }}</span>
          <span v-if="liturgy?.ano" ref="yearFact" class="catalog-fact">Ano {{ liturgy.ano }}</span>
          <label ref="dateFact" class="catalog-date">
            <span class="catalog-date-name">Data</span>
            <span class="catalog-date-value">
              <input v-model.lazy="date" type="date" aria-label="Data" @click="openPinnedDate" />
              <button type="button" class="catalog-calendar" tabindex="-1" aria-label="Escolher a data" @click="openPinnedDate">
                <svg viewBox="0 0 16 16" aria-hidden="true">
                  <rect x="2.25" y="3.25" width="11.5" height="10.5" rx="1.4" />
                  <path d="M2.25 6.5h11.5M5.5 1.75v2.5M10.5 1.75v2.5" />
                </svg>
              </button>
            </span>
          </label>
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
      <div v-if="songNotices.length" class="song-notices" role="group" aria-label="Avisos">
        <button
          v-for="notice in songNotices"
          :key="notice.key"
          type="button"
          @click="showNotice(notice)"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path stroke-linejoin="round" d="M12 4.2 21.2 20H2.8L12 4.2z" />
            <path stroke-linecap="round" d="M12 10v4.2" />
            <path stroke-linecap="round" d="M12 17.2h.01" />
          </svg>
          <span>{{ notice.text }}</span>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 6l6 6-6 6" />
          </svg>
        </button>
      </div>
      <p v-if="intervalMissing" class="open-names">{{ intervalMissing }}</p>
      <FontNotice :fonts="fonts" />
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
                :id="'moment-' + moment.id"
                class="moment"
                :class="{ armed: !moment.fixed && active === moment.id }"
                :disabled="moment.fixed"
                @click="chooseMoment(moment.id)"
              >
                <span class="mark" :class="{ filled: moment.fixed || moment.optional || !!slots[moment.id] }" aria-hidden="true"></span>
                <span class="moment-name" :class="{ waiting: !moment.fixed && !moment.optional && !slots[moment.id] }">{{ moment.label }}</span>
                <span class="moment-book">
                  <Transition name="moment-swap">
                    <span v-if="rowSong(moment)?.bookId" :key="rowSong(moment).id" class="book book-badge">{{ rowSong(moment).bookId }}</span>
                    <span v-else key="empty" class="book wait">—</span>
                  </Transition>
                </span>
                <span class="moment-song-slot">
                  <Transition name="moment-swap">
                    <span v-if="rowSong(moment)" :key="rowSong(moment).id" class="moment-song picked">{{ rowSong(moment).title }}</span>
                    <span v-else-if="moment.optional" key="rezado" class="moment-song">Rezado</span>
                    <span v-else key="empty" class="moment-song missing">Por escolher</span>
                  </Transition>
                </span>
              </button>
            </div>
            <span v-if="!moment.fixed && slots[moment.id]" class="choice-tokens">
              <SongShow :song="slots[moment.id]" @change="chooseShow(moment.id, $event)" />
              <VerseTempo
                :song="slots[moment.id]"
                :fallback="boxMark(slots[moment.id])"
                :fallback-label="dayMarkLabel(slots[moment.id])"
                @change="chooseShow(moment.id, $event)"
              />
              <Estrofes
                compact
                :song="slots[moment.id]"
                :mark="chosenMark(slots[moment.id])"
                :open="estrofesOpen === moment.id"
                @open="showEstrofes(moment.id)"
                @close="closeEstrofes"
                @change="setShow(moment.id, $event)"
              />
            </span>
            <button
              v-if="!moment.fixed && slots[moment.id]"
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

    <section class="panel deck-choices">
      <h2>Credo, oração e intervalo entre cânticos</h2>
      <div class="choice-grid">
        <div class="deck-field">
          <span id="credo-label">Credo</span>
          <div class="kind" role="radiogroup" aria-labelledby="credo-label">
            <label>
              <input v-model="credo" type="radio" name="credo" value="longo" />
              <span>Longo</span>
            </label>
            <label>
              <input v-model="credo" type="radio" name="credo" value="breve" />
              <span>Breve</span>
            </label>
          </div>
        </div>
        <label class="deck-field">
          <span>Resposta da oração universal (opcional)</span>
          <input v-model="oracao" type="text" placeholder="Ouvi-nos, Senhor" aria-label="Resposta da oração universal" />
        </label>
        <div class="deck-field interval-choice">
          <span id="interval-label">Intervalo entre cânticos</span>
          <div class="kind" role="radiogroup" aria-labelledby="interval-label">
            <label>
              <input v-model="interval" type="radio" name="interval" value="vazio" />
              <span>Em branco</span>
            </label>
            <label>
              <input v-model="interval" type="radio" name="interval" value="imagem" />
              <span>Imagem</span>
            </label>
            <label>
              <input v-model="interval" type="radio" name="interval" value="texto" />
              <span>Texto</span>
            </label>
            <label>
              <input v-model="interval" type="radio" name="interval" value="texto+imagem" />
              <span>Texto e imagem</span>
            </label>
          </div>
        </div>
      </div>
      <div v-if="intervalWantsText || intervalWantsImage" class="interval-detail">
        <label v-if="intervalWantsText" class="deck-field">
          <span>Texto</span>
          <input v-model="intervalText" type="text" aria-label="Texto do intervalo" />
        </label>
        <div v-if="intervalWantsImage" class="deck-field">
          <span>Imagem</span>
          <div class="path-row">
            <input :value="intervalImage" type="text" readonly placeholder="Nenhuma imagem escolhida" aria-label="Imagem do intervalo" />
            <button class="browse" type="button" title="Escolher imagem" aria-label="Escolher imagem" @click="chooseImage">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5v-10z" />
              </svg>
            </button>
          </div>
        </div>
      </div>
      <p v-if="intervalMissing" class="interval-missing">{{ intervalMissing }}</p>
    </section>

    <section v-if="liturgy" id="readings" class="panel cover">
      <div class="panel-head">
        <h2>Capa e leituras</h2>
        <button
          v-if="readingsDirty"
          class="text-action"
          type="button"
          @click="revertReadings"
        >
          Repor
        </button>
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
      <p v-if="readingNote" class="reading-note">{{ readingNote }}</p>
      <div class="reading-grid">
        <div class="reading-row">
          <label for="cover-field">Capa</label>
          <div class="reading-fields">
            <select id="cover-field" v-model="cover" @change="onCoverPick">
              <option v-for="item in COVERS" :key="item.id" :value="item.id">{{ item.label }}</option>
            </select>
            <p v-if="coverEffect" class="cover-effect">{{ coverEffect }}</p>
          </div>
        </div>
        <div v-if="liturgy.primeira" class="reading-row">
          <span>Primeira leitura</span>
          <span class="reading-fields pair">
            <input v-model="form.primeiraRef" type="text" placeholder="Referência" aria-label="Referência da primeira leitura" />
            <input v-model="form.primeira" type="text" placeholder="Citação" aria-label="Primeira leitura" />
          </span>
        </div>
        <div class="reading-row">
          <span>Salmo</span>
          <span class="reading-fields">
            <input v-model="form.salmo" type="text" placeholder="Refrão" aria-label="Salmo" />
          </span>
        </div>
        <div v-if="liturgy.segunda" class="reading-row">
          <span>Segunda leitura</span>
          <span class="reading-fields pair">
            <input v-model="form.segundaRef" type="text" placeholder="Referência" aria-label="Referência da segunda leitura" />
            <input v-model="form.segunda" type="text" placeholder="Citação" aria-label="Segunda leitura" />
          </span>
        </div>
        <div v-if="lenten" class="reading-row">
          <span>Aclamação</span>
          <span class="reading-fields">
            <textarea v-model="form.louvor" :rows="louvorRows" placeholder="Citação" aria-label="Aclamação"></textarea>
          </span>
        </div>
        <div class="reading-row">
          <span>Evangelho</span>
          <span class="reading-fields pair">
            <input v-model="form.versiculo" type="text" placeholder="Referência" aria-label="Referência do Evangelho" />
            <input v-model="form.frase" type="text" placeholder="Citação" aria-label="Evangelho" />
          </span>
        </div>
      </div>
    </section>
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
          <button class="primary" type="button" @click="generate(true)">Substituir</button>
        </div>
      </div>
    </div>
  </div>
</template>

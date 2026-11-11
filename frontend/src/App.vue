<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import ChosenSongs from './components/ChosenSongs.vue'
import CoverFields from './components/CoverFields.vue'
import ShapeLine from './components/ShapeLine.vue'
import Eucharist from './components/Eucharist.vue'
import FontNotice from './components/FontNotice.vue'
import Library from './components/Library.vue'
import SongList from './components/SongList.vue'
import { keepEstrofes, occasionFileName, slideChoice, withShow } from './songs.js'

const screen = ref('home')
const shape = ref('missa')
const lastDeck = ref('')
const songsMounted = ref(false)
const eucharistMounted = ref(false)
const libraryMounted = ref(false)
const lectionaryMounted = ref(false)
const eucharistDraft = ref(null)
const hasEucharistDraft = ref(false)
const draftChecked = ref(false)
const openEucharistWhenReady = ref(false)
const continueDraftButton = ref(null)
const library = ref([])
const chosen = ref([])
const query = ref('')
const coverOn = ref(false)
const cover = reactive({
  kind: 'outro',
  season: 'comum',
  ano: 'A',
  domingo: '',
  frase: '',
  versiculo: '',
  layout: 'Capa - Tempo Comum',
  header: '',
})
const output = ref('cânticos.pptx')
const outputEdited = ref(false)
const outputInput = ref(null)
const replaceName = ref('')
const folder = ref('')
const folderEdited = ref(false)
const savedPath = ref('')
const error = ref('')
const fileOpen = ref('')
const busy = ref(false)
const loadError = ref('')
const missingFonts = ref([])
const indexPath = ref('')
const indexMissing = ref(false)
const libraryMode = ref('')
const libraryFolder = ref('')
const libraryNote = ref('')
const libraryBusy = ref(false)
const libraryAction = ref('')
const libraryKnown = ref(false)
const libraryLoading = ref(false)
const localLibraryButton = ref(null)
const retryLibraryButton = ref(null)
const importDirectory = ref('')
const slidesOpen = ref(false)
const slidesKind = ref('songs')
const slidesName = ref('cânticos.pptx')
const slidesInput = ref(null)
const slidesError = ref('')
const slidesFileOpen = ref('')
const slidesReplace = ref('')
const syncChoice = ref('local')
const draftFolder = ref('')
const pendingLocal = ref(false)
const libraryFolderInput = ref(null)
const slidesFolderInput = ref(null)
const importCancel = ref(null)
const localCancel = ref(null)
const libraryReady = ref(false)

const coverReady = computed(() => Boolean(cover.layout))

const generateReason = computed(() => {
  const reasons = []
  if (chosen.value.length === 0) {
    reasons.push('Escolha pelo menos um cântico.')
  }
  if (!output.value.trim()) {
    reasons.push('Indique o nome do ficheiro.')
  }
  if (coverOn.value && !coverReady.value) {
    reasons.push('Escolha o tema da capa.')
  }
  return reasons.join(' ')
})

const canGenerate = computed(() => !generateReason.value && missingFonts.value.length === 0)

const chosenIds = computed(() => chosen.value.map((song) => song.id))

const folderLeaf = computed(() => propsFolderLeaf(folder.value))

const libraryPlaceLine = computed(() => {
  if (libraryMode.value === 'folder' && libraryFolder.value.trim()) {
    const leaf = propsFolderLeaf(libraryFolder.value)
    return leaf ? `Na pasta ${leaf}.` : 'Numa pasta partilhada.'
  }
  return 'Neste computador.'
})

const folderChange = computed(() => {
  const draft = draftFolder.value.trim()
  if (!draft) {
    return false
  }
  return libraryMode.value !== 'folder' || !samePath(draft, libraryFolder.value)
})

const pendingLocalText = computed(() => {
  const move = 'Passar os cânticos e a liturgia para este computador?'
  return chosen.value.length ? `${move} A lista de cânticos em curso fica vazia.` : move
})

const libraryMissingText = computed(() => {
  const place = libraryFolder.value.trim()
  const where = place ? `em ${place}` : 'no sítio guardado'
  return `Os cânticos e a liturgia já não estão ${where}. Escolha de novo onde ficam.`
})

const libraryStatusText = computed(() => {
  if (libraryAction.value === 'local') return 'A guardar os cânticos neste computador.'
  if (libraryAction.value === 'folder') return 'A escolher a pasta.'
  if (libraryLoading.value) return 'A tentar outra vez.'
  return ''
})

function readableError(error, fallback) {
  if (!(error instanceof Error) || error instanceof TypeError || error instanceof SyntaxError) {
    return fallback
  }
  const message = error.message.trim()
  if (!message || /^failed to fetch$/i.test(message) || /^networkerror/i.test(message)) {
    return fallback
  }
  return message
}

function propsFolderLeaf(path) {
  const parts = path.split(/[/\\]/).filter(Boolean)
  return parts.at(-1) || ''
}

function samePath(left, right) {
  const norm = (value) => value.trim().replace(/[\\/]+$/, '').replaceAll('/', '\\').toLowerCase()
  return norm(left) !== '' && norm(left) === norm(right)
}

function revealPathEnd(input) {
  if (input) {
    input.scrollLeft = input.scrollWidth
  }
}

function stayedNote(where, hadSongs) {
  const base = `Os cânticos e a liturgia ficam ${where}.`
  return hadSongs ? `${base} A lista de cânticos em curso ficou vazia.` : base
}

const savedName = computed(() => {
  const path = savedPath.value
  const name = path.split(/[/\\]/).pop()
  return name || output.value.trim()
})

const draftPrompt = ref(false)
const openingDraft = ref(false)

const draftTitle = computed(() => {
  const name = eucharistDraft.value?.form?.domingo
  return typeof name === 'string' && name.trim() ? name.trim() : 'Missa'
})

watch(draftPrompt, (open) => {
  if (open) {
    nextTick(() => continueDraftButton.value?.focus())
  }
})

watch(importDirectory, (directory) => {
  if (directory) {
    nextTick(() => importCancel.value?.focus())
  }
})

watch(pendingLocal, (open) => {
  if (open) {
    nextTick(() => localCancel.value?.focus())
  }
})

watch([draftFolder, syncChoice], () => {
  nextTick(revealOpenPaths)
})

watch(folder, () => {
  nextTick(revealOpenPaths)
})

watch([coverOn, () => cover.domingo], () => {
  if (outputEdited.value) {
    return
  }
  output.value = coverOn.value && cover.domingo.trim()
    ? occasionFileName(cover.domingo)
    : 'cânticos.pptx'
})

function dismissSaved() {
  savedPath.value = ''
}

function showEucharist() {
  eucharistMounted.value = true
  songsMounted.value = true
  lastDeck.value = 'eucharist'
  screen.value = 'eucharist'
}

function openEucharist() {
  if (openingDraft.value) {
    return
  }
  if (!draftChecked.value) {
    openEucharistWhenReady.value = true
    return
  }
  if (eucharistDraft.value && !eucharistMounted.value) {
    draftPrompt.value = true
    return
  }
  showEucharist()
}

function chooseShape(next) {
  shape.value = next
  if (screen.value !== 'eucharist') {
    openEucharist()
  }
}

function openLibrary() {
  libraryMounted.value = true
  screen.value = 'library'
}

function openLectionary() {
  lectionaryMounted.value = true
  screen.value = 'lectionary'
}

function openConfig() {
  screen.value = 'config'
}

watch(shape, (value) => {
  if (value === 'lista') {
    songsMounted.value = true
  }
})

watch([libraryReady, indexPath, libraryKnown], ([ready, path, known]) => {
  if (!ready) return
  nextTick(() => {
    if (!known) retryLibraryButton.value?.focus()
    else if (!path) localLibraryButton.value?.focus()
  })
})

watch([libraryReady, indexPath, draftChecked], () => {
  if (!libraryReady.value || !draftChecked.value || !indexPath.value) {
    return
  }
  if (screen.value !== 'home' && screen.value !== 'decks') {
    return
  }
  openEucharist()
})

function continueDraft() {
  draftPrompt.value = false
  openingDraft.value = true
  eucharistMounted.value = true
  lastDeck.value = 'eucharist'
}

function onEucharistRestored() {
  if (!openingDraft.value) {
    return
  }
  openingDraft.value = false
  screen.value = 'eucharist'
}

watch(screen, (value) => {
  if (openingDraft.value && value !== 'home' && value !== 'decks') {
    openingDraft.value = false
  }
})

async function discardDraft() {
  draftPrompt.value = false
  eucharistDraft.value = null
  hasEucharistDraft.value = false
  showEucharist()
  try {
    await fetch('/api/eucharist-draft', { method: 'DELETE' })
  } catch {
    // The fresh screen clears the file again if it is still there.
  }
}

async function loadEucharistDraft() {
  try {
    const response = await fetch('/api/eucharist-draft')
    if (!response.ok) {
      return
    }
    const payload = await response.json()
    if (!payload.date) {
      return
    }
    eucharistDraft.value = payload
    hasEucharistDraft.value = true
  } catch {
    // Home opens without the draft note when the file cannot be read.
  } finally {
    draftChecked.value = true
    if (openEucharistWhenReady.value) {
      openEucharistWhenReady.value = false
      openEucharist()
    }
  }
}

function onEucharistDraft(saved) {
  hasEucharistDraft.value = saved === true
}

function onKeydown(event) {
  if (event.key !== 'Escape') {
    return
  }
  if (savedPath.value) {
    dismissSaved()
  }
}

onMounted(() => {
  loadLibrary()
  loadFonts()
  loadEucharistDraft()
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', revealOpenPaths)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', revealOpenPaths)
})

function revealOpenPaths() {
  revealPathEnd(libraryFolderInput.value)
  revealPathEnd(slidesFolderInput.value)
}

async function loadLibrary() {
  if (libraryLoading.value) return
  libraryLoading.value = true
  loadError.value = ''
  try {
    const response = await fetch('/api/library')
    if (!response.ok) {
      throw new Error('Não foi possível saber onde ficam os cânticos. Tente outra vez.')
    }
    const payload = await response.json()
    applyLibrary(payload)
    libraryKnown.value = true
    if (payload.index) {
      await loadSongs()
    }
  } catch (e) {
    libraryKnown.value = false
    loadError.value = readableError(e, 'Não foi possível saber onde ficam os cânticos. Tente outra vez.')
  } finally {
    libraryLoading.value = false
    libraryReady.value = true
  }
}

async function loadSongs() {
  try {
    const response = await fetch('/api/songs')
    if (!response.ok) {
      throw new Error(await errorText(response))
    }
    library.value = await response.json()
    const byId = new Map(library.value.map((song) => [song.id, song]))
    chosen.value = chosen.value.flatMap((song) => {
      const next = byId.get(song.id)
      return next ? [withShow(next, song)] : []
    })
  } catch (e) {
    loadError.value = e.message
  }
}

async function loadFonts() {
  try {
    const response = await fetch('/api/fonts')
    if (!response.ok) {
      return
    }
    const payload = await response.json()
    missingFonts.value = Array.isArray(payload.missing) ? payload.missing : []
  } catch {
    // Generating still checks the fonts when this request fails.
  }
}

function add(song) {
  if (!song.hasVerses || chosenIds.value.includes(song.id)) {
    return
  }
  chosen.value = [...chosen.value, withShow(song)]
}

function showSong(index, patch) {
  const song = chosen.value[index]
  if (!song) return
  const next = { ...song, ...patch }
  if ((patch.showLyrics && patch.showLyrics !== song.showLyrics)
      || Object.prototype.hasOwnProperty.call(patch, 'showTempo')
      || Object.prototype.hasOwnProperty.call(patch, 'showSunday')) {
    next.showEstrofes = keepEstrofes(next, song.showEstrofes)
  }
  const copy = chosen.value.slice()
  copy[index] = next
  chosen.value = copy
}

function removeSong(id) {
  chosen.value = chosen.value.filter((song) => song.id !== id)
}

function remove(index) {
  chosen.value = chosen.value.filter((_, i) => i !== index)
}

function clearChosen() {
  chosen.value = []
}

function move(index, delta) {
  const next = index + delta
  if (next < 0 || next >= chosen.value.length) {
    return
  }
  const copy = [...chosen.value]
  const [song] = copy.splice(index, 1)
  copy.splice(next, 0, song)
  chosen.value = copy
}

function editOutput(value) {
  outputEdited.value = true
  output.value = value
}

function applyLibrary(payload) {
  indexMissing.value = payload.missing === true
  libraryMode.value = payload.mode || ''
  libraryFolder.value = payload.folder || ''
  syncChoice.value = payload.mode === 'folder' ? 'folder' : 'local'
  draftFolder.value = libraryFolder.value
  applyOutputFolder(payload)
  if (payload.index) {
    indexPath.value = payload.index
    indexMissing.value = false
  }
}

async function useLocalLibrary() {
  if (libraryBusy.value) return
  error.value = ''
  libraryNote.value = ''
  libraryAction.value = 'local'
  libraryBusy.value = true
  try {
    const response = await fetch('/api/library', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode: 'local' }),
    })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível guardar os cânticos e a liturgia neste computador. Tente outra vez.')
    }
    applyLibrary(payload)
    chosen.value = []
    loadError.value = ''
    await loadSongs()
  } catch (e) {
    error.value = readableError(e, 'Não foi possível guardar os cânticos e a liturgia neste computador. Tente outra vez.')
  } finally {
    libraryBusy.value = false
    libraryAction.value = ''
  }
}

async function chooseLibraryFolder() {
  if (libraryBusy.value) return
  error.value = ''
  libraryNote.value = ''
  libraryAction.value = 'folder'
  libraryBusy.value = true
  try {
    const response = await fetch('/api/choose-library-folder', { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível escolher a pasta. Tente outra vez.')
    }
    if (payload.cancelled || !payload.index) {
      return
    }
    applyLibrary(payload)
    chosen.value = []
    loadError.value = ''
    await loadSongs()
  } catch (e) {
    error.value = readableError(e, 'Não foi possível escolher a pasta. Tente outra vez.')
  } finally {
    libraryBusy.value = false
    libraryAction.value = ''
  }
}

async function chooseSync(mode) {
  if (libraryBusy.value) {
    return
  }
  if (mode === 'folder') {
    syncChoice.value = 'folder'
    draftFolder.value = libraryFolder.value || draftFolder.value
    return
  }
  if (libraryMode.value !== 'local') {
    pendingLocal.value = true
    return
  }
  syncChoice.value = 'local'
}

async function confirmLocal() {
  const hadSongs = chosen.value.length > 0
  await useLocalLibrary()
  pendingLocal.value = false
  if (!error.value && libraryMode.value === 'local') {
    libraryNote.value = stayedNote('neste computador', hadSongs)
  }
}

function cancelLocal() {
  pendingLocal.value = false
}

async function pickSyncFolder() {
  error.value = ''
  libraryNote.value = ''
  try {
    const response = await fetch('/api/choose-directory', { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível escolher a pasta. Tente outra vez.')
    }
    if (payload.directory) {
      draftFolder.value = payload.directory
    }
  } catch (e) {
    error.value = readableError(e, 'Não foi possível escolher a pasta. Tente outra vez.')
  }
}

async function applySyncFolder() {
  if (!folderChange.value || libraryBusy.value) {
    return
  }
  const hadSongs = chosen.value.length > 0
  const where = draftFolder.value.trim()
  error.value = ''
  libraryNote.value = ''
  libraryAction.value = 'apply'
  libraryBusy.value = true
  try {
    const response = await fetch('/api/library', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode: 'folder', directory: draftFolder.value }),
    })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível usar essa pasta. Escolha outra, ou tente outra vez.')
    }
    applyLibrary(payload)
    chosen.value = []
    loadError.value = ''
    await loadSongs()
    libraryNote.value = stayedNote(`em ${where}`, hadSongs)
  } catch (e) {
    error.value = readableError(e, 'Não foi possível usar essa pasta. Escolha outra, ou tente outra vez.')
  } finally {
    libraryBusy.value = false
    libraryAction.value = ''
  }
}

async function chooseImportFolder() {
  if (libraryBusy.value) {
    return
  }
  error.value = ''
  libraryNote.value = ''
  libraryBusy.value = true
  try {
    const response = await fetch('/api/choose-import-folder', { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível escolher a pasta. Tente outra vez.')
    }
    if (payload.directory) {
      importDirectory.value = payload.directory
    }
  } catch (e) {
    error.value = readableError(e, 'Não foi possível escolher a pasta. Tente outra vez.')
  } finally {
    libraryBusy.value = false
  }
}

function cancelImport() {
  importDirectory.value = ''
}

async function confirmImport() {
  const directory = importDirectory.value
  const hadSongs = chosen.value.length > 0
  importDirectory.value = ''
  error.value = ''
  libraryNote.value = ''
  try {
    const response = await fetch('/api/import-library', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ directory }),
    })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível substituir os cânticos e a liturgia. Tente outra vez.')
    }
    applyLibrary(payload)
    chosen.value = []
    loadError.value = ''
    await loadSongs()
    const replaced = `Os cânticos e a liturgia foram substituídos pelos ficheiros de ${directory}.`
    libraryNote.value = hadSongs ? `${replaced} A lista de cânticos em curso ficou vazia.` : replaced
  } catch (e) {
    error.value = readableError(e, 'Não foi possível substituir os cânticos e a liturgia. Tente outra vez.')
  }
}

const slidesTitle = computed(() =>
  slidesKind.value === 'covers' ? 'Exportar todas as capas' : 'Exportar todos os cânticos')

async function openSlides() {
  await openExport('songs', 'cânticos.pptx')
}

async function openCovers() {
  await openExport('covers', 'capas.pptx')
}

async function openExport(kind, name) {
  slidesKind.value = kind
  slidesName.value = name
  slidesError.value = ''
  slidesFileOpen.value = ''
  slidesReplace.value = ''
  slidesOpen.value = true
  await nextTick()
  slidesInput.value?.focus()
  slidesInput.value?.select()
}

function closeSlides() {
  slidesOpen.value = false
  slidesReplace.value = ''
  slidesFileOpen.value = ''
}

async function chooseSlidesFolder() {
  folderEdited.value = true
  slidesError.value = ''
  try {
    const response = await fetch('/api/choose-folder', { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível escolher a pasta.')
    }
    if (payload.directory) {
      folder.value = payload.directory
    }
  } catch (e) {
    slidesError.value = e instanceof Error ? e.message : 'Não foi possível escolher a pasta.'
  }
}

async function exportSlides(replace = false) {
  slidesError.value = ''
  slidesFileOpen.value = ''
  savedPath.value = ''
  slidesReplace.value = ''
  const name = slidesName.value.trim()
  if (!name) {
    return
  }
  busy.value = true
  const body = { output: name }
  if (folder.value) {
    body.directory = folder.value
  }
  if (replace === true) {
    body.replace = true
  }
  try {
    const covers = slidesKind.value === 'covers'
    const response = await fetch(covers ? '/api/export-covers' : '/api/export-slides', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const payload = await response.json()
    if (payload.exists) {
      slidesReplace.value = payload.name || name
      return
    }
    if (!response.ok) {
      throw new Error(payload.error || (covers
        ? 'Não foi possível exportar as capas.'
        : 'Não foi possível exportar os cânticos.'))
    }
    slidesOpen.value = false
    slidesReplace.value = ''
    savedPath.value = payload.output
  } catch (e) {
    const message = e instanceof Error ? e.message : 'Não foi possível exportar os cânticos.'
    if (message.includes('ficheiro está aberto')) {
      slidesFileOpen.value = message
    } else if (!(missingFonts.value.length && message.includes('Install the missing font'))) {
      slidesError.value = message
    }
  } finally {
    busy.value = false
  }
}

async function exportLibrary() {
  if (libraryBusy.value) {
    return
  }
  error.value = ''
  libraryNote.value = ''
  libraryBusy.value = true
  try {
    const response = await fetch('/api/choose-export-folder', { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível copiar os cânticos e a liturgia. Tente outra vez.')
    }
    if (payload.directory) {
      libraryNote.value = `Os cânticos e a liturgia foram copiados para ${payload.directory}.`
    }
  } catch (e) {
    error.value = readableError(e, 'Não foi possível copiar os cânticos e a liturgia. Tente outra vez.')
  } finally {
    libraryBusy.value = false
  }
}

function applyOutputFolder(payload) {
  if (!folderEdited.value && payload.outputDirectory) {
    folder.value = payload.outputDirectory
  }
}

async function chooseFolder() {
  folderEdited.value = true
  error.value = ''
  try {
    const response = await fetch('/api/choose-folder', { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível escolher a pasta.')
    }
    if (payload.directory) {
      folder.value = payload.directory
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Não foi possível escolher a pasta.'
  }
}

async function openSaved(what) {
  error.value = ''
  try {
    const response = await fetch('/api/open', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path: savedPath.value, what }),
    })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível abrir.')
    }
  } catch (e) {
    error.value = e.message
  }
}

async function pickAnotherName() {
  replaceName.value = ''
  await nextTick()
  outputInput.value?.focus()
  outputInput.value?.select()
}

async function generate(replace = false) {
  error.value = ''
  fileOpen.value = ''
  savedPath.value = ''
  replaceName.value = ''
  busy.value = true
  const body = {
    ids: chosen.value.map((song) => {
      const choice = slideChoice(song, { sendTempo: true })
      return Object.keys(choice).length ? { id: song.id, ...choice } : song.id
    }),
    output: output.value.trim(),
  }
  if (folder.value) {
    body.directory = folder.value
  }
  if (replace === true) {
    body.replace = true
  }
  if (coverOn.value) {
    body.cover = {
      kind: 'outro',
      layout: cover.layout,
      domingo: cover.domingo.trim(),
      header: cover.header.trim(),
      frase: cover.frase.trim(),
      versiculo: cover.versiculo.trim(),
    }
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
    savedPath.value = payload.output
  } catch (e) {
    const message = e instanceof Error ? e.message : 'Não foi possível gerar.'
    if (message.includes('ficheiro está aberto')) {
      fileOpen.value = message
    } else if (!(missingFonts.value.length && message.includes('Install the missing font'))) {
      error.value = message
    }
  } finally {
    busy.value = false
  }
}

async function errorText(response) {
  try {
    const payload = await response.json()
    return payload.error || 'Não foi possível ler os cânticos.'
  } catch {
    return 'Não foi possível ler os cânticos.'
  }
}
</script>

<template>
  <div class="app">
  <nav v-if="indexPath" class="topbar" aria-label="Secções">
    <span class="brand">eCoral</span>
    <button type="button" class="nav-link" :class="{ current: screen === 'eucharist' }" :aria-current="screen === 'eucharist' ? 'page' : undefined" @click="openEucharist">Apresentação</button>
    <button type="button" class="nav-link" :class="{ current: screen === 'library' }" :aria-current="screen === 'library' ? 'page' : undefined" @click="openLibrary">Cancioneiro</button>
    <button type="button" class="nav-link" :class="{ current: screen === 'lectionary' }" :aria-current="screen === 'lectionary' ? 'page' : undefined" @click="openLectionary">Leccionário</button>
    <button type="button" class="nav-cog" :class="{ current: screen === 'config' }" :aria-current="screen === 'config' ? 'page' : undefined" title="Configuração" aria-label="Configuração" @click="openConfig">
      <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="3" />
        <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 0 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 0 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 0 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 0 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" />
      </svg>
    </button>
  </nav>
  <div class="app-body">
  <main v-if="!libraryReady" class="welcome">
    <p role="status">A abrir…</p>
  </main>
  <main v-else-if="!libraryKnown" class="welcome" :aria-busy="libraryLoading || undefined">
    <h1>Onde ficam os cânticos</h1>
    <p class="welcome-alert" role="alert">{{ loadError || 'Não foi possível saber onde ficam os cânticos. Tente outra vez.' }}</p>
    <button ref="retryLibraryButton" class="primary welcome-primary" type="button" :disabled="libraryLoading" @click="loadLibrary">
      <span v-if="libraryLoading" class="spinner" aria-hidden="true"></span>
      {{ libraryLoading ? 'A tentar…' : 'Tentar outra vez' }}
    </button>
    <p v-if="libraryStatusText" class="welcome-status" role="status">{{ libraryStatusText }}</p>
  </main>
  <main v-else-if="!indexPath" class="welcome" :aria-busy="libraryBusy || undefined">
    <h1>Onde ficam os cânticos</h1>
    <p v-if="indexMissing" class="welcome-alert" role="alert">{{ libraryMissingText }}</p>
    <p v-if="loadError" class="welcome-alert" role="alert">{{ loadError }}</p>
    <p>O eCoral já vem com os cânticos e a liturgia de alguns Domingos e solenidades.</p>
    <div class="welcome-actions">
      <button ref="localLibraryButton" class="primary welcome-primary" type="button" :disabled="libraryBusy" @click="useLocalLibrary">
        <span v-if="libraryAction === 'local'" class="spinner" aria-hidden="true"></span>
        {{ libraryAction === 'local' ? 'A guardar…' : 'Neste computador' }}
      </button>
      <div class="welcome-folder">
        <button class="welcome-secondary" type="button" :disabled="libraryBusy" aria-describedby="library-folder-note" @click="chooseLibraryFolder">
          {{ libraryAction === 'folder' ? 'A escolher…' : 'Numa pasta partilhada' }}
        </button>
        <p id="library-folder-note" class="welcome-aside">Os cânticos e a liturgia que já estejam nessa pasta mantêm-se. Fechar a janela volta aqui.</p>
      </div>
    </div>
    <p v-if="error" class="welcome-alert" role="alert">{{ error }}</p>
    <p class="welcome-later">Pode mudar isto mais tarde, em Configuração.</p>
    <p v-if="libraryStatusText" class="welcome-status" role="status">{{ libraryStatusText }}</p>
  </main>
  <main v-else-if="indexPath && screen === 'config'" class="page config-page">
    <header class="config-header">
      <h1>Configuração</h1>
    </header>
    <section class="panel">
      <div class="config-fields">
        <h2 class="config-heading">Onde ficam os cânticos e a liturgia</h2>
        <p class="config-place">{{ libraryPlaceLine }}</p>
        <p v-if="libraryMode === 'folder' && libraryFolder" class="config-path">{{ libraryFolder }}</p>
        <p class="config-hint">A pasta partilhada, por exemplo na Dropbox, é a mesma para quem prepara noutro computador.</p>
        <div class="kind">
          <label>
            <input type="radio" name="sync" value="local" :checked="syncChoice === 'local'" :disabled="libraryBusy" @change="chooseSync('local')" />
            <span>Neste computador</span>
          </label>
          <label>
            <input type="radio" name="sync" value="folder" :checked="syncChoice === 'folder'" :disabled="libraryBusy" @change="chooseSync('folder')" />
            <span>Numa pasta partilhada</span>
          </label>
        </div>
        <div v-if="syncChoice === 'folder'" class="field">
          <label for="library-folder">Pasta partilhada</label>
          <div class="sync-apply">
            <div class="path-row">
              <input id="library-folder" ref="libraryFolderInput" :value="draftFolder" :title="draftFolder || undefined" type="text" readonly placeholder="Nenhuma pasta escolhida" />
              <button class="browse" type="button" title="Escolher pasta" aria-label="Escolher pasta" :disabled="libraryBusy" @click="pickSyncFolder">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5v-10z" />
                </svg>
              </button>
            </div>
            <button class="primary" type="button" :disabled="!folderChange || libraryBusy" :aria-describedby="folderChange ? 'apply-note' : undefined" @click="applySyncFolder">
              <span v-if="libraryAction === 'apply'" class="spinner" aria-hidden="true"></span>
              {{ libraryAction === 'apply' ? 'A guardar…' : 'Aplicar' }}
            </button>
          </div>
          <p v-if="folderChange" id="apply-note" class="config-hint">Aplicar passa a usar esta pasta. A lista de cânticos em curso fica vazia.</p>
          <p v-else-if="draftFolder.trim()" class="config-hint">Esta pasta já está em uso.</p>
          <p v-else class="config-hint">Escolha a pasta. Aplicar passa a usá-la, e a lista de cânticos em curso fica vazia.</p>
        </div>
        <h3 class="config-heading">Cópia</h3>
        <p class="config-hint">Substituir troca os cânticos e a liturgia em uso. A cópia deixa-os como estão.</p>
        <div class="config-actions">
          <button type="button" :disabled="libraryBusy" @click="exportLibrary">Exportar uma cópia</button>
          <button type="button" class="text-action" :disabled="libraryBusy" @click="chooseImportFolder">Substituir</button>
        </div>
        <details class="config-more">
          <summary class="config-heading">Apresentações de referência</summary>
          <div class="config-deck">
            <div>
              <p class="config-hint">Os cânticos tal como estão configurados, numa apresentação.</p>
              <button type="button" :disabled="libraryBusy" @click="openSlides">Exportar todos os cânticos</button>
            </div>
            <div>
              <p class="config-hint">Cada capa, com um texto de exemplo, numa apresentação.</p>
              <button type="button" :disabled="libraryBusy" @click="openCovers">Exportar todas as capas</button>
            </div>
          </div>
        </details>
      </div>
      <p class="status" :class="{ 'is-empty': !libraryNote }" role="status">{{ libraryNote }}</p>
      <p class="status error" :class="{ 'is-empty': !error }" role="alert">{{ error }}</p>
    </section>
    <div v-if="slidesOpen" class="modal-backdrop" @click.self="closeSlides">
      <div class="modal slides-export" role="dialog" aria-modal="true" aria-labelledby="export-slides" @keydown.esc="closeSlides">
        <button class="icon close" type="button" aria-label="Fechar" @click="closeSlides">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>
        <h2 id="export-slides">{{ slidesTitle }}</h2>
        <div class="export-fields">
          <label class="field">
            <span>Ficheiro</span>
            <input ref="slidesInput" v-model="slidesName" type="text" />
          </label>
          <div class="field">
            <label for="slides-folder">Pasta da apresentação</label>
            <div class="path-row">
              <input id="slides-folder" ref="slidesFolderInput" :value="folder" :title="folder || undefined" type="text" readonly placeholder="Nenhuma pasta escolhida" />
              <button class="browse" type="button" title="Escolher pasta" aria-label="Escolher pasta" @click="chooseSlidesFolder">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5v-10z" />
                </svg>
              </button>
            </div>
          </div>
          <button class="primary" type="button" :disabled="!slidesName.trim() || busy || missingFonts.length > 0" @click="exportSlides()">
            <span v-if="busy" class="spinner" aria-hidden="true"></span>
            {{ busy ? 'A gerar…' : 'Exportar' }}
          </button>
        </div>
        <FontNotice :fonts="missingFonts" />
        <p v-if="slidesError" class="status error" role="alert">{{ slidesError }}</p>
      </div>
    </div>
    <div v-if="slidesFileOpen" class="modal-backdrop" @click.self="slidesFileOpen = ''">
      <div class="modal confirm" role="dialog" aria-modal="true" aria-labelledby="slides-file-open">
        <p id="slides-file-open" class="file-open">{{ slidesFileOpen }}</p>
        <div class="open-actions">
          <button class="primary" type="button" @click="slidesFileOpen = ''">Fechar</button>
        </div>
      </div>
    </div>
    <div v-if="slidesReplace" class="modal-backdrop" @click.self="slidesReplace = ''">
      <div class="modal confirm" role="dialog" aria-modal="true" aria-labelledby="replace-slides">
        <p id="replace-slides">Já existe um ficheiro com o nome {{ slidesReplace }}. Substituí-lo apaga o que lá está.</p>
        <div class="open-actions">
          <button type="button" @click="slidesReplace = ''; nextTick(() => { slidesInput?.focus(); slidesInput?.select() })">Escolher outro nome</button>
          <button class="primary" type="button" @click="exportSlides(true)">Substituir</button>
        </div>
      </div>
    </div>
    <div v-if="importDirectory" class="modal-backdrop" @click.self="cancelImport">
      <div class="modal confirm" role="dialog" aria-modal="true" aria-labelledby="import-library" @keydown.esc="cancelImport">
        <p id="import-library">Substituir os cânticos e a liturgia em uso pelos ficheiros de {{ importDirectory }}? Esta troca não se desfaz.</p>
        <div class="open-actions">
          <button ref="importCancel" type="button" @click="cancelImport">Cancelar</button>
          <button class="primary" type="button" @click="confirmImport">Substituir</button>
        </div>
      </div>
    </div>
    <div v-if="pendingLocal" class="modal-backdrop" @click.self="cancelLocal">
      <div class="modal confirm" role="dialog" aria-modal="true" aria-labelledby="move-local" @keydown.esc="cancelLocal">
        <p id="move-local">{{ pendingLocalText }}</p>
        <div class="open-actions">
          <button ref="localCancel" type="button" :disabled="libraryBusy" @click="cancelLocal">Cancelar</button>
          <button class="primary" type="button" :disabled="libraryBusy" @click="confirmLocal">
            <span v-if="libraryAction === 'local'" class="spinner" aria-hidden="true"></span>
            {{ libraryAction === 'local' ? 'A guardar…' : 'Neste computador' }}
          </button>
        </div>
      </div>
    </div>
  </main>
  <Library
    v-if="indexPath && libraryMounted"
    v-show="screen === 'library'"
    :songs="library"
    @saved="loadSongs"
  />
  <Library
    v-if="indexPath && lectionaryMounted"
    v-show="screen === 'lectionary'"
    mode="lectionary"
    :songs="library"
  />
  <main v-if="indexPath && (eucharistMounted || songsMounted)" v-show="screen === 'eucharist'" class="page">
    <Eucharist
      v-if="eucharistMounted"
      v-show="shape === 'missa'"
      :songs="library"
      :folder="folder"
      :draft="eucharistDraft"
      :shape="shape"
      :fonts="missingFonts"
      @choose="chooseShape"
      @choose-folder="chooseFolder"
      @generated="savedPath = $event"
      @draft="onEucharistDraft"
      @restored="onEucharistRestored"
    />
    <div v-if="songsMounted" v-show="shape === 'lista'" class="deck">
      <header class="deck-head">
        <h1>Lista de cânticos</h1>
        <ShapeLine :shape="shape" group="lista-shape" @choose="chooseShape" />
      </header>
      <p v-if="loadError" class="status error">{{ loadError }}</p>
      <section v-else class="panel export">
        <div class="export-fields">
          <label class="field">
            <span>Ficheiro</span>
            <input ref="outputInput" :value="output" type="text" @input="editOutput($event.target.value)" />
          </label>
          <div class="field">
            <span>Pasta</span>
            <div class="path-row">
              <input :value="folderLeaf" :title="folder" type="text" readonly placeholder="Nenhuma pasta escolhida" />
              <button
                class="browse"
                type="button"
                title="Escolher pasta"
                aria-label="Escolher pasta"
                @click="chooseFolder"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5v-10z" />
                </svg>
              </button>
            </div>
          </div>
          <div class="generate">
            <span class="generate-hit" :class="{ blocked: generateReason }">
              <button class="primary" type="button" :disabled="!canGenerate || busy" :aria-describedby="generateReason ? 'lista-generate-reason' : undefined" @click="generate">
                <span v-if="busy" class="spinner" aria-hidden="true"></span>
                {{ busy ? 'A gerar…' : 'Gerar' }}
              </button>
              <p v-if="generateReason" id="lista-generate-reason" class="generate-hint" role="tooltip">{{ generateReason }}</p>
            </span>
          </div>
        </div>
        <p v-if="folder" class="path-full">{{ folder }}</p>
        <FontNotice :fonts="missingFonts" />
        <p v-if="error && !savedPath" class="status error">{{ error }}</p>
      </section>
      <div v-if="!loadError" class="columns list-columns">
        <ChosenSongs :songs="chosen" @remove="remove" @up="move($event, -1)" @down="move($event, 1)" @clear="clearChosen" @show="showSong" />
        <SongList
          class="song-rail"
          heading="Pesquisar"
          :songs="library"
          :query="query"
          :chosen-ids="chosenIds"
          @update:query="query = $event"
          @add="add"
          @remove-song="removeSong"
        />
      </div>
      <CoverFields v-if="!loadError" v-model:enabled="coverOn" :cover="cover" />
    </div>
  </main>
  <div v-if="draftPrompt" class="modal-backdrop">
    <div class="modal confirm" role="dialog" aria-modal="true" aria-labelledby="draft-prompt">
      <p id="draft-prompt">Há um rascunho de {{ draftTitle }} por continuar. Quer retomá-lo?</p>
      <div class="open-actions">
        <button type="button" @click="discardDraft">Começar de novo</button>
        <button ref="continueDraftButton" class="primary" type="button" @click="continueDraft">Continuar</button>
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
          <button class="primary" type="button" @click="generate(true)">Substituir</button>
        </div>
      </div>
    </div>
    <div v-if="savedPath" class="modal-backdrop" @click.self="dismissSaved">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="export-done">
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
        <p id="export-done">O ficheiro {{ savedName }} foi criado.</p>
        <div class="open-actions">
          <button type="button" @click="openSaved('folder')">Abrir localização</button>
          <button type="button" @click="openSaved('file')">Abrir ficheiro</button>
        </div>
        <p v-if="error" class="status error">{{ error }}</p>
      </div>
    </div>
  </div>
  </div>
</template>


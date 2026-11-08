<script setup>
import { computed } from 'vue'
import { byBookId, matches } from '../songs.js'

const props = defineProps({
  songs: { type: Array, required: true },
  query: { type: String, required: true },
  chosenIds: { type: Array, required: true },
  heading: { type: String, default: 'Lista de cânticos' },
  actionLabel: { type: String, default: 'Adicionar' },
  requireBookId: { type: Boolean, default: false },
})

const emit = defineEmits(['update:query', 'add', 'remove-song'])

function picked(song) {
  return props.chosenIds.includes(song.id)
}

function unavailable(song) {
  return !song.hasVerses || (props.requireBookId && !song.bookId)
}

function toggle(song) {
  if (unavailable(song)) {
    return
  }
  if (picked(song)) {
    emit('remove-song', song.id)
  } else {
    emit('add', song)
  }
}

const visible = computed(() =>
  props.songs.filter((song) => matches(song, props.query)).sort(byBookId))
</script>

<template>
  <section class="panel">
    <h2>{{ heading }}</h2>
    <input
      class="search"
      type="search"
      placeholder="Título ou número"
      :value="query"
      @input="emit('update:query', $event.target.value)"
    />
    <ul class="songs">
      <li v-for="song in visible" :key="song.id" :class="{ pickable: !unavailable(song), placed: picked(song) }" @click="toggle(song)">
        <span class="song-label">
          <span class="book-badge">{{ song.bookId || '#' }}</span>
          <span class="song-name">
            {{ song.title }}
            <span class="title-alt">{{ song.titleAlt ? `(${song.titleAlt})` : '' }}</span>
          </span>
        </span>
        <span v-if="!song.hasVerses" class="missing">sem letra</span>
        <button
          type="button"
          class="icon song-toggle"
          :class="{ chosen: picked(song) }"
          :aria-label="picked(song) ? 'Retirar' : actionLabel"
          :title="picked(song) ? 'Retirar' : actionLabel"
          :disabled="unavailable(song)"
          @click.stop="toggle(song)"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
        </button>
      </li>
    </ul>
  </section>
</template>

<script setup>
const props = defineProps({
  fonts: { type: Array, default: () => [] },
})

const one = () => props.fonts.length === 1

function joiner(index) {
  const last = props.fonts.length - 1
  if (index >= last) {
    return ''
  }
  return index === last - 1 ? ' e ' : ', '
}
</script>

<template>
  <div v-if="fonts.length" class="font-notice" role="status">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
      <path stroke-linejoin="round" d="M12 4.2 21.2 20H2.8L12 4.2z" />
      <path stroke-linecap="round" d="M12 10v4.2" />
      <path stroke-linecap="round" d="M12 17.2h.01" />
    </svg>
    <p>
      {{ one() ? 'Falta a fonte ' : 'Faltam as fontes ' }}<template v-for="(font, index) in fonts" :key="font.family"><a :href="font.url" target="_blank" rel="noopener noreferrer">{{ font.family }}</a>{{ joiner(index) }}</template>. {{ one() ? 'Instale-a para ser possível gerar a apresentação.' : 'Instale-as para ser possível gerar a apresentação.' }}
    </p>
  </div>
</template>

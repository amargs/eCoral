<script setup>
import { COVERS } from '../songs.js'

defineProps({
  enabled: { type: Boolean, required: true },
  cover: { type: Object, required: true },
})

const emit = defineEmits(['update:enabled'])
</script>

<template>
  <section class="panel cover">
    <div class="panel-head">
      <h2>Capa</h2>
      <label class="cover-include">
        <span>Incluir</span>
        <input
          class="switch"
          type="checkbox"
          :checked="enabled"
          @change="emit('update:enabled', $event.target.checked)"
        />
      </label>
    </div>
    <fieldset class="fields" :disabled="!enabled">
      <label class="field wide">
        <span>Tema</span>
        <select v-model="cover.layout">
          <option v-for="item in COVERS" :key="item.id" :value="item.id">{{ item.label }}</option>
        </select>
      </label>
      <div class="occasion">
        <label class="field">
          <span>Título</span>
          <input v-model="cover.domingo" type="text" placeholder="Oração de Taizé, Vigília do Crisma..." />
        </label>
        <label class="field">
          <span>Cabeçalho</span>
          <input v-model="cover.header" type="text" placeholder="29/04/2026 - Esgueira" />
        </label>
      </div>
      <label class="field wide">
        <span>Frase</span>
        <input v-model="cover.frase" type="text" placeholder="«Vinde a mim, todos os que estais cansados»" />
      </label>
      <label class="field wide">
        <span>Versículo</span>
        <input v-model="cover.versiculo" type="text" placeholder="Mt 11, 28" />
      </label>
    </fieldset>
  </section>
</template>

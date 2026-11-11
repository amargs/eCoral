<script setup>
defineProps({
  shape: { type: String, required: true },
  group: { type: String, required: true },
})

const emit = defineEmits(['choose'])
</script>

<template>
  <div class="shape-switch">
    <span class="shape-name" :id="`${group}-label`">Modo de apresentação</span>
    <div class="shape-line" role="radiogroup" :aria-labelledby="`${group}-label`">
      <label>
        <input type="radio" :name="group" value="missa" :checked="shape === 'missa'" @change="emit('choose', 'missa')" />
        <span>Eucaristia</span>
      </label>
      <label>
        <input type="radio" :name="group" value="lista" :checked="shape === 'lista'" @change="emit('choose', 'lista')" />
        <span>Lista de cânticos</span>
      </label>
    </div>
  </div>
</template>

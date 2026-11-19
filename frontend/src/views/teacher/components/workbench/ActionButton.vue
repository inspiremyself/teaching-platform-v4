<script setup lang="ts">
import type { ActionTone } from './types'

withDefaults(
  defineProps<{ label: string; tone: ActionTone; disabled?: boolean }>(),
  { disabled: false },
)
const emit = defineEmits<{ click: [] }>()
</script>

<template>
  <button
    class="action-button"
    :class="`action-button--${tone}`"
    type="button"
    :disabled="disabled"
    @click="emit('click')"
  >
    {{ label }}
  </button>
</template>

<script setup lang="ts">
import type { ActionTone } from './types'

withDefaults(
  defineProps<{ label: string; tone: ActionTone; disabled?: boolean; size?: 'default' | 'roomy' }>(),
  { disabled: false, size: 'default' },
)
const emit = defineEmits<{ click: [] }>()
</script>

<template>
  <button
    class="action-button"
    :class="[`action-button--${tone}`, { 'action-button--roomy': size === 'roomy' }]"
    type="button"
    :disabled="disabled"
    @click="emit('click')"
  >
    {{ label }}
  </button>
</template>

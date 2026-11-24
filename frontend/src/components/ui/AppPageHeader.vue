<template>
  <div class="page-header">
    <div class="page-header__main">
      <slot name="title"><h2>{{ title }}</h2></slot>
      <slot name="description"><p v-if="description">{{ description }}</p></slot>
      <div v-if="$slots.meta" class="page-header__meta"><slot name="meta" /></div>
    </div>
    <div v-if="$slots.actions" class="page-header__actions"><slot name="actions" /></div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string;
  description?: string;
}>();
</script>

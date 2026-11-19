<script setup lang="ts">
import './workbench.css'

defineProps<{ eyebrow: string; title: string; description: string }>()
defineSlots<{
  actions(): any
  meta(): any
  toolbar(): any
  default(): any
}>()
</script>

<template>
  <div class="workbench-page workbench-shell">
    <header class="workbench-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="workbench-eyebrow">{{ eyebrow }}</div>
          <h1>{{ title }}</h1>
          <p>{{ description }}</p>
        </div>
        <div class="workbench-header__actions">
          <slot name="actions" />
        </div>
      </div>
      <div class="workbench-meta">
        <slot name="meta" />
      </div>
    </header>
    <section class="workbench-toolbar">
      <slot name="toolbar" />
    </section>
    <section class="workbench-panel workbench-shell__content">
      <slot />
    </section>
  </div>
</template>

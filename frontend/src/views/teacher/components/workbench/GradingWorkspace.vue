<script setup lang="ts">
import './workbench.css'

defineSlots<{
  headerActions(): any
  queue(): any
  detail(): any
  summary(): any
}>()
</script>

<template>
  <div class="workbench-page grading-workspace workbench-page--locked">
    <header class="workbench-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main"></div>
        <div class="workbench-header__actions">
          <slot name="headerActions" />
        </div>
      </div>
    </header>
    <div class="grading-layout">
      <aside class="grading-layout__queue"><slot name="queue" /></aside>
      <main class="grading-layout__detail"><slot name="detail" /></main>
      <aside class="grading-layout__summary"><slot name="summary" /></aside>
    </div>
  </div>
</template>

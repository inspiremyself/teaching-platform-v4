<template>
  <section class="table-card">
    <header v-if="$slots.header" class="app-table-card__header"><slot name="header" /></header>
    <slot />
  </section>
</template>

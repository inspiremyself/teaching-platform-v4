<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { resolveBreadcrumbTrail } from '@/router/breadcrumb'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()
const items = computed(() =>
  resolveBreadcrumbTrail(route, authStore.user?.role, authStore.homePath),
)
</script>

<template>
  <nav v-if="items.length" class="app-breadcrumb" aria-label="面包屑导航">
    <template v-for="(item, index) in items" :key="`${index}-${item.label}`">
      <span v-if="index > 0" class="app-breadcrumb__separator" aria-hidden="true">/</span>
      <router-link
        v-if="item.to && index < items.length - 1"
        class="app-breadcrumb__link"
        :to="item.to"
      >
        {{ item.label }}
      </router-link>
      <span
        v-else
        class="app-breadcrumb__text"
        :aria-current="index === items.length - 1 ? 'page' : undefined"
      >
        {{ item.label }}
      </span>
    </template>
  </nav>
</template>

<style scoped>
.app-breadcrumb {
  display: flex;
  flex: none;
  align-items: center;
  gap: 6px;
  /* 与 .main-content__shell 同宽并居中，避免宽屏下面包屑与内容列错位 */
  width: 100%;
  max-width: 1380px;
  margin: 0 auto;
  height: 34px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--primary-color, #0f766e);
}

.app-breadcrumb__link {
  color: var(--primary-color, #0f766e);
  text-decoration: none;
}

.app-breadcrumb__link:hover {
  text-decoration: underline;
}

.app-breadcrumb__link:focus-visible {
  outline: 2px solid var(--primary-color, #0f766e);
  outline-offset: 2px;
}

.app-breadcrumb__separator,
.app-breadcrumb__text {
  color: #64748b;
}

.app-breadcrumb__text[aria-current='page'] {
  color: #0f172a;
}

@media (max-width: 899px) {
  .app-breadcrumb {
    display: none;
  }
}
</style>

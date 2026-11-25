<script setup lang="ts">
import './workbench.css'
import ActionButton from './ActionButton.vue'

defineProps<{ modelValue: boolean; title: string }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: []
}>()
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    width="1040px"
    destroy-on-close
    class="task-editor-dialog"
    @update:model-value="emit('update:modelValue', Boolean($event))"
  >
    <div class="task-editor-dialog__shell">
      <header class="task-editor-dialog__header">
        <div>
          <div class="task-editor-dialog__badges"><slot name="badges" /></div>
          <h2>{{ title }}</h2>
          <div class="task-editor-dialog__summary"><slot name="summary" /></div>
        </div>
      </header>
      <div class="task-editor-dialog__body">
        <div class="task-editor-dialog__main">
          <slot name="form" />
          <slot name="questions" />
          <slot />
        </div>
        <aside class="task-editor-dialog__aside"><slot name="aside" /></aside>
      </div>
      <footer class="task-editor-dialog__footer">
        <slot name="footer">
          <ActionButton label="取消" tone="ghost" @click="emit('update:modelValue', false)" />
          <ActionButton label="保存" tone="primary" @click="emit('submit')" />
        </slot>
      </footer>
    </div>
  </el-dialog>
</template>

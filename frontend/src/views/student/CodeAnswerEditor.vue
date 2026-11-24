<template>
  <div
    class="code-editor-shell"
    :class="{
      'is-readonly': disabled,
      'is-focused': isFocused,
      'has-content': hasContent,
    }"
  >
    <div class="code-editor-shell__toolbar">
      <div class="code-editor-shell__toolbar-left">
        <span class="code-editor-shell__traffic">
          <i></i>
          <i></i>
          <i></i>
        </span>
        <span class="code-editor-shell__title">代码编辑区</span>
        <span class="code-editor-shell__status" :class="`code-editor-shell__status--${editorStatusTone}`">{{ editorStatusText }}</span>
      </div>
      <div class="code-editor-shell__toolbar-right">
        <span v-if="disabled" class="code-editor-shell__readonly">只读</span>
        <span class="code-editor-shell__language">{{ normalizedLanguage }}</span>
        <span class="code-editor-shell__count">{{ lineCountLabel }} · {{ characterCount }}</span>
      </div>
    </div>

    <div class="code-editor-shell__body">
      <div class="code-editor-shell__gutter" aria-hidden="true">
        <div class="code-editor-shell__line-numbers" :style="lineNumberStyle">
          <span
            v-for="line in lineNumbers"
            :key="line"
            :class="{ 'is-active': line === currentLine }"
          >
            {{ line }}
          </span>
        </div>
      </div>
      <textarea
        ref="textareaRef"
        :value="modelValue"
        :disabled="disabled"
        :placeholder="placeholder"
        :rows="rows"
        :maxlength="maxlength"
        class="code-editor-shell__textarea"
        spellcheck="false"
        @input="handleInput"
        @keydown="handleKeydown"
        @scroll="handleScroll"
        @focus="handleFocus"
        @blur="handleBlur"
        @click="syncCursorState"
        @keyup="syncCursorState"
        @mouseup="syncCursorState"
      ></textarea>
    </div>

    <div class="code-editor-shell__footer">
      <span>{{ footerHint }}</span>
      <span>{{ footerStatus }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

const props = withDefaults(
  defineProps<{
    modelValue: string;
    language?: string;
    disabled?: boolean;
    placeholder?: string;
    rows?: number;
    maxlength?: number;
  }>(),
  {
    language: 'CODE',
    disabled: false,
    placeholder: '',
    rows: 16,
    maxlength: 20000,
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();

const textareaRef = ref<HTMLTextAreaElement | null>(null);
const scrollTop = ref(0);
const isFocused = ref(false);
const selectionStart = ref(0);

const normalizedLanguage = computed(() => String(props.language || 'CODE').trim().toUpperCase());
const characterCount = computed(() => `${props.modelValue.length}/${props.maxlength}`);
const hasContent = computed(() => props.modelValue.trim().length > 0);
const lineNumbers = computed(() => {
  const total = Math.max(1, props.modelValue.split(/\r?\n/).length, props.rows);
  return Array.from({ length: total }, (_, index) => index + 1);
});
const currentLine = computed(() => {
  const cursor = Math.max(0, Math.min(selectionStart.value, props.modelValue.length));
  return props.modelValue.slice(0, cursor).split(/\r?\n/).length;
});
const editorStatusText = computed(() => {
  if (props.disabled) return '查看模式';
  if (isFocused.value) return '正在编辑';
  if (hasContent.value) return '已输入内容';
  return '待输入';
});
const editorStatusTone = computed(() => {
  if (props.disabled) return 'readonly';
  if (isFocused.value) return 'editing';
  if (hasContent.value) return 'filled';
  return 'idle';
});
const lineCountLabel = computed(() => `${lineNumbers.value.length} 行`);
const footerHint = computed(() => {
  if (props.disabled) {
    return '当前答案已锁定为只读，可继续查看代码结构与缩进。';
  }
  return props.placeholder || '请在此输入代码答案，支持保留换行与缩进。';
});
const footerStatus = computed(() => {
  if (props.disabled) {
    return `第 ${currentLine.value} 行 · 不可编辑`;
  }
  return `第 ${currentLine.value} 行 · Tab 插入 2 个空格`;
});

const lineNumberStyle = computed(() => ({ transform: `translateY(-${scrollTop.value}px)` })); // 动态值：行号列随代码区滚动偏移

const handleInput = (event: Event) => {
  const target = event.target as HTMLTextAreaElement;
  selectionStart.value = target.selectionStart;
  emit('update:modelValue', target.value);
};

const handleScroll = (event: Event) => {
  scrollTop.value = (event.target as HTMLTextAreaElement).scrollTop;
};

const handleFocus = (event: FocusEvent) => {
  isFocused.value = true;
  selectionStart.value = (event.target as HTMLTextAreaElement).selectionStart;
};

const handleBlur = (event: FocusEvent) => {
  isFocused.value = false;
  selectionStart.value = (event.target as HTMLTextAreaElement).selectionStart;
};

const syncCursorState = (event?: Event) => {
  const target = (event?.target as HTMLTextAreaElement | undefined) ?? textareaRef.value;
  if (!target) {
    return;
  }
  selectionStart.value = target.selectionStart;
};

const handleKeydown = (event: KeyboardEvent) => {
  if (props.disabled || event.key !== 'Tab') {
    return;
  }

  event.preventDefault();
  const target = event.target as HTMLTextAreaElement;
  const start = target.selectionStart;
  const end = target.selectionEnd;
  selectionStart.value = start + 2;
  const nextValue = `${props.modelValue.slice(0, start)}  ${props.modelValue.slice(end)}`;
  emit('update:modelValue', nextValue);

  requestAnimationFrame(() => {
    const textarea = textareaRef.value;
    if (!textarea) {
      return;
    }
    textarea.focus();
    textarea.setSelectionRange(start + 2, start + 2);
  });
};
</script>

<style scoped>
.code-editor-shell {
  overflow: hidden;
  border: 1px solid #1e293b;
  border-radius: 16px;
  background: #0f172a;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, opacity 0.2s ease;
}

.code-editor-shell.is-readonly {
  border-color: #334155;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03), 0 0 0 1px rgba(148, 163, 184, 0.08);
}

.code-editor-shell.is-focused {
  border-color: #60a5fa;
  box-shadow: 0 0 0 1px rgba(96, 165, 250, 0.24), 0 18px 36px rgba(15, 23, 42, 0.18);
}

.code-editor-shell__toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.96), rgba(15, 23, 42, 0.96));
}

.code-editor-shell__toolbar-left,
.code-editor-shell__toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.code-editor-shell__traffic {
  display: inline-flex;
  gap: 6px;
}

.code-editor-shell__traffic i {
  width: 10px;
  height: 10px;
  display: block;
  border-radius: 999px;
}

.code-editor-shell__traffic i:nth-child(1) {
  background: #f87171;
}

.code-editor-shell__traffic i:nth-child(2) {
  background: #fbbf24;
}

.code-editor-shell__traffic i:nth-child(3) {
  background: #4ade80;
}

.code-editor-shell__title,
.code-editor-shell__count {
  color: #94a3b8;
  font-size: var(--font-size-sm);
}

.code-editor-shell__status {
  padding: 4px 8px;
  border-radius: 999px;
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.code-editor-shell__status--idle {
  background: rgba(148, 163, 184, 0.14);
  color: #cbd5e1;
}

.code-editor-shell__status--editing {
  background: rgba(59, 130, 246, 0.16);
  color: #bfdbfe;
}

.code-editor-shell__status--filled {
  background: rgba(16, 185, 129, 0.16);
  color: #a7f3d0;
}

.code-editor-shell__status--readonly {
  background: rgba(148, 163, 184, 0.16);
  color: #e2e8f0;
}

.code-editor-shell__readonly {
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.16);
  color: #cbd5e1;
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.code-editor-shell__language {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.16);
  color: #bfdbfe;
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.code-editor-shell__body {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
}

.code-editor-shell__gutter {
  overflow: hidden;
  border-right: 1px solid rgba(148, 163, 184, 0.12);
  background: rgba(15, 23, 42, 0.95);
}

.code-editor-shell__line-numbers {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 16px 8px 16px 0;
  background: rgba(15, 23, 42, 0.95);
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, SFMono, Menlo, Consolas, monospace;
  font-size: var(--font-size-base);
  line-height: var(--line-height-body);
  text-align: right;
  user-select: none;
  transition: transform 0.05s linear;
}

.code-editor-shell__line-numbers span {
  display: block;
  border-radius: 8px;
  padding-right: 4px;
}

.code-editor-shell__line-numbers span.is-active {
  color: #f8fafc;
  background: rgba(59, 130, 246, 0.18);
}

.code-editor-shell__textarea {
  width: 100%;
  min-height: 320px;
  padding: 16px;
  border: none;
  resize: vertical;
  outline: none;
  background: transparent;
  color: #e2e8f0;
  font-family: ui-monospace, SFMono-Regular, SFMono, Menlo, Consolas, monospace;
  font-size: var(--font-size-base);
  line-height: var(--line-height-body);
  white-space: pre;
  tab-size: 2;
}

.code-editor-shell__textarea:focus {
  background: rgba(15, 23, 42, 0.55);
}

.code-editor-shell__textarea::placeholder {
  color: #64748b;
}

.code-editor-shell__textarea:disabled {
  color: #cbd5e1;
  cursor: not-allowed;
  background: rgba(15, 23, 42, 0.35);
}

.code-editor-shell__textarea:disabled::placeholder {
  color: #475569;
}

.code-editor-shell__footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  color: #64748b;
  font-size: var(--font-size-xs);
  background: rgba(15, 23, 42, 0.92);
}

@media (max-width: 768px) {
  .code-editor-shell__toolbar,
  .code-editor-shell__footer {
    flex-direction: column;
    align-items: flex-start;
  }

  .code-editor-shell__toolbar-left,
  .code-editor-shell__toolbar-right {
    flex-wrap: wrap;
  }

  .code-editor-shell__body {
    grid-template-columns: 42px minmax(0, 1fr);
  }

  .code-editor-shell__line-numbers {
    padding-right: 6px;
  }
}
</style>

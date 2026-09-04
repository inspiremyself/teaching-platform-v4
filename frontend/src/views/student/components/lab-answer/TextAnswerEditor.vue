<template>
  <div class="text-answer-editor">
    <el-input
      :model-value="currentText"
      type="textarea"
      :rows="rows"
      :maxlength="maxlength"
      show-word-limit
      :disabled="disabled"
      :placeholder="placeholder"
      @update:model-value="updateText"
    />

    <div v-if="supportsImages" class="text-answer-editor__images">
      <div class="text-answer-editor__images-header">
        <span>答题截图（最多 5 张）</span>
        <span v-if="compressionHint" class="text-answer-editor__compression-hint">{{ compressionHint }}</span>
      </div>

      <div v-if="currentImages.length" class="text-answer-editor__thumb-list">
        <div
          v-for="(image, index) in currentImages"
          :key="image.path"
          class="text-answer-editor__thumb"
        >
          <img :src="previewUrlOf(image.path)" :alt="image.name" />
          <button
            v-if="!disabled"
            type="button"
            class="text-answer-editor__remove"
            @click="removeImage(index)"
          >
            删除
          </button>
          <span v-if="image.compressed" class="text-answer-editor__badge">已压缩</span>
        </div>
      </div>

      <div v-if="!disabled && currentImages.length < 5" class="text-answer-editor__upload-row">
        <input
          ref="fileInputRef"
          class="text-answer-editor__file-input"
          type="file"
          accept="image/png,image/jpeg,image/webp"
          @change="handleFileChange"
        />
        <el-button :loading="uploading" @click="openFilePicker">添加图片</el-button>
        <span class="text-answer-editor__upload-tip">支持 png / jpeg / webp，单张不超过 1MB</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { uploadStudentLabAnswerImage } from '@/api/labs';
import { fetchLabAnswerImageBlobUrl, revokeLabAnswerImageBlobUrl } from '@/utils/labAnswerImage';
import { compressLabAnswerImage, toLabAnswerImageMeta } from './compressLabAnswerImage';
import type { LabAnswerDraft, LabAnswerImageDraftMeta } from './types';

const props = withDefaults(defineProps<{
  modelValue: LabAnswerDraft | null;
  labId?: number | string;
  stepId?: number | string;
  enableImages?: boolean;
  disabled?: boolean;
  placeholder?: string;
  rows?: number;
  maxlength?: number;
}>(), {
  enableImages: false,
  disabled: false,
  placeholder: '',
  rows: 8,
  maxlength: 5000,
});

const emit = defineEmits<{
  'update:modelValue': [value: LabAnswerDraft];
  'images-removed': [];
}>();

const fileInputRef = ref<HTMLInputElement | null>(null);
const uploading = ref(false);
const compressionHint = ref('');
const localPreviewUrls = reactive<Record<string, string>>({});
const remotePreviewUrls = reactive<Record<string, string>>({});

const currentText = computed(() => (props.modelValue?.kind === 'text' ? props.modelValue.text : ''));
const currentImages = computed(() => (props.modelValue?.kind === 'text' ? props.modelValue.images : []));
const supportsImages = computed(() => props.enableImages && props.modelValue?.kind === 'text');

const previewUrlOf = (path: string) => localPreviewUrls[path] || remotePreviewUrls[path] || '';

const emitDraft = (text: string, images: LabAnswerImageDraftMeta[]) => {
  emit('update:modelValue', {
    kind: 'text',
    text,
    images,
  });
};

const updateText = (value: string | number) => {
  emitDraft(String(value ?? ''), currentImages.value);
};

const revokePreviewUrl = (url?: string) => {
  revokeLabAnswerImageBlobUrl(url);
};

const clearRemotePreviewUrls = () => {
  Object.values(remotePreviewUrls).forEach(revokePreviewUrl);
  Object.keys(remotePreviewUrls).forEach((key) => {
    delete remotePreviewUrls[key];
  });
};

const clearLocalPreviewUrls = (pathsToKeep: Set<string> = new Set()) => {
  Object.entries(localPreviewUrls).forEach(([path, url]) => {
    if (!pathsToKeep.has(path)) {
      revokePreviewUrl(url);
      delete localPreviewUrls[path];
    }
  });
};

const loadRemotePreviewUrls = async (images: LabAnswerImageDraftMeta[]) => {
  clearRemotePreviewUrls();
  for (const image of images) {
    if (!image.path || localPreviewUrls[image.path]) {
      continue;
    }
    try {
      remotePreviewUrls[image.path] = await fetchLabAnswerImageBlobUrl(image.path);
    } catch {
      remotePreviewUrls[image.path] = '';
    }
  }
};

watch(currentImages, (images) => {
  const activePaths = new Set(images.map(image => image.path).filter(Boolean));
  clearLocalPreviewUrls(activePaths);
  void loadRemotePreviewUrls(images);
}, { immediate: true, deep: true });

const openFilePicker = () => {
  fileInputRef.value?.click();
};

const removeImage = (index: number) => {
  const removed = currentImages.value[index];
  const nextImages = currentImages.value.filter((_, imageIndex) => imageIndex !== index);
  if (removed?.path) {
    revokePreviewUrl(localPreviewUrls[removed.path]);
    revokePreviewUrl(remotePreviewUrls[removed.path]);
    delete localPreviewUrls[removed.path];
    delete remotePreviewUrls[removed.path];
  }
  emitDraft(currentText.value, nextImages);
  emit('images-removed');
};

const handleFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';

  if (!file) {
    return;
  }
  if (!props.labId || !props.stepId) {
    ElMessage.error('缺少实验题项信息，无法上传图片');
    return;
  }
  if (currentImages.value.length >= 5) {
    ElMessage.warning('单题最多上传 5 张图片');
    return;
  }

  uploading.value = true;
  compressionHint.value = '';
  try {
    const compressed = await compressLabAnswerImage(file);
    const uploaded = await uploadStudentLabAnswerImage(props.labId, props.stepId, compressed.file);
    const imageMeta = toLabAnswerImageMeta(uploaded, {
      compressed: compressed.compressed,
      originalSize: compressed.originalSize,
    });
    localPreviewUrls[imageMeta.path] = URL.createObjectURL(compressed.file);
    emitDraft(currentText.value, [...currentImages.value, imageMeta]);
    if (compressed.compressed) {
      compressionHint.value = '已自动压缩部分图片以符合 1MB 限制';
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '图片上传失败');
  } finally {
    uploading.value = false;
  }
};

onBeforeUnmount(() => {
  clearLocalPreviewUrls();
  clearRemotePreviewUrls();
});
</script>

<style scoped>
.text-answer-editor__images {
  margin-top: 14px;
}

.text-answer-editor__images-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
}

.text-answer-editor__compression-hint {
  color: #0f766e;
  font-size: 12px;
  font-weight: 500;
}

.text-answer-editor__thumb-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}

.text-answer-editor__thumb {
  position: relative;
  width: 112px;
}

.text-answer-editor__thumb img {
  width: 112px;
  height: 112px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.text-answer-editor__remove {
  position: absolute;
  top: 6px;
  right: 6px;
  border: none;
  border-radius: 999px;
  padding: 2px 8px;
  background: rgba(15, 23, 42, 0.72);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}

.text-answer-editor__badge {
  display: inline-block;
  margin-top: 6px;
  color: #0f766e;
  font-size: 12px;
}

.text-answer-editor__upload-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.text-answer-editor__file-input {
  display: none;
}

.text-answer-editor__upload-tip {
  color: #64748b;
  font-size: 12px;
}
</style>

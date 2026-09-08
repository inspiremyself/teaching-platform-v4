<template>
  <div v-if="images.length" class="lab-answer-image-gallery">
    <figure
      v-for="image in images"
      :key="image.path"
      class="lab-answer-image-gallery__item"
    >
      <img
        :src="previewUrls[image.path] || ''"
        :alt="image.name"
        class="lab-answer-image-gallery__image"
        @click="handleThumbClick(image.path)"
      />
      <figcaption class="lab-answer-image-gallery__caption">{{ image.name }}</figcaption>
    </figure>
  </div>

  <el-image-viewer
    v-if="viewerVisible && viewerUrlList.length"
    teleported
    :url-list="viewerUrlList"
    :initial-index="viewerInitialIndex"
    @close="closePreview"
  />
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, watch } from 'vue';
import type { LabAnswerImageMeta } from '@/types/lab';
import { fetchLabAnswerImageBlobUrl, revokeLabAnswerImageBlobUrl } from '@/utils/labAnswerImage';
import { useLabAnswerImageViewer } from './useLabAnswerImageViewer';

const props = defineProps<{
  images: LabAnswerImageMeta[];
}>();

const previewUrls = reactive<Record<string, string>>({});
let previewLoadGeneration = 0;
const {
  viewerVisible,
  viewerUrlList,
  viewerInitialIndex,
  openPreview,
  closePreview,
} = useLabAnswerImageViewer();

const revokeAll = () => {
  Object.values(previewUrls).forEach(revokeLabAnswerImageBlobUrl);
  Object.keys(previewUrls).forEach((key) => {
    delete previewUrls[key];
  });
};

const cachePreviewUrl = (path: string, fetchedUrl: string) => {
  const existing = previewUrls[path];
  if (existing) {
    revokeLabAnswerImageBlobUrl(fetchedUrl);
    return existing;
  }
  previewUrls[path] = fetchedUrl;
  return fetchedUrl;
};

const imagePathKey = computed(() => props.images.map((item) => item.path).join('\0'));

const handleThumbClick = async (path: string) => {
  const pathKeyAtClick = imagePathKey.value;
  const orderedPathsAtClick = props.images.map((item) => item.path);

  if (!previewUrls[path]) {
    let fetchedUrl = '';
    try {
      fetchedUrl = await fetchLabAnswerImageBlobUrl(path);
    } catch {
      if (imagePathKey.value === pathKeyAtClick && !previewUrls[path]) {
        previewUrls[path] = '';
      }
      return;
    }

    if (imagePathKey.value !== pathKeyAtClick) {
      revokeLabAnswerImageBlobUrl(fetchedUrl);
      return;
    }

    cachePreviewUrl(path, fetchedUrl);
  }

  if (imagePathKey.value !== pathKeyAtClick) {
    return;
  }

  void openPreview(
    path,
    orderedPathsAtClick,
    previewUrls,
    () => imagePathKey.value !== pathKeyAtClick,
  );
};

const loadPreviews = async (images: LabAnswerImageMeta[], generation: number) => {
  const activePaths = new Set(images.map((image) => image.path).filter(Boolean));

  for (const path of Object.keys(previewUrls)) {
    if (!activePaths.has(path)) {
      revokeLabAnswerImageBlobUrl(previewUrls[path]);
      delete previewUrls[path];
    }
  }

  for (const image of images) {
    if (!image.path || previewUrls[image.path]) {
      continue;
    }
    try {
      const fetchedUrl = await fetchLabAnswerImageBlobUrl(image.path);
      if (generation !== previewLoadGeneration) {
        revokeLabAnswerImageBlobUrl(fetchedUrl);
        return;
      }
      cachePreviewUrl(image.path, fetchedUrl);
    } catch {
      if (generation !== previewLoadGeneration) {
        return;
      }
      if (!previewUrls[image.path]) {
        previewUrls[image.path] = '';
      }
    }
  }
};

watch(imagePathKey, (_pathKey, previousPathKey) => {
  if (previousPathKey !== undefined) {
    closePreview();
  }
  const generation = ++previewLoadGeneration;
  void loadPreviews(props.images ?? [], generation);
}, { immediate: true });

onBeforeUnmount(() => {
  closePreview();
  revokeAll();
});
</script>

<style scoped>
.lab-answer-image-gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}

.lab-answer-image-gallery__item {
  width: 120px;
  margin: 0;
}

.lab-answer-image-gallery__image {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  cursor: zoom-in;
  background: #fff;
}

.lab-answer-image-gallery__caption {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
  word-break: break-all;
}
</style>

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
        @click="openPreview(image.path)"
      />
      <figcaption class="lab-answer-image-gallery__caption">{{ image.name }}</figcaption>
    </figure>
  </div>

  <el-image-viewer
    v-if="viewerVisible && viewerUrl"
    :url-list="[viewerUrl]"
    @close="closePreview"
  />
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue';
import type { LabAnswerImageMeta } from '@/types/lab';
import { fetchLabAnswerImageBlobUrl, revokeLabAnswerImageBlobUrl } from '@/utils/labAnswerImage';

const props = defineProps<{
  images: LabAnswerImageMeta[];
}>();

const previewUrls = reactive<Record<string, string>>({});
const viewerVisible = ref(false);
const viewerUrl = ref('');

const revokeAll = () => {
  Object.values(previewUrls).forEach(revokeLabAnswerImageBlobUrl);
  Object.keys(previewUrls).forEach((key) => {
    delete previewUrls[key];
  });
};

const loadPreviews = async (images: LabAnswerImageMeta[]) => {
  revokeAll();
  for (const image of images) {
    if (!image.path) {
      continue;
    }
    try {
      previewUrls[image.path] = await fetchLabAnswerImageBlobUrl(image.path);
    } catch {
      previewUrls[image.path] = '';
    }
  }
};

const openPreview = (path: string) => {
  const url = previewUrls[path];
  if (!url) {
    return;
  }
  viewerUrl.value = url;
  viewerVisible.value = true;
};

const closePreview = () => {
  viewerVisible.value = false;
  viewerUrl.value = '';
};

watch(
  () => props.images,
  (images) => {
    void loadPreviews(images ?? []);
  },
  { immediate: true, deep: true },
);

onBeforeUnmount(() => {
  revokeAll();
  closePreview();
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

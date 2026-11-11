<template>
  <div class="content-preview">
    <p
      v-for="(paragraph, paragraphIndex) in paragraphs"
      :key="paragraphIndex"
      :class="['content-preview__paragraph', `content-preview__paragraph--${paragraph.class}`]"
    >
      <template v-for="(sentence, sentenceIndex) in paragraph.sentences" :key="sentenceIndex">
        <span class="content-preview__sentence">{{ sentence.text }}</span>
        <img
          v-if="imageUrls[sentence.image]"
          class="content-preview__image"
          :src="imageUrls[sentence.image]"
          alt="课件插图"
        />
      </template>
    </p>
    <p v-if="paragraphs.length === 0" class="content-preview__empty">本小节暂无正文</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';

interface SentencePayload {
  text: string;
  narration: string;
  image: string;
  audio: string;
}

interface ParagraphPayload {
  class: string;
  sentences: SentencePayload[];
}

const props = withDefaults(
  defineProps<{
    contentJson: string;
    readAsset?: (path: string) => Promise<string>;
  }>(),
  { readAsset: undefined },
);

const paragraphs = computed<ParagraphPayload[]>(() => {
  try {
    const parsed = JSON.parse(props.contentJson) as { paragraphs?: ParagraphPayload[] };
    return Array.isArray(parsed.paragraphs) ? parsed.paragraphs : [];
  } catch {
    return [];
  }
});

const imageUrlByPath = ref<Record<string, string>>({});
const objectUrls = ref<string[]>([]);

const imageUrls = computed<Record<string, string>>(() => {
  const urls: Record<string, string> = {};
  const byPath = imageUrlByPath.value;
  for (const paragraph of paragraphs.value) {
    for (const sentence of paragraph.sentences) {
      if (sentence.image) {
        urls[sentence.image] = byPath[sentence.image] ?? '';
      }
    }
  }
  return urls;
});

const loadImages = async () => {
  const paths = new Set<string>();
  for (const paragraph of paragraphs.value) {
    for (const sentence of paragraph.sentences) {
      if (sentence.image) {
        paths.add(sentence.image);
      }
    }
  }
  const next: Record<string, string> = {};
  for (const path of paths) {
    if (!props.readAsset) {
      continue;
    }
    try {
      next[path] = await props.readAsset(path);
    } catch {
      next[path] = '';
    }
  }
  releaseUrls();
  imageUrlByPath.value = next;
  objectUrls.value = Object.values(next).filter((url) => url !== '');
};

const releaseUrls = () => {
  for (const url of objectUrls.value) {
    URL.revokeObjectURL(url);
  }
  objectUrls.value = [];
};

watch(
  () => props.contentJson,
  () => {
    void loadImages();
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  releaseUrls();
});
</script>

<style scoped>
.content-preview__paragraph {
  margin: 0 0 12px;
  line-height: 1.8;
}

.content-preview__paragraph--tip {
  padding-left: 10px;
  border-left: 3px solid #e6a23c;
}

.content-preview__paragraph--example {
  padding-left: 10px;
  border-left: 3px solid #409eff;
}

.content-preview__paragraph--note {
  color: #6b7785;
}

.content-preview__sentence {
  margin-right: 6px;
}

.content-preview__image {
  display: block;
  max-width: 100%;
  margin: 8px 0;
}

.content-preview__empty {
  color: #909399;
}
</style>

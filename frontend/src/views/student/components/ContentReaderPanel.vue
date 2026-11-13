<template>
  <div class="page content-reader">
    <div class="content-reader__layout">
      <aside class="content-reader__sidebar">
        <h3 class="content-reader__heading">学习任务</h3>
        <el-empty v-if="reader.groups.value.length === 0" description="暂无布置的学习内容" />
        <el-card
          v-for="group in reader.groups.value"
          :key="group.assignmentGroupId"
          class="content-reader__card"
          shadow="never"
        >
          <template #header>
            <span class="content-reader__card-title">{{ group.coursewareTitle }}</span>
          </template>
          <div v-for="section in group.sections" :key="section.sectionId" class="content-reader__section-row">
            <el-button
              link
              type="primary"
              :class="{ 'content-reader__section-active': isActive(group.coursewareId, section.sectionId) }"
              @click="onSelect(group, section.sectionId)"
            >
              {{ section.heading }}
            </el-button>
          </div>
        </el-card>
      </aside>

      <section class="content-reader__main" v-loading="reader.loading.value">
        <el-empty v-if="!reader.sectionContent.value" description="请选择左侧小节开始阅读" />
        <template v-else>
          <h2 class="content-reader__title">{{ reader.sectionContent.value.heading }}</h2>
          <div ref="readerRef" class="content-reader__math">
            <p
              v-for="(paragraph, paragraphIndex) in paragraphs"
              :key="paragraphIndex"
              :class="['content-reader__paragraph', `content-reader__paragraph--${paragraph.class}`]"
            >
              <template v-for="(sentence, sentenceIndex) in paragraph.sentences" :key="sentenceIndex">
                <span class="content-reader__sentence">{{ sentence.text }}</span>
                <img
                  v-if="imageUrls[sentence.image]"
                  class="content-reader__image"
                  :src="imageUrls[sentence.image]"
                  alt="课件插图"
                />
                <audio
                  v-if="audioUrls[sentence.audio]"
                  controls
                  class="content-reader__audio"
                  :src="audioUrls[sentence.audio]"
                />
              </template>
            </p>
            <p v-if="paragraphs.length === 0" class="content-reader__empty">本小节暂无正文</p>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { parseParagraphs, studentContentKey } from '@/composables/useStudentContent';
import type { StudentAssignmentGroup } from '@/types/studentContent';

const reader = inject(studentContentKey)!;
const readerRef = ref<HTMLElement | null>(null);
const imageUrls = ref<Record<string, string>>({});
const audioUrls = ref<Record<string, string>>({});
const generation = ref(0);

const paragraphs = computed(() => parseParagraphs(reader.sectionContent.value?.contentJson));

const isActive = (coursewareId: number, sectionId: number) => {
  const active = reader.activeSection.value;
  return active?.coursewareId === coursewareId && active.sectionId === sectionId;
};

const onSelect = (group: StudentAssignmentGroup, sectionId: number) => {
  reader.activeGroup.value = group;
  void reader.openSection(group.coursewareId, sectionId);
};

watch(
  () => reader.sectionContent.value,
  async (content) => {
    imageUrls.value = {};
    audioUrls.value = {};
    const snapshot = ++generation.value;
    if (!content) {
      return;
    }
    const imagePaths = new Set<string>();
    const audioPaths = new Set<string>();
    for (const paragraph of parseParagraphs(content.contentJson)) {
      for (const sentence of paragraph.sentences) {
        if (sentence.image) {
          imagePaths.add(sentence.image);
        }
        if (sentence.audio) {
          audioPaths.add(sentence.audio);
        }
      }
    }
    const nextImages: Record<string, string> = {};
    for (const path of imagePaths) {
      nextImages[path] = await reader.readAssetFor(path);
    }
    if (snapshot !== generation.value) {
      return;
    }
    imageUrls.value = nextImages;
    const nextAudios: Record<string, string> = {};
    for (const path of audioPaths) {
      nextAudios[path] = await reader.readAssetFor(path);
    }
    if (snapshot !== generation.value) {
      return;
    }
    audioUrls.value = nextAudios;
    await nextTick();
    if (snapshot !== generation.value) {
      return;
    }
    await reader.typeset(readerRef.value);
  },
);

onBeforeUnmount(() => {
  reader.releaseUrls();
});
</script>

<style scoped>
.content-reader__layout {
  display: flex;
  align-items: flex-start;
  gap: 20px;
}

.content-reader__sidebar {
  width: 300px;
  flex-shrink: 0;
}

.content-reader__heading {
  margin: 0 0 12px;
  font-size: 16px;
}

.content-reader__card {
  margin-bottom: 12px;
}

.content-reader__card-title {
  font-weight: 600;
}

.content-reader__section-row {
  line-height: 2;
}

.content-reader__section-active {
  font-weight: 700;
}

.content-reader__main {
  flex: 1;
  min-width: 0;
  padding: 4px 16px 24px;
}

.content-reader__title {
  margin: 0 0 16px;
  font-size: 20px;
}

.content-reader__math {
  line-height: 1.8;
}

.content-reader__paragraph {
  margin: 0 0 12px;
  line-height: 1.8;
}

.content-reader__paragraph--tip {
  padding-left: 10px;
  border-left: 3px solid #e6a23c;
}

.content-reader__paragraph--example {
  padding-left: 10px;
  border-left: 3px solid #409eff;
}

.content-reader__paragraph--note {
  color: #6b7785;
}

.content-reader__sentence {
  margin-right: 6px;
}

.content-reader__image {
  display: block;
  max-width: 100%;
  margin: 8px 0;
}

.content-reader__audio {
  display: block;
  width: 100%;
  max-width: 420px;
  margin: 8px 0;
}

.content-reader__empty {
  color: #909399;
}
</style>

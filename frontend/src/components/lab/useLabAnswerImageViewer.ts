import { nextTick, ref, unref, type MaybeRef } from 'vue';

export function useLabAnswerImageViewer() {
  const viewerVisible = ref(false);
  const viewerUrlList = ref<string[]>([]);
  const viewerInitialIndex = ref(0);
  let openSession = 0;

  const resolvePreviewState = (
    clickedPath: string,
    orderedPaths: string[],
    urlByPath: MaybeRef<Record<string, string>>,
  ) => {
    const resolvedUrlByPath = unref(urlByPath);
    const loaded = orderedPaths
      .map((path) => ({ path, url: resolvedUrlByPath[path] }))
      .filter((item) => Boolean(item.url));

    const index = loaded.findIndex((item) => item.path === clickedPath);
    if (index < 0) {
      return null;
    }

    return {
      nextUrlList: loaded.map((item) => item.url),
      index,
    };
  };

  const openPreview = async (
    clickedPath: string,
    orderedPaths: string[],
    urlByPath: MaybeRef<Record<string, string>>,
    isStale?: () => boolean,
  ) => {
    const session = ++openSession;
    const previewState = resolvePreviewState(clickedPath, orderedPaths, urlByPath);
    if (!previewState || isStale?.()) {
      return;
    }

    if (viewerVisible.value) {
      viewerVisible.value = false;
      viewerUrlList.value = [];
      viewerInitialIndex.value = 0;
      await nextTick();
    }

    if (session !== openSession || isStale?.()) {
      return;
    }

    const nextPreviewState = resolvePreviewState(clickedPath, orderedPaths, urlByPath);
    if (!nextPreviewState || isStale?.()) {
      return;
    }

    viewerUrlList.value = nextPreviewState.nextUrlList;
    viewerInitialIndex.value = nextPreviewState.index;
    viewerVisible.value = true;
  };

  const closePreview = () => {
    openSession += 1;
    viewerVisible.value = false;
    viewerUrlList.value = [];
    viewerInitialIndex.value = 0;
  };

  return {
    viewerVisible,
    viewerUrlList,
    viewerInitialIndex,
    openPreview,
    closePreview,
  };
};

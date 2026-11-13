import { ref } from 'vue';
import type { InjectionKey } from 'vue';
import { getStudentSection, listStudentAssignments, readStudentAsset } from '@/api/studentContent';
import type { StudentAssignmentGroup, StudentSectionContent } from '@/types/studentContent';

export interface ReaderSentence {
  text: string;
  image: string;
  audio: string;
}

export interface ReaderParagraph {
  class: string;
  sentences: ReaderSentence[];
}

const ALLOWED_CLASSES = new Set(['tip', 'example', 'note']);

/**
 * 正文按 paragraphs → sentences 原顺序解析；公式保留 `\(...\)` 原文，
 * MathJax 脚本加载失败时仍是纯文本，不出现空白段落。
 */
export const parseParagraphs = (contentJson: string | null | undefined): ReaderParagraph[] => {
  if (!contentJson) {
    return [];
  }
  try {
    const parsed = JSON.parse(contentJson) as { paragraphs?: ReaderParagraph[] };
    if (!Array.isArray(parsed.paragraphs)) {
      return [];
    }
    return parsed.paragraphs.map((paragraph) => ({
      class: ALLOWED_CLASSES.has(paragraph?.class) ? paragraph.class : '',
      sentences: Array.isArray(paragraph?.sentences)
        ? paragraph.sentences.map((sentence) => ({
            text: typeof sentence?.text === 'string' ? sentence.text : '',
            image: typeof sentence?.image === 'string' ? sentence.image : '',
            audio: typeof sentence?.audio === 'string' ? sentence.audio : '',
          }))
        : [],
    }));
  } catch {
    return [];
  }
};

let mathJaxPromise: Promise<void> | null = null;

interface MathJaxRuntime {
  startup: { defaultReady: () => void; promise: Promise<unknown> };
  typesetPromise?: (nodes: Element[]) => Promise<void>;
}

/**
 * 自托管 MathJax 3 只插入一次。脚本 onload 时 window.MathJax 仍是配置对象、组件尚未异步装载完毕，
 * 必须等 startup.promise 完成才有 typesetPromise；加载失败或超时仍 resolve，公式保持纯文本可读。
 */
const loadMathJax = (): Promise<void> => {
  if (mathJaxPromise) {
    return mathJaxPromise;
  }
  mathJaxPromise = new Promise<void>((resolve) => {
    (window as unknown as { MathJax?: unknown }).MathJax = {
      tex: { inlineMath: [['\\(', '\\)']] },
      options: { enableMenu: false },
      startup: {
        ready: () => {
          const runtime = (window as unknown as { MathJax: MathJaxRuntime }).MathJax;
          runtime.startup.defaultReady();
          runtime.startup.promise.then(() => resolve(), () => resolve());
        },
      },
    };
    const script = document.createElement('script');
    script.src = '/vendor/mathjax/es5/tex-chtml.js';
    script.async = true;
    script.onload = () => {
      globalThis.setTimeout(resolve, 2000);
    };
    script.onerror = () => resolve();
    document.head.appendChild(script);
  });
  return mathJaxPromise;
};

export const useStudentContent = () => {
  const groups = ref<StudentAssignmentGroup[]>([]);
  const activeGroup = ref<StudentAssignmentGroup | null>(null);
  const activeSection = ref<{ coursewareId: number; sectionId: number } | null>(null);
  const sectionContent = ref<StudentSectionContent | null>(null);
  const loading = ref(false);
  const objectUrls = ref<string[]>([]);
  const generation = ref(0);

  const loadGroups = async () => {
    loading.value = true;
    try {
      groups.value = await listStudentAssignments();
    } finally {
      loading.value = false;
    }
  };

  /** 先逐个 revoke，再清空 URL 列表与正文，避免旧 blob 悬挂到下一小节。 */
  const releaseUrls = () => {
    for (const url of objectUrls.value) {
      URL.revokeObjectURL(url);
    }
    objectUrls.value = [];
    sectionContent.value = null;
  };

  const openSection = async (coursewareId: number, sectionId: number) => {
    releaseUrls();
    const snapshot = ++generation.value;
    loading.value = true;
    try {
      const content = await getStudentSection(coursewareId, sectionId);
      if (snapshot !== generation.value) {
        return;
      }
      sectionContent.value = content;
      activeSection.value = { coursewareId, sectionId };
    } finally {
      if (snapshot === generation.value) {
        loading.value = false;
      }
    }
  };

  const readAssetFor = async (path: string): Promise<string> => {
    const content = sectionContent.value;
    const asset = content?.assets.find((item) => item.filePath === path);
    if (!content || !asset) {
      return '';
    }
    try {
      const buffer = await readStudentAsset(content.coursewareId, asset.id);
      const blob = new Blob([buffer], { type: asset.mime ?? undefined });
      const url = URL.createObjectURL(blob);
      objectUrls.value.push(url);
      return url;
    } catch {
      return '';
    }
  };

  const typeset = async (container?: Element | null) => {
    await loadMathJax();
    const mathJax = (window as unknown as { MathJax?: MathJaxRuntime }).MathJax;
    if (!container || !mathJax?.typesetPromise) {
      return;
    }
    await mathJax.typesetPromise([container]);
  };

  return {
    groups,
    activeGroup,
    activeSection,
    sectionContent,
    loading,
    objectUrls,
    loadGroups,
    openSection,
    readAssetFor,
    typeset,
    releaseUrls,
  };
};

export type StudentContentContext = ReturnType<typeof useStudentContent>;

export const studentContentKey: InjectionKey<StudentContentContext> = Symbol('student-content');

import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const panelSource = readFileSync(
  new URL('../components/ContentReaderPanel.vue', import.meta.url),
  'utf-8',
);

const api = vi.hoisted(() => ({
  listStudentAssignments: vi.fn(),
  getStudentSection: vi.fn(),
  readStudentAsset: vi.fn(),
}));

vi.mock('@/api/studentContent', () => api);

import { parseParagraphs, useStudentContent } from '@/composables/useStudentContent';

const revoked: string[] = [];
const OriginalURL = globalThis.URL;

class FakeURL extends OriginalURL {
  static createObjectURL = vi.fn(() => 'blob:stage4');
  static revokeObjectURL = vi.fn((url: string) => {
    revoked.push(url);
  });
}

const sectionContent = {
  coursewareId: 1,
  sectionId: 2,
  heading: '小节',
  contentJson: '{}',
  assets: [],
};

describe('学生阅读器模板与 composable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    revoked.length = 0;
    vi.stubGlobal('URL', FakeURL);
    vi.stubGlobal('window', { MathJax: undefined });
    vi.stubGlobal('document', {
      createElement: () => ({ src: '', async: false, onload: null, onerror: null }),
      head: {
        appendChild: (script: { onerror?: (() => void) | null }) => {
          script.onerror?.();
        },
      },
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('按 paragraphs 与 sentences 原顺序解析，白名单外的 class 降级', () => {
    const parsed = parseParagraphs(
      JSON.stringify({
        paragraphs: [
          { class: 'tip', sentences: [{ text: '第一段 \\(x^2\\)', image: 'a.png', audio: '' }] },
          { class: 'unknown', sentences: [{ text: '第二段', image: '', audio: 'b.mp3' }] },
        ],
      }),
    );
    expect(parsed.map((paragraph) => paragraph.class)).toEqual(['tip', '']);
    expect(parsed[0].sentences[0].text).toContain('\\(x^2\\)');
    expect(parsed[0].sentences[0].image).toBe('a.png');
    expect(parsed[1].sentences[0].audio).toBe('b.mp3');
  });

  it('正文 JSON 非法时返回空数组，不抛错', () => {
    expect(parseParagraphs('not-json')).toEqual([]);
    expect(parseParagraphs(null)).toEqual([]);
  });

  it('模板含音频播放器、公式容器与三个白名单 class，且无作答元素', () => {
    expect(panelSource).toContain('<audio');
    expect(panelSource).toContain('content-reader__math');
    expect(panelSource).toContain('content-reader__paragraph--tip');
    expect(panelSource).toContain('content-reader__paragraph--example');
    expect(panelSource).toContain('content-reader__paragraph--note');
    expect(panelSource).not.toContain('作答');
    expect(panelSource).not.toContain('提交');
    expect(panelSource).not.toContain('评分');
    expect(panelSource).not.toContain('成绩');
  });

  it('素材路径未命中时返回空串且不发起请求', async () => {
    const reader = useStudentContent();
    reader.sectionContent.value = sectionContent;
    await expect(reader.readAssetFor('missing.png')).resolves.toBe('');
    expect(api.readStudentAsset).not.toHaveBeenCalled();
  });

  it('releaseUrls 先逐个 revoke，再清空 URL 列表与正文', () => {
    const reader = useStudentContent();
    reader.sectionContent.value = sectionContent;
    reader.objectUrls.value = ['blob:a', 'blob:b'];
    reader.releaseUrls();
    expect(revoked).toEqual(['blob:a', 'blob:b']);
    expect(reader.objectUrls.value).toEqual([]);
    expect(reader.sectionContent.value).toBeNull();
  });

  it('MathJax 脚本加载失败不阻塞阅读，公式原文仍以纯文本保留', async () => {
    const reader = useStudentContent();
    await expect(reader.typeset(null)).resolves.toBeUndefined();
    const configured = (window as unknown as { MathJax?: { startup?: { ready?: unknown } } }).MathJax;
    expect(configured?.startup?.ready).toBeTypeOf('function');
    const parsed = parseParagraphs(
      JSON.stringify({
        paragraphs: [{ class: 'note', sentences: [{ text: '公式 \\(a+b\\)' }] }],
      }),
    );
    expect(parsed[0].sentences[0].text).toContain('\\(a+b\\)');
  });

  it('MathJax startup 完成后才调用 typesetPromise', async () => {
    vi.resetModules();
    interface FakeMathJax {
      startup: { ready: () => void; defaultReady: () => void; promise: Promise<unknown> };
      typesetPromise: (nodes: Element[]) => Promise<void>;
    }
    const typesetPromise = vi.fn(async (_nodes: Element[]) => undefined);
    vi.stubGlobal('document', {
      createElement: () => ({ src: '', async: false, onload: null, onerror: null }),
      head: {
        appendChild: () => {
          const runtime = (window as unknown as { MathJax: FakeMathJax }).MathJax;
          runtime.startup.defaultReady = () => {};
          runtime.startup.promise = Promise.resolve();
          runtime.typesetPromise = typesetPromise;
          runtime.startup.ready();
        },
      },
    });
    const { useStudentContent } = await import('@/composables/useStudentContent');
    const reader = useStudentContent();
    await reader.typeset({} as Element);
    expect(typesetPromise).toHaveBeenCalledTimes(1);
  });
});

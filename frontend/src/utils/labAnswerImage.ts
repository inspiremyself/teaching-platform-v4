import { useAuthStore } from '@/stores/auth';

const buildAnswerImageUrl = (path: string) =>
  `/api/v1/labs/answer-images?path=${encodeURIComponent(path)}`;

export const fetchLabAnswerImageBlobUrl = async (path: string) => {
  const authStore = useAuthStore();
  const response = await fetch(buildAnswerImageUrl(path), {
    headers: authStore.token ? { Authorization: `Bearer ${authStore.token}` } : undefined,
  });

  if (!response.ok) {
    throw new Error('图片加载失败');
  }

  const blob = await response.blob();
  return URL.createObjectURL(blob);
};

export const revokeLabAnswerImageBlobUrl = (url?: string | null) => {
  if (url?.startsWith('blob:')) {
    URL.revokeObjectURL(url);
  }
};

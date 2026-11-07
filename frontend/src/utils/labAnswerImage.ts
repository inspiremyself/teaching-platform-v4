import { getToken } from '@/utils/authToken';
import { buildLabAnswerImageUrl } from '@/api/endpoints';

export const fetchLabAnswerImageBlobUrl = async (path: string) => {
  const token = getToken();
  const response = await fetch(buildLabAnswerImageUrl(path), {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
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

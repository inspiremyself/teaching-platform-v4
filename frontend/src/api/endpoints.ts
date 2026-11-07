import { getToken } from '@/utils/authToken';
import { API_BASE_URL } from '@/utils/request';
import type { LabBehaviorEventItem } from '@/types/lab';

const authHeaders = (): Record<string, string> => {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
};

export const buildMaterialDownloadUrl = (id: number | string) =>
  `${API_BASE_URL}/materials/${id}/download`;

export const buildLabAnswerImageUrl = (path: string) =>
  `${API_BASE_URL}/labs/answer-images?path=${encodeURIComponent(path)}`;

/** 相对 baseURL 的路径，供 axios 通道使用 */
export const behaviorEventsPath = (labId: number | string) =>
  `/student/labs/${labId}/behavior-events`;

/** 完整 URL，供 fetch keepalive 通道使用 */
export const buildBehaviorEventsUrl = (labId: number | string) =>
  `${API_BASE_URL}${behaviorEventsPath(labId)}`;

export const downloadMaterialBlob = async (id: number | string): Promise<Blob> => {
  const response = await fetch(buildMaterialDownloadUrl(id), { headers: authHeaders() });
  if (!response.ok) {
    throw new Error('资料下载失败');
  }
  return response.blob();
};

export const reportBehaviorEventsKeepalive = (
  labId: number | string,
  events: LabBehaviorEventItem[],
): void => {
  fetch(buildBehaviorEventsUrl(labId), {
    method: 'POST',
    keepalive: true,
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(),
    },
    body: JSON.stringify({ events }),
  }).catch(() => {
    // 卸载中无法重试，静默吞掉
  });
};

import request from '@/utils/request';
import { useAuthStore } from '@/stores/auth';
import type { MaterialItem, UpdateMaterialPayload, UploadMaterialPayload } from '@/types/material';

export const listMaterials = () => request.get<MaterialItem[]>('/materials');

export const uploadMaterial = (data: UploadMaterialPayload) => {
  const formData = new FormData();
  formData.append('title', data.title);
  formData.append('category', data.category);
  formData.append('description', data.description);
  formData.append('visibility', data.visibility);
  formData.append('file', data.file);
  return request.post<MaterialItem>('/materials', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  });
};

export const updateMaterial = (id: number, data: UpdateMaterialPayload) =>
  request.put<MaterialItem>(`/materials/${id}`, data);

export const deleteMaterial = (id: number) => request.delete<{ deleted: boolean }>(`/materials/${id}`);

export const downloadMaterial = async (url: string, fileName: string) => {
  const authStore = useAuthStore();
  const response = await fetch(url, {
    headers: authStore.token ? { Authorization: `Bearer ${authStore.token}` } : undefined,
  });
  if (!response.ok) {
    throw new Error('资料下载失败');
  }
  const blob = await response.blob();
  const objectUrl = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(objectUrl);
};

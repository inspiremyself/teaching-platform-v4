import request from '@/utils/request';
import { downloadMaterialBlob } from '@/api/endpoints';
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

export const downloadMaterial = async (id: number, fileName: string) => {
  const blob = await downloadMaterialBlob(id);
  const objectUrl = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(objectUrl);
};

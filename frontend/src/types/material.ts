export type MaterialVisibility = 'TEACHER' | 'ALL';

export interface MaterialItem {
  id: number;
  title: string;
  category: string;
  description: string;
  fileName: string;
  visibility: MaterialVisibility;
  uploaderUserId: number;
  downloadUrl: string;
}

export interface UpdateMaterialPayload {
  title?: string;
  category?: string;
  description?: string;
  visibility?: MaterialVisibility;
  /** 编辑历史资料可省略：省略表示保持原值 */
  subjectId?: number;
}

export interface UploadMaterialPayload {
  title: string;
  category: string;
  description: string;
  visibility: MaterialVisibility;
  /** 新建必填；指向本校 ACTIVE 的 school_subject.id */
  subjectId: number;
  file: File;
}

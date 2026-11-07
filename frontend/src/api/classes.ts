import request from '@/utils/request';
import type { PagedResult } from '@/types/common';
import type { TeachingClass } from '@/types/class';

export const listClasses = async (): Promise<TeachingClass[]> => {
  const result = await request.get<PagedResult<TeachingClass>>('/classes');
  return result.items;
};

import request from '@/utils/request';
import type { PagedResult } from '@/types/common';
import type { TeachingClass } from '@/types/class';

export const listClasses = () => request.get<PagedResult<TeachingClass>>('/classes');

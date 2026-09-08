import request from '@/utils/request';
import type { PageResult } from '@/types/common';
import type { TeachingClass } from '@/types/class';

export const listClasses = () => request.get<PageResult<TeachingClass>>('/classes');

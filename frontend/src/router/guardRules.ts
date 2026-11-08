import type { AppRole } from '@/layout/navigation';

export const resolveHomePath = (role: AppRole | undefined | null): string => {
  switch (role) {
    case 'SCHOOL_ADMIN':
      return '/admin/dashboard';
    case 'TEACHER':
      return '/teacher/dashboard';
    case 'STUDENT':
      return '/student/dashboard';
    case 'PLATFORM_ADMIN':
      return '/admin/schools';
    default:
      return '/login';
  }
};

export const shouldRedirectToHome = (
  roles: AppRole[] | undefined,
  userRole: AppRole | undefined | null,
): boolean => roles != null && (userRole == null || !roles.includes(userRole));

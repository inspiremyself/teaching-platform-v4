import type { AppRole } from '@/layout/navigation';

export interface RoleView {
  currentRole: AppRole;
  orgTerm: string;
  brandText: string;
  spaceLabel: string;
  sidebarNavLabel: string;
  themeClass: string;
}

export const resolveRoleView = (role: AppRole | undefined | null): RoleView => {
  switch (role) {
    case 'PLATFORM_ADMIN':
      return {
        currentRole: 'PLATFORM_ADMIN',
        orgTerm: '平台管理空间',
        brandText: '平台管理台',
        spaceLabel: '平台空间',
        sidebarNavLabel: '平台超管端主导航',
        themeClass: 'theme-admin',
      };
    case 'SCHOOL_ADMIN':
      return {
        currentRole: 'SCHOOL_ADMIN',
        orgTerm: '学校管理空间',
        brandText: '学校管理台',
        spaceLabel: '校管空间',
        sidebarNavLabel: '校管端主导航',
        themeClass: 'theme-admin',
      };
    case 'TEACHER':
      return {
        currentRole: 'TEACHER',
        orgTerm: '当前教学周期',
        brandText: '教学工作台',
        spaceLabel: '教师空间',
        sidebarNavLabel: '教师端主导航',
        themeClass: 'theme-teacher',
      };
    default:
      return {
        currentRole: 'STUDENT',
        orgTerm: '学生学习空间',
        brandText: '学习工作台',
        spaceLabel: '学生空间',
        sidebarNavLabel: '学生端主导航',
        themeClass: 'theme-student',
      };
  }
};

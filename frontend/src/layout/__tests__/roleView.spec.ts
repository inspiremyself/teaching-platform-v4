import { describe, expect, it } from 'vitest';
import { resolveRoleView } from '../roleView';
import { MOBILE_NAV, SIDEBAR_NAV } from '@/layout/navigation';

describe('resolveRoleView', () => {
  it('平台超管六字段按决策表取值', () => {
    expect(resolveRoleView('PLATFORM_ADMIN')).toEqual({
      currentRole: 'PLATFORM_ADMIN',
      orgTerm: '平台管理空间',
      brandText: '平台管理台',
      spaceLabel: '平台空间',
      sidebarNavLabel: '平台超管端主导航',
      themeClass: 'theme-admin',
    });
  });

  it('三角色与空值取值保持不变', () => {
    expect(resolveRoleView('SCHOOL_ADMIN')).toMatchObject({
      currentRole: 'SCHOOL_ADMIN',
      brandText: '学校管理台',
      themeClass: 'theme-admin',
    });
    expect(resolveRoleView('TEACHER')).toMatchObject({
      currentRole: 'TEACHER',
      brandText: '教学工作台',
      themeClass: 'theme-teacher',
    });
    expect(resolveRoleView('STUDENT')).toMatchObject({
      currentRole: 'STUDENT',
      brandText: '学习工作台',
      themeClass: 'theme-student',
    });
    expect(resolveRoleView(undefined).currentRole).toBe('STUDENT');
    expect(resolveRoleView(null).currentRole).toBe('STUDENT');
  });

  it('平台超管侧栏与移动导航各两项且顺序固定', () => {
    expect(SIDEBAR_NAV.PLATFORM_ADMIN).toEqual([
      { to: '/admin/schools', label: '学校管理', icon: 'domain' },
      { to: '/admin/platform-content', label: '平台内容', icon: 'menu_book' },
    ]);
    expect(MOBILE_NAV.PLATFORM_ADMIN).toEqual([
      { to: '/admin/schools', label: '学校管理', icon: 'domain' },
      { to: '/admin/platform-content', label: '平台内容', icon: 'menu_book' },
    ]);
  });
});

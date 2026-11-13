import { describe, expect, it } from 'vitest';
import { resolveHomePath, shouldRedirectToHome } from '../guardRules';

describe('resolveHomePath', () => {
  it('四角色与空值映射到唯一主页', () => {
    expect(resolveHomePath('SCHOOL_ADMIN')).toBe('/admin/dashboard');
    expect(resolveHomePath('TEACHER')).toBe('/teacher/dashboard');
    expect(resolveHomePath('STUDENT')).toBe('/student/dashboard');
    expect(resolveHomePath('PLATFORM_ADMIN')).toBe('/admin/schools');
    expect(resolveHomePath(undefined)).toBe('/login');
    expect(resolveHomePath(null)).toBe('/login');
  });
});

describe('shouldRedirectToHome', () => {
  it('roles 缺失时放行', () => {
    expect(shouldRedirectToHome(undefined, 'TEACHER')).toBe(false);
    expect(shouldRedirectToHome(undefined, undefined)).toBe(false);
  });

  it('roles 存在但用户为空时拒绝', () => {
    expect(shouldRedirectToHome(['TEACHER'], undefined)).toBe(true);
    expect(shouldRedirectToHome(['TEACHER'], null)).toBe(true);
  });

  it('按 roles 列表判定角色命中', () => {
    expect(shouldRedirectToHome(['SCHOOL_ADMIN'], 'PLATFORM_ADMIN')).toBe(true);
    expect(shouldRedirectToHome(['SCHOOL_ADMIN', 'PLATFORM_ADMIN'], 'PLATFORM_ADMIN')).toBe(false);
    expect(shouldRedirectToHome(['TEACHER'], 'TEACHER')).toBe(false);
  });

  it('平台内容入口只对平台超管开放、教师端入口只对教师开放', () => {
    expect(shouldRedirectToHome(['PLATFORM_ADMIN'], 'TEACHER')).toBe(true);
    expect(shouldRedirectToHome(['TEACHER'], 'PLATFORM_ADMIN')).toBe(true);
  });

  it('学习内容入口只对 STUDENT 开放', () => {
    expect(shouldRedirectToHome(['STUDENT'], 'TEACHER')).toBe(true);
    expect(shouldRedirectToHome(['STUDENT'], 'STUDENT')).toBe(false);
  });
});

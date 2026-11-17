import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const navSource = readFileSync(
  new URL('../../../layout/navigation.ts', import.meta.url),
  'utf-8',
);

const teacherSidebar = () => {
  const from = navSource.indexOf('TEACHER: [');
  const to = navSource.indexOf('STUDENT: [', from);
  return navSource.slice(from, to);
};

const teacherMobile = () => {
  const from = navSource.indexOf('TEACHER: [', navSource.indexOf('MOBILE_NAV'));
  const to = navSource.indexOf('STUDENT: [', from);
  return navSource.slice(from, to);
};

describe('教师端导航（实验报告取消一级菜单）', () => {
  it('教师端侧边栏不含实验报告一级菜单，且保留实验入口', () => {
    const sidebar = teacherSidebar();
    expect(sidebar).not.toContain('lab-reports');
    expect(sidebar).not.toContain("label: '实验报告'");
    expect(sidebar).toContain("to: '/teacher/labs'");
  });

  it('教师端移动端导航本就无实验报告，且保留实验入口', () => {
    const mobile = teacherMobile();
    expect(mobile).not.toContain('lab-reports');
    expect(mobile).toContain("to: '/teacher/labs'");
  });
});

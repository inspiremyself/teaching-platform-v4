import { readFileSync } from 'node:fs'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { describe, expect, it } from 'vitest'
import { resolveBreadcrumbTrail } from '@/router/breadcrumb'

const makeRoute = (
  name: string,
  params: Record<string, string> = {},
): RouteLocationNormalizedLoaded => ({ name, params }) as unknown as RouteLocationNormalizedLoaded

const componentSource = (): string =>
  readFileSync(new URL('../AppBreadcrumb.vue', import.meta.url), 'utf8')

describe('resolveBreadcrumbTrail', () => {
  it('教师考试结果拼出角色根与可点击中间段', () => {
    const trail = resolveBreadcrumbTrail(
      makeRoute('teacher-exam-results'),
      'TEACHER',
      '/teacher/dashboard',
    )
    expect(trail.map(item => item.label)).toEqual(['教学工作台', '考试', '考试结果'])
    expect(trail[0].to).toBe('/teacher/dashboard')
    expect(trail[1].to).toBe('/teacher/exams')
    expect(trail[2].to).toBeUndefined()
  })

  it('平台超管读取平台管理台作为角色根', () => {
    const trail = resolveBreadcrumbTrail(makeRoute('admin-schools'), 'PLATFORM_ADMIN', '/admin/schools')
    expect(trail.map(item => item.label)).toEqual(['平台管理台', '学校管理'])
  })

  it('校管读取学校管理台作为角色根', () => {
    const trail = resolveBreadcrumbTrail(makeRoute('admin-schools'), 'SCHOOL_ADMIN', '/admin/dashboard')
    expect(trail.map(item => item.label)).toEqual(['学校管理台', '学校管理'])
  })

  it('角色缺失时不注入角色根', () => {
    const trail = resolveBreadcrumbTrail(makeRoute('teacher-exam-results'), undefined, '/teacher/dashboard')
    expect(trail.map(item => item.label)).toEqual(['考试', '考试结果'])
  })

  it('未配置路由返回空数组', () => {
    expect(resolveBreadcrumbTrail(makeRoute('login'), 'TEACHER', '/teacher/dashboard')).toEqual([])
  })

  it('带参矩阵页从当前路由取 labId', () => {
    const trail = resolveBreadcrumbTrail(makeRoute('teacher-lab-matrix', { labId: 'L1' }), 'TEACHER', '/teacher/dashboard')
    expect(trail[2].to).toEqual({
      name: 'teacher-lab-submission-overview',
      params: { labId: 'L1' },
    })
  })

  it('带参重判页从 id 映射为 labId', () => {
    const trail = resolveBreadcrumbTrail(makeRoute('teacher-lab-blank-regrade', { id: 'L2' }), 'TEACHER', '/teacher/dashboard')
    const middle = trail[2].to as { params?: Record<string, string> }
    expect(middle.params?.labId).toBe('L2')
  })
})

describe('AppBreadcrumb.vue', () => {
  it('具备可访问性与自适应隐藏规则', () => {
    const source = componentSource()
    expect(source).toContain('aria-label="面包屑导航"')
    expect(source).toContain('aria-current')
    expect(source).toContain('router-link')
    expect(source).toContain('resolveBreadcrumbTrail')
    expect(source).toContain('max-width: 899px')
  })
})

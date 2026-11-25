import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), 'utf-8');

const teachersSource = read('../Teachers.vue');
const subjectsSource = read('../Subjects.vue');
const classesSource = read('../Classes.vue');
const studentsSource = read('../Students.vue');
const schoolsSource = read('../Schools.vue');

const pages = {
  'Teachers.vue': teachersSource,
  'Subjects.vue': subjectsSource,
  'Classes.vue': classesSource,
  'Students.vue': studentsSource,
};

describe('校管 CRUD 页面复用 useCrudTable', () => {
  it('四页均接入 useCrudTable', () => {
    for (const source of Object.values(pages)) {
      expect(source).toContain("from '@/composables/useCrudTable'");
      expect(source).toContain('useCrudTable<');
    }
  });

  it('四页不再保留本地 filteredRows computed', () => {
    for (const source of Object.values(pages)) {
      expect(source).not.toContain('const filteredRows = computed');
    }
  });

  it('学生页保留导入能力并改为 refresh 驱动', () => {
    expect(studentsSource).toContain('parseStudentExcelFile');
    expect(studentsSource).toContain('importAdminStudents');
    expect(studentsSource).toContain('@change="refresh"');
  });

  it('班级页保留多教师子表单与学科下拉同步', () => {
    expect(classesSource).toContain('syncSubjectSelectOptions');
    expect(classesSource).toContain('createEmptyTeacherRow');
  });

  it('学科页提交异常由页面吞掉', () => {
    expect(subjectsSource).toContain('swallowError: true');
  });

  it('Schools.vue 保持在边界外', () => {
    expect(schoolsSource).not.toContain('useCrudTable');
  });
});

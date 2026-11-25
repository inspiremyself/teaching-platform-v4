import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), 'utf-8');

const labsSource = read('../../../composables/useLabs.ts');
const homeworksSource = read('../../../composables/useHomeworks.ts');
const examListSource = read('../../../composables/useExamList.ts');
const labPanelSource = read('../components/LabListPanel.vue');
const homeworkPanelSource = read('../components/HomeworkListPanel.vue');
const examPanelSource = read('../components/ExamListPanel.vue');

const legacySymbols = [
  'searchKeyword',
  'searchQuery',
  'statusFilter',
  'classFilter',
  'dueFilter',
  'filteredRows',
  'highlightedExam',
];

const sliceResetFilters = (source: string) => {
  const startMarker = 'const resetFilters = () => {';
  const start = source.indexOf(startMarker);
  expect(start).toBeGreaterThan(-1);
  const end = source.indexOf('};', start);
  expect(end).toBeGreaterThan(start);
  return source.slice(start, end + 2);
};

describe('教师三域旧筛选层清理', () => {
  it('三个 composable 不再包含旧筛选符号', () => {
    for (const source of [labsSource, homeworksSource, examListSource]) {
      for (const symbol of legacySymbols) {
        expect(source).not.toContain(symbol);
      }
    }
  });

  it('resetFilters 只重置 workbench.filters 四项', () => {
    for (const source of [labsSource, homeworksSource]) {
      const block = sliceResetFilters(source);
      expect(block).toContain('workbench.filters.keyword.value');
      expect(block).toContain('workbench.filters.status.value');
      expect(block).toContain('workbench.filters.classId.value');
      expect(block).toContain('workbench.filters.deadlineBucket.value');
      expect(block).not.toContain('search');
      expect(block).not.toContain('classFilter');
      expect(block).not.toContain('dueFilter');
    }
  });

  it('三个面板仍绑定 workbench.filteredRows', () => {
    for (const source of [labPanelSource, homeworkPanelSource, examPanelSource]) {
      expect(source).toContain('workbench.filteredRows');
    }
  });

  it('保留符号与删除符号符合预期', () => {
    expect(labsSource).toContain('rows, classOptions');
    expect(examListSource).not.toContain('examDescription');
    expect(examPanelSource).not.toContain('highlightedExamTitle');
  });
});

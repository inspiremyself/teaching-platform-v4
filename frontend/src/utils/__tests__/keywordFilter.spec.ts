import { describe, expect, it } from 'vitest';
import { filterRowsByKeyword } from '@/utils/keywordFilter';

interface Row {
  title?: string | null;
  className?: string | null;
}

const rows: Row[] = [
  { title: 'Math 101', className: 'SE2026-1' },
  { title: 'Physics', className: 'Math Club' },
  { title: 'Chemistry', className: null },
];

const fieldsOf = (row: Row) => [row.title, row.className];

describe('filterRowsByKeyword', () => {
  it('空关键词与纯空格返回全部行且顺序不变', () => {
    expect(filterRowsByKeyword(rows, '', fieldsOf).map(row => row.title)).toEqual([
      'Math 101',
      'Physics',
      'Chemistry',
    ]);
    expect(filterRowsByKeyword(rows, '   ', fieldsOf).map(row => row.title)).toEqual([
      'Math 101',
      'Physics',
      'Chemistry',
    ]);
  });

  it('忽略大小写与首尾空白', () => {
    expect(filterRowsByKeyword(rows, ' MATH ', fieldsOf).map(row => row.title)).toEqual([
      'Math 101',
      'Physics',
    ]);
  });

  it('任一字段命中即保留', () => {
    expect(filterRowsByKeyword(rows, 'chemistry', fieldsOf).map(row => row.title)).toEqual(['Chemistry']);
  });

  it('空字段不报错也不误命中', () => {
    expect(filterRowsByKeyword([{ title: null, className: undefined }], 'x', fieldsOf)).toEqual([]);
  });

  it('无命中返回空数组', () => {
    expect(filterRowsByKeyword(rows, '不存在的关键词', fieldsOf)).toEqual([]);
  });
});

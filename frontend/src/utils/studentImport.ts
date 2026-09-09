import { read, utils, writeFileXLSX } from 'xlsx';
import type { ImportStudentRow } from '@/types/student';

export const REQUIRED_IMPORT_HEADERS = {
  username: ['学号', '账号', '用户名'],
  displayName: ['姓名', '学生姓名'],
  password: ['密码', '初始密码'],
} as const;

const normalizeCellValue = (value: string | number | boolean | null | undefined) => String(value ?? '').trim();

const findHeaderIndex = (headers: string[], candidates: readonly string[]) =>
  headers.findIndex(header => candidates.some(candidate => candidate === header));

export const parseStudentImportText = (text: string): ImportStudentRow[] =>
  text
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map(line => {
      const [username = '', displayName = '', password = ''] = line.split(',').map(item => item.trim());
      const normalizedPassword = password || username;
      return { username, displayName, password: normalizedPassword };
    })
    .filter(item => item.username && item.displayName);

export const parseStudentExcelFile = async (file: File): Promise<ImportStudentRow[]> => {
  const buffer = await file.arrayBuffer();
  const workbook = read(buffer, { type: 'array' });
  const firstSheetName = workbook.SheetNames[0];
  if (!firstSheetName) {
    throw new Error('Excel 文件中未找到可用工作表');
  }

  const worksheet = workbook.Sheets[firstSheetName];
  const rawRows = utils.sheet_to_json<Array<string | number | boolean | null>>(worksheet, {
    header: 1,
    raw: false,
    defval: '',
  });

  if (!rawRows.length) {
    throw new Error('Excel 文件内容为空');
  }

  const headerRow = rawRows[0].map(item => normalizeCellValue(item));
  const usernameIndex = findHeaderIndex(headerRow, REQUIRED_IMPORT_HEADERS.username);
  const displayNameIndex = findHeaderIndex(headerRow, REQUIRED_IMPORT_HEADERS.displayName);
  const passwordIndex = findHeaderIndex(headerRow, REQUIRED_IMPORT_HEADERS.password);

  if (usernameIndex < 0 || displayNameIndex < 0) {
    throw new Error('Excel 表头必须包含“学号”和“姓名”列');
  }

  return rawRows
    .slice(1)
    .map(row => {
      const username = normalizeCellValue(row[usernameIndex]);
      const displayName = normalizeCellValue(row[displayNameIndex]);
      const rawPassword = passwordIndex >= 0 ? normalizeCellValue(row[passwordIndex]) : '';
      const password = rawPassword || username;
      return { username, displayName, password };
    })
    .filter(item => item.username || item.displayName || item.password)
    .filter(item => item.username && item.displayName);
};

export const downloadStudentImportTemplate = () => {
  const worksheet = utils.aoa_to_sheet([
    ['学号', '姓名', '密码'],
    ['20260011', '张三', ''],
    ['20260012', '李四', '123456'],
  ]);
  const workbook = utils.book_new();
  utils.book_append_sheet(workbook, worksheet, '学生导入模板');
  writeFileXLSX(workbook, '学生批量导入模板.xlsx');
};

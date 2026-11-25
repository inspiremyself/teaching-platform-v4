export function filterRowsByKeyword<T>(
  rows: readonly T[],
  keyword: string,
  fieldsOf: (row: T) => ReadonlyArray<string | null | undefined>,
): T[] {
  const key = keyword.trim().toLowerCase();
  if (!key) {
    return [...rows];
  }

  return rows.filter(row => fieldsOf(row).some(field => (field ?? '').toLowerCase().includes(key)));
}

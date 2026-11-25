import { computed, type ComputedRef, type Ref } from 'vue';
import { filterRowsByKeyword } from '@/utils/keywordFilter';

export function useKeywordFilter<T>(
  rows: Ref<T[]>,
  keyword: Ref<string>,
  fieldsOf: (row: T) => ReadonlyArray<string | null | undefined>,
): { keywordMatchedRows: ComputedRef<T[]> } {
  const keywordMatchedRows = computed(() => filterRowsByKeyword(rows.value, keyword.value, fieldsOf));
  return { keywordMatchedRows };
}

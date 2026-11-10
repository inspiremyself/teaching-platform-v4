<template>
  <section class="question-import-spec">
    <h4 class="question-import-spec__title">字段要求</h4>
    <el-table :data="fields" size="small" border class="question-import-spec__table">
      <el-table-column prop="name" label="字段" width="100" />
      <el-table-column prop="type" label="类型" width="80" />
      <el-table-column label="必填" width="64">
        <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="默认" width="80">
        <template #default="{ row }">{{ row.defaultValue === null ? '—' : row.defaultValue || "''" }}</template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="240" />
    </el-table>

    <h4 class="question-import-spec__title">code 编码要求</h4>
    <el-table :data="codeRules" size="small" border class="question-import-spec__table">
      <el-table-column prop="name" label="规则" width="130" />
      <el-table-column prop="detail" label="说明" min-width="300" />
    </el-table>

    <h4 class="question-import-spec__title">文件要求</h4>
    <el-table :data="fileRules" size="small" border class="question-import-spec__table">
      <el-table-column prop="name" label="项" width="110" />
      <el-table-column prop="requirement" label="要求" min-width="300" />
    </el-table>
  </section>
</template>

<script setup lang="ts">
import {
  IMPORT_CODE_REQUIREMENTS,
  IMPORT_FIELD_DEFINITIONS,
  IMPORT_FILE_REQUIREMENTS,
} from '@/utils/questionImportSpec';

const fields = IMPORT_FIELD_DEFINITIONS;
const fileRules = IMPORT_FILE_REQUIREMENTS;
const codeRules = [
  ...IMPORT_CODE_REQUIREMENTS.hardRules.map((rule) => ({
    name: rule.match(/^H\d+/)?.[0] ?? '规则',
    detail: rule,
  })),
  { name: '唯一作用域', detail: IMPORT_CODE_REQUIREMENTS.duplicateRule },
  { name: '合规示例', detail: IMPORT_CODE_REQUIREMENTS.validExamples.join('、') },
  { name: '不合规示例', detail: IMPORT_CODE_REQUIREMENTS.invalidExamples.join('、') },
  ...IMPORT_CODE_REQUIREMENTS.softRules.map((rule) => ({ name: '软规则', detail: rule })),
];
</script>

<style scoped>
.question-import-spec {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.question-import-spec__title {
  margin: 4px 0 0;
  color: #0f172a;
  font-size: 13px;
}

.question-import-spec__table {
  font-size: 12px;
}
</style>

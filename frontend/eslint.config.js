import tseslint from 'typescript-eslint';
import vuePlugin from 'eslint-plugin-vue';
import vueParser from 'vue-eslint-parser';

const files = ['**/*.ts', '**/*.vue'];

export default [
  { ignores: ['dist/**', 'node_modules/**', 'coverage/**'] },
  ...tseslint.configs.recommended.map(config => ({ ...config, files })),
  ...vuePlugin.configs['flat/recommended'].map(config => ({ ...config, files })),
  {
    files,
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: tseslint.parser,
        ecmaVersion: 'latest',
        sourceType: 'module',
      },
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
      '@typescript-eslint/no-empty-object-type': 'off',
      'vue/multi-word-component-names': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/html-self-closing': 'off',
      'vue/attributes-order': 'off',
      'vue/no-v-html': 'off',
      'vue/require-default-prop': 'off',
      // 与 prettier 分工：HTML 缩进 / 换行由 format 脚本负责，lint 不重复报格式问题
      'vue/html-indent': 'off',
      'vue/multiline-html-element-content-newline': 'off',
      // 现网 FillBlankInlineEditor.vue 的 computed 有既有副作用，不属本期结构治理范围
      'vue/no-side-effects-in-computed-properties': 'off',
      'no-undef': 'off',
    },
  },
];

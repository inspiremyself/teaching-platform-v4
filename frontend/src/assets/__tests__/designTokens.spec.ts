import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

/**
 * 设计体系门禁（PRD FR-20 / 技术方案 §5.1）
 *
 * 8 条断言把「token 唯一来源、骨架类去重、断点收敛、图标统一、尺寸阶梯」变成可执行约束。
 * 历史遗留量记录在 designTokenBaseline.json，只允许下降；新增违规会直接让 npm test 失败。
 */

const SRC = fileURLToPath(new URL('../../', import.meta.url));
const BASELINE_PATH = fileURLToPath(new URL('./designTokenBaseline.json', import.meta.url));

/** 允许的断点集合（技术方案 §5.1 #4） */
const ALLOWED_BREAKPOINTS = [640, 768, 900, 1024, 1280];

/** 字号阶梯（PRD D7）：11 / 12 / 13 / 14 / 16 / 18 / 22 / 28 / 36 / 44 / 58 */
const FONT_SIZE_LADDER = [11, 12, 13, 14, 16, 18, 22, 28, 36, 44, 58];

/** 图标字号 24 按 PRD D17 固定，不参与阶梯归一 */
const FONT_SIZE_WHITELIST = [...FONT_SIZE_LADDER, 24];

/** 圆角阶梯（PRD D7）：999 / 16 / 12 / 8 / 6，另允许 0 */
const RADIUS_LADDER = [0, 6, 8, 12, 16, 999];

/** 6 个共享骨架类（技术方案 §3.6） */
const SKELETON_CLASSES = ['toolbar-card', 'table-card', 'identity-cell', 'summary-grid', 'state-card', 'toolbar-row'];

interface Baseline {
  version: number;
  generatedAt: string;
  views: {
    hexColorLiterals: number;
    importantCount: number;
    nonStandardBreakpoints: number[];
    elIconRefs: number;
  };
  skeletonDuplicatesOutsideAssets: number;
  nonStandardFontSizes: number[];
  nonStandardRadii: number[];
}

/** 递归收集目录下所有文件（不跟随目录软链，仓库内无此场景） */
const walk = (dir: string): string[] =>
  readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const full = join(dir, entry.name);
    return entry.isDirectory() ? walk(full) : [full];
  });

const read = (file: string): string => readFileSync(file, 'utf8');

const viewsFiles = walk(join(SRC, 'views')).filter(file => file.endsWith('.vue'));
const uiFiles = walk(join(SRC, 'components/ui')).filter(file => file.endsWith('.vue'));
/** gate #7 / #8 扫描范围：assets/*.css（除 tokens）+ views/** + components/ui/** */
const styleScopeFiles = [
  join(SRC, 'assets/global.css'),
  join(SRC, 'assets/layout.css'),
  ...viewsFiles,
  ...uiFiles,
];
/** gate #6 扫描范围：src 下非 assets 文件 */
const skeletonScanFiles = walk(SRC).filter(
  file => !file.includes(`${sep}assets${sep}`) && /\.(vue|css)$/.test(file),
);

const matchAll = (source: string, pattern: RegExp): RegExpMatchArray[] => Array.from(source.matchAll(pattern));

const countHexLiterals = (source: string): number => matchAll(source, /#[0-9A-Fa-f]{3,8}\b/g).length;

const countImportant = (source: string): number => matchAll(source, /!important/g).length;

const collectBreakpoints = (source: string): number[] =>
  matchAll(source, /@media\s*\(max-width:\s*(\d+)px\)/g).map(match => Number(match[1]));

const countElIconRefs = (source: string): number =>
  matchAll(source, /\bel-icon\b/g).length + matchAll(source, /@element-plus\/icons-vue/g).length;

const countSkeletonDuplicates = (source: string): number =>
  matchAll(source, new RegExp(`\\.(?:${SKELETON_CLASSES.join('|')})\\s*\\{`, 'g')).length;

/** 提取所有 px 字号，返回不在阶梯内的值 */
const collectNonStandardFontSizes = (source: string): number[] =>
  matchAll(source, /font-size:\s*([\d.]+)px/g)
    .map(match => Number(match[1]))
    .filter(size => !FONT_SIZE_WHITELIST.includes(size));

/** 提取所有 px 圆角（含多值写法），返回不在阶梯内的值；var()/百分比等表达式跳过 */
const collectNonStandardRadii = (source: string): number[] =>
  matchAll(source, /border-radius:\s*([^;]+);/g)
    .flatMap(match => {
      const value = match[1].trim();
      if (value.includes('var(') || value.includes('%')) return [];
      return matchAll(value, /([\d.]+)px/g).map(item => Number(item[1]));
    })
    .filter(radius => !RADIUS_LADDER.includes(radius));

/** 校验自定义属性右值（gate #7），返回违规行 */
const collectInvalidCustomProps = (source: string): string[] =>
  source.split('\n').flatMap((line, index) => {
    const match = line.match(/^\s*--([\w-]+)\s*:\s*(.+);/);
    if (!match || match[1].startsWith('el-')) return [];
    return match[2].trim().startsWith('var(') ? [] : [`L${index + 1}: ${line.trim()}`];
  });

const measure = (): Omit<Baseline, 'version' | 'generatedAt'> => {
  const viewsSource = viewsFiles.map(read).join('\n');
  const styleScopeSource = styleScopeFiles.map(read).join('\n');
  const skeletonScanSource = skeletonScanFiles.map(read).join('\n');

  return {
    views: {
      hexColorLiterals: countHexLiterals(viewsSource),
      importantCount: countImportant(viewsSource),
      nonStandardBreakpoints: [...new Set(collectBreakpoints(viewsSource))]
        .filter(value => !ALLOWED_BREAKPOINTS.includes(value))
        .sort((a, b) => a - b),
      elIconRefs: countElIconRefs(viewsSource),
    },
    skeletonDuplicatesOutsideAssets: countSkeletonDuplicates(skeletonScanSource),
    nonStandardFontSizes: [...new Set(collectNonStandardFontSizes(styleScopeSource))].sort((a, b) => a - b),
    nonStandardRadii: [...new Set(collectNonStandardRadii(styleScopeSource))].sort((a, b) => a - b),
  };
};

/**
 * 基线写入模式：DESIGN_TOKEN_BASELINE_WRITE=1 时重新测量并覆盖 JSON。
 * 正常 npm test 只读不写，人工 review diff 时只允许数值下降。
 */
if (process.env.DESIGN_TOKEN_BASELINE_WRITE === '1') {
  const measured = measure();
  const payload: Baseline = {
    version: 1,
    generatedAt: new Date().toISOString().slice(0, 10),
    ...measured,
  };
  writeFileSync(BASELINE_PATH, `${JSON.stringify(payload, null, 2)}\n`);
}

const baseline = JSON.parse(read(BASELINE_PATH)) as Baseline;

describe('设计体系门禁', () => {
  it('#1 tokens/layout 存在，main.ts 引入顺序为 EP → tokens → global → layout', () => {
    const tokensPath = join(SRC, 'assets/tokens.css');
    const layoutPath = join(SRC, 'assets/layout.css');
    expect(existsSync(tokensPath)).toBe(true);
    expect(existsSync(layoutPath)).toBe(true);

    const mainSource = read(join(SRC, 'main.ts'));
    const order = [
      'element-plus/theme-chalk/index.css',
      './assets/tokens.css',
      './assets/global.css',
      './assets/layout.css',
    ].map(item => mainSource.indexOf(item));

    expect(order.every(index => index >= 0)).toBe(true);
    expect([...order].sort((a, b) => a - b)).toEqual(order);
  });

  it('#2 views 十六进制颜色字面量不超过基线', () => {
    expect(measure().views.hexColorLiterals).toBeLessThanOrEqual(baseline.views.hexColorLiterals);
  });

  it('#3 views !important 数量不超过基线', () => {
    expect(measure().views.importantCount).toBeLessThanOrEqual(baseline.views.importantCount);
  });

  it('#4 断点只允许 640/768/900/1024/1280 或基线例外', () => {
    const extras = measure().views.nonStandardBreakpoints.filter(
      value => !ALLOWED_BREAKPOINTS.includes(value) && !baseline.views.nonStandardBreakpoints.includes(value),
    );
    expect(extras).toEqual([]);
  });

  it('#5 views 不再引用 Element Plus 图标组件', () => {
    expect(measure().views.elIconRefs).toBeLessThanOrEqual(baseline.views.elIconRefs);
  });

  it('#6 骨架类在 assets 之外无自定义定义', () => {
    expect(measure().skeletonDuplicatesOutsideAssets).toBeLessThanOrEqual(
      baseline.skeletonDuplicatesOutsideAssets,
    );
  });

  it('#7 tokens.css 之外的自定义属性右值必须以 var() 开头（豁免 --el-*）', () => {
    const violations = styleScopeFiles.flatMap(file =>
      collectInvalidCustomProps(read(file)).map(issue => `${file.replace(SRC, '')} ${issue}`),
    );
    expect(violations).toEqual([]);
  });

  it('#8 font-size / border-radius 属于阶梯或基线例外', () => {
    const measured = measure();
    const extraFontSizes = measured.nonStandardFontSizes.filter(
      value => !FONT_SIZE_WHITELIST.includes(value) && !baseline.nonStandardFontSizes.includes(value),
    );
    const extraRadii = measured.nonStandardRadii.filter(
      value => !RADIUS_LADDER.includes(value) && !baseline.nonStandardRadii.includes(value),
    );
    expect({ extraFontSizes, extraRadii }).toEqual({ extraFontSizes: [], extraRadii: [] });
  });
});

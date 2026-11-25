import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

/**
 * 设计体系门禁（PRD FR-20 / FR-22 / 技术方案 §5.1）
 *
 * 14 条断言把「token 唯一来源、骨架类去重、断点收敛、图标统一、尺寸阶梯、排版归一、组件唯一化」变成可执行约束。
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
    emptyStateLiterals: number;
  };
  skeletonDuplicatesOutsideAssets: number;
  nonStandardFontSizes: number[];
  nonStandardRadii: number[];
  /** 排版归一（FR-21D/FR-21E）：P3.1 后目标全 0 */
  fontSizeLiterals: number;
  nonStandardFontWeights: number;
  nonStandardLineHeights: number;
  nonStandardLetterSpacings: number;
}

/** 图标规则块选择器：块内 font-size 18/24px 与图标 reset（font-weight: normal、line-height: 1）整体豁免（PRD D17 / §5.1 #9） */
const ICON_BLOCK_SELECTORS = ['material-symbols-outlined', '.app-icon--sm', '.app-icon--md'];

/** #11 白名单：互斥状态分支导致同文件多个 h1 的显式例外（不写入基线 JSON） */
const H1_WHITELIST: Record<string, number> = {
  'views/student/components/ExamAnswerPanel.vue': 3, // 进行中 / 未开始 / 已完成三互斥分支各渲染一个 h1
};

/** 递归收集目录下所有文件（不跟随目录软链，仓库内无此场景） */
const walk = (dir: string): string[] =>
  readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const full = join(dir, entry.name);
    return entry.isDirectory() ? walk(full) : [full];
  });

const read = (file: string): string => readFileSync(file, 'utf8');

const viewsFiles = walk(join(SRC, 'views')).filter(file => file.endsWith('.vue'));
const uiFiles = walk(join(SRC, 'components/ui')).filter(file => file.endsWith('.vue'));
/** gate #9 / #10 额外纳入 views 下的共享 CSS（workbench.css 等），#7 / #8 维持原口径 */
const viewsCssFiles = walk(join(SRC, 'views')).filter(file => file.endsWith('.css'));
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

const countEmptyStates = (source: string): number =>
  matchAll(source, /<el-(?:empty|result)\b/g).length;

/** 空态计数范围：views/** + components/ui/**，排除 AppEmptyState.vue 自身实现 */
const emptyStateFiles = [...viewsFiles, ...uiFiles].filter(file => !file.endsWith('AppEmptyState.vue'));

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

/** 提取 .vue 文件内全部 <style> 块正文 */
const extractStyleBlocks = (source: string): string[] =>
  Array.from(source.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)).map(match => match[1]);

interface RuleBlock {
  selector: string;
  body: string;
}

/** 递归展开 CSS 规则块（@media 内层规则块单独返回），供块上下文豁免判断 */
const splitRules = (css: string): RuleBlock[] => {
  const blocks: RuleBlock[] = [];
  let cursor = 0;
  while (cursor < css.length) {
    const open = css.indexOf('{', cursor);
    if (open < 0) break;
    const selector = css.slice(cursor, open).trim();
    let depth = 1;
    let close = open + 1;
    while (close < css.length && depth > 0) {
      if (css[close] === '{') depth++;
      else if (css[close] === '}') depth--;
      close++;
    }
    const body = css.slice(open + 1, close - 1);
    if (selector.startsWith('@') && !selector.startsWith('@font-face')) {
      blocks.push(...splitRules(body));
    } else {
      blocks.push({ selector, body });
    }
    cursor = close;
  }
  return blocks;
};

/** 块级豁免：@font-face（变量字体字重范围声明）与图标规则块（D17 图标尺寸 / reset） */
const isExemptBlock = (selector: string): boolean =>
  selector.startsWith('@font-face') || ICON_BLOCK_SELECTORS.some(item => selector.includes(item));

/** 字重 / 行高 / 字距白名单（FR-21E：var() 引用一律合法） */
const isLegalFontWeight = (value: string): boolean => /^(var\(|[4567]00\b)/.test(value);
const isLegalLineHeight = (value: string): boolean => /^(var\(|1\.15\b|1\.3\b|1\.5\b|1\.6\b)/.test(value);
const isLegalLetterSpacing = (value: string): boolean =>
  /^(var\(|normal\b|-0\.025em\b|0\.04em\b|0\.08em\b)/.test(value);

/** 排版归一测量（gate #9 / #10）：图标与 @font-face 块豁免后统计违规声明 */
const measureTypography = (source: string, isVue: boolean) => {
  const css = isVue ? extractStyleBlocks(source).join('\n') : source;
  let fontSizeLiterals = 0;
  let nonStandardFontWeights = 0;
  let nonStandardLineHeights = 0;
  let nonStandardLetterSpacings = 0;
  for (const block of splitRules(css)) {
    if (isExemptBlock(block.selector)) continue;
    fontSizeLiterals += matchAll(block.body, /font-size:\s*[\d.]+px/g).length;
    nonStandardFontWeights += matchAll(block.body, /font-weight:\s*([^;}\n]+)/g)
      .filter(match => !isLegalFontWeight(match[1].trim())).length;
    nonStandardLineHeights += matchAll(block.body, /line-height:\s*([^;}\n]+)/g)
      .filter(match => !isLegalLineHeight(match[1].trim())).length;
    nonStandardLetterSpacings += matchAll(block.body, /letter-spacing:\s*([^;}\n]+)/g)
      .filter(match => !isLegalLetterSpacing(match[1].trim())).length;
  }
  return { fontSizeLiterals, nonStandardFontWeights, nonStandardLineHeights, nonStandardLetterSpacings };
};

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
      emptyStateLiterals: countEmptyStates(emptyStateFiles.map(read).join('\n')),
    },
    skeletonDuplicatesOutsideAssets: countSkeletonDuplicates(skeletonScanSource),
    nonStandardFontSizes: [...new Set(collectNonStandardFontSizes(styleScopeSource))].sort((a, b) => a - b),
    nonStandardRadii: [...new Set(collectNonStandardRadii(styleScopeSource))].sort((a, b) => a - b),
    ...[...styleScopeFiles, ...viewsCssFiles].reduce(
      (totals, file) => {
        const issues = measureTypography(read(file), file.endsWith('.vue'));
        return {
          fontSizeLiterals: totals.fontSizeLiterals + issues.fontSizeLiterals,
          nonStandardFontWeights: totals.nonStandardFontWeights + issues.nonStandardFontWeights,
          nonStandardLineHeights: totals.nonStandardLineHeights + issues.nonStandardLineHeights,
          nonStandardLetterSpacings: totals.nonStandardLetterSpacings + issues.nonStandardLetterSpacings,
        };
      },
      { fontSizeLiterals: 0, nonStandardFontWeights: 0, nonStandardLineHeights: 0, nonStandardLetterSpacings: 0 },
    ),
  };
};

/**
 * 基线写入模式：DESIGN_TOKEN_BASELINE_WRITE=1 时重新测量并覆盖 JSON。
 * 正常 npm test 只读不写，人工 review diff 时只允许数值下降。
 */
if (process.env.DESIGN_TOKEN_BASELINE_WRITE === '1') {
  const measured = measure();
  const payload: Baseline = {
    version: 3,
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

  it('#9 font-size 不得出现 px 字面量（图标块 18/24 豁免），不超过基线', () => {
    expect(measure().fontSizeLiterals).toBeLessThanOrEqual(baseline.fontSizeLiterals);
  });

  it('#10 字重 / 行高 / 字距取值属于白名单（var() / 400-700 / 4 档行高 / 4 态字距）', () => {
    const measured = measure();
    expect(measured.nonStandardFontWeights).toBeLessThanOrEqual(baseline.nonStandardFontWeights);
    expect(measured.nonStandardLineHeights).toBeLessThanOrEqual(baseline.nonStandardLineHeights);
    expect(measured.nonStandardLetterSpacings).toBeLessThanOrEqual(baseline.nonStandardLetterSpacings);
  });

  it('#11 标题层级唯一：无 h5/h6；每文件 h1 数不超过白名单；页头骨架为 workbench-header', () => {
    const problems: string[] = [];
    for (const file of [...viewsFiles, ...uiFiles]) {
      const source = read(file);
      const relative = file.replace(SRC, '').replace(/^[\\/]/, '');
      const h56Count = matchAll(source, /<h[56][\s>]/g).length;
      if (h56Count > 0) problems.push(`${relative}: h5/h6 x${h56Count}`);
      const h1Count = matchAll(source, /<h1[\s>]/g).length;
      const limit = H1_WHITELIST[relative] ?? 1;
      if (h1Count > limit) problems.push(`${relative}: h1 x${h1Count} > ${limit}`);
    }
    const globalCss = read(join(SRC, 'assets/global.css'));
    if (!/\.workbench-header\s*\{/.test(globalCss)) problems.push('global.css: 缺少 .workbench-header 骨架');
    if (!/\.workbench-header__main h1/.test(globalCss)) problems.push('global.css: 缺少 .workbench-header__main h1 标题规则');
    if (existsSync(join(SRC, 'components/ui/AppPageHeader.vue'))) problems.push('AppPageHeader.vue: 应已删除');
    expect(problems).toEqual([]);
  });

  it('#12 页头唯一性：components/ui 无零引用组件；全站无 page-header；labs-header-card 与 workbench-header 同现', () => {
    const problems: string[] = [];
    const allSources = walk(SRC).filter(file => /\.(vue|ts)$/.test(file) && !file.includes(`${sep}__tests__${sep}`));
    for (const file of uiFiles) {
      const name = file.split(sep).pop()!.replace('.vue', '');
      if (!allSources.some(item => item !== file && read(item).includes(name))) {
        problems.push(`${file.replace(SRC, '')}: components/ui 零引用组件`);
      }
    }
    const styleFiles = [
      ...viewsFiles,
      ...uiFiles,
      ...viewsCssFiles,
      join(SRC, 'assets/global.css'),
      join(SRC, 'assets/layout.css'),
    ];
    for (const file of styleFiles) {
      if (/\.page-header\b/.test(read(file))) problems.push(`${file.replace(SRC, '')}: 仍存在 .page-header 选择器`);
    }
    const labs = read(join(SRC, 'views/student/Labs.vue'));
    if (labs.includes('labs-header-card') && !labs.includes('workbench-header')) {
      problems.push('student/Labs.vue: labs-header-card 未与 workbench-header 同现');
    }
    expect(problems).toEqual([]);
  });

  it('#13 action-button 写法唯一：全站仅 ActionButton.vue 与 workbench.css 可出现', () => {
    const workbenchDir = join(SRC, 'views/teacher/components/workbench') + sep;
    const allowed = new Set([
      join(workbenchDir, 'ActionButton.vue'),
      join(workbenchDir, 'workbench.css'),
    ]);
    const violations = walk(SRC)
      .filter(file => /\.(vue|css)$/.test(file) && !file.includes(`${sep}__tests__${sep}`))
      .filter(file => !file.startsWith(workbenchDir) || !allowed.has(file))
      .filter(file => read(file).includes('action-button'))
      .map(file => file.replace(SRC, ''));
    expect(violations).toEqual([]);
  });

  it('#14 空态计数不超过基线（AppEmptyState 自身实现不计入）', () => {
    expect(measure().views.emptyStateLiterals).toBeLessThanOrEqual(baseline.views.emptyStateLiterals);
  });
});

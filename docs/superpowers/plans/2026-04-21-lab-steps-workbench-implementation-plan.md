# Lab Steps Workbench Implementation Plan

> **历史设计文档**：本文涉及 `defaultScore` 的描述已被 `docs/PRD/2026-11-09-题目分值归属与组卷分值规范PRD.md`（V1.5）覆盖；题库无分值，分值只属组卷配置。

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the teacher lab steps page into a viewport-locked three-column workbench, extract a shared typed-editor core reused by the teacher question-bank page and teacher lab steps page, and add an optional create-question-on-save flow that runs strictly after lab step save and never creates an ongoing binding.

**Architecture:** The safest implementation is to extract a shared typed-editor core as pure TypeScript state/adapter modules plus focused Vue editor sections, then let `Questions.vue` and `LabSteps.vue` remain separate page-level orchestration layers with separate payload builders. `LabSteps.vue` owns the new three-column layout and save sequencing, while `Questions.vue` adopts the same shared editing core without sharing final API payloads.

**Tech Stack:** Vue 3 `<script setup>`, Element Plus, Vue Router, existing Axios request wrapper in `frontend/src/utils/request.ts`, teacher views in `frontend/src/views/teacher/`, existing typed question and lab contracts in `frontend/src/types/`.

---

## File Structure

### Create
- `teaching-platform/frontend/src/views/teacher/components/typed-editor/types.ts`
- `teaching-platform/frontend/src/views/teacher/components/typed-editor/constants.ts`
- `teaching-platform/frontend/src/views/teacher/components/typed-editor/useTypedEditor.ts`
- `teaching-platform/frontend/src/views/teacher/components/typed-editor/adapters/questionAdapter.ts`
- `teaching-platform/frontend/src/views/teacher/components/typed-editor/adapters/labStepAdapter.ts`
- `teaching-platform/frontend/src/views/teacher/components/typed-editor/hydrators/fromQuestion.ts`
- `teaching-platform/frontend/src/views/teacher/components/typed-editor/hydrators/fromLabStep.ts`
- `teaching-platform/frontend/src/views/teacher/components/typed-editor/TypedEditorShell.vue`
- `teaching-platform/frontend/src/views/teacher/components/typed-editor/ObjectiveEditor.vue`
- `teaching-platform/frontend/src/views/teacher/components/typed-editor/FillBlankEditor.vue`
- `teaching-platform/frontend/src/views/teacher/components/typed-editor/SubjectiveEditor.vue`

### Modify
- `teaching-platform/frontend/src/views/teacher/Questions.vue`
- `teaching-platform/frontend/src/views/teacher/LabSteps.vue`
- `teaching-platform/frontend/src/layout/index.vue`
- `teaching-platform/frontend/src/types/question.ts`
- `teaching-platform/frontend/src/types/lab.ts`

### Verify
- `teaching-platform/frontend/package.json`
- `teaching-platform/frontend/npm run build`

---

## Task 1: Define shared typed-editor contracts

**Files:**
- Create: `teaching-platform/frontend/src/views/teacher/components/typed-editor/types.ts`
- Create: `teaching-platform/frontend/src/views/teacher/components/typed-editor/constants.ts`
- Modify: `teaching-platform/frontend/src/types/question.ts`
- Modify: `teaching-platform/frontend/src/types/lab.ts`

- [ ] **Step 1: Define the internal editor type vocabulary**

Create a shared internal type layer that does not leak backend payload names:

```ts
export type TypedEditorKind = 'SINGLE' | 'MULTI' | 'JUDGE' | 'FILL' | 'SHORT' | 'TEXT' | 'CODE';

export interface TypedEditorOptionDraft {
  id: string;
  key: string;
  content: string;
}

export interface TypedEditorBlankDraft {
  index: number;
  token: string;
  answersText: string;
}

export interface TypedEditorKeywordDraft {
  term: string;
  weight: number;
}

export interface TypedEditorDraft {
  kind: TypedEditorKind;
  prompt: string;
  analysisText: string;
  objectiveOptions: TypedEditorOptionDraft[];
  selectedObjectiveOptionIds: string[];
  judgeAnswer: 'T' | 'F' | '';
  fillBlanks: TypedEditorBlankDraft[];
  subjectiveAnswer: string;
  keywords: TypedEditorKeywordDraft[];
  minLength: number;
  commentTemplate: string;
  codeLanguage: string;
  codeRubric: string;
}

export interface TypedEditorValidationResult {
  messages: string[];
}
```

- [ ] **Step 2: Define shared labels and defaults**

Add `constants.ts` with shared labels and default editor state builders:

```ts
export const TYPED_EDITOR_LABELS: Record<TypedEditorKind, string> = {
  SINGLE: '单选题',
  MULTI: '多选题',
  JUDGE: '判断题',
  FILL: '填空题',
  SHORT: '简答题',
  TEXT: '文本题',
  CODE: '代码题',
};
```

- [ ] **Step 3: Keep page-facing backend contracts separate**

Only add narrow helper types to `types/question.ts` and `types/lab.ts` if absolutely necessary. Do not merge page payloads into the shared editor contract.

- [ ] **Step 4: Verify type boundaries**

Run: `npm run build`

Expected: build still passes, no shared contract file introduces type errors.

---

## Task 2: Extract shared hydration and serialization helpers

**Files:**
- Create: `teaching-platform/frontend/src/views/teacher/components/typed-editor/hydrators/fromQuestion.ts`
- Create: `teaching-platform/frontend/src/views/teacher/components/typed-editor/hydrators/fromLabStep.ts`
- Create: `teaching-platform/frontend/src/views/teacher/components/typed-editor/adapters/questionAdapter.ts`
- Create: `teaching-platform/frontend/src/views/teacher/components/typed-editor/adapters/labStepAdapter.ts`

- [ ] **Step 1: Move question hydration out of `Questions.vue`**

Extract the existing question parsing behavior into `fromQuestion.ts`:
- objective option parsing
- judge answer parsing
- fill blank parsing
- subjective answer parsing
- CODE compatibility behavior

- [ ] **Step 2: Move lab-step hydration out of `LabSteps.vue`**

Extract current `answerConfigJson` parsing and question-type normalization into `fromLabStep.ts`, preserving:
- `TEXT`
- `SHORT_ANSWER`
- `FILL_BLANK`
- `SINGLE_CHOICE`
- `MULTIPLE_CHOICE`
- `TRUE_FALSE`
- `CODE`
- any current legacy normalization rules already present in `LabSteps.vue`

- [ ] **Step 3: Add page-specific payload adapters**

Create adapter modules so shared draft state can become two different payloads:

```ts
export function buildQuestionPayloadFromDraft(...) {}
export function buildLabStepPayloadFromDraft(...) {}
```

Rules:
- Question adapter outputs `SaveQuestionPayload`
- Lab-step adapter outputs the existing `CreateLabStepPayload` / `UpdateLabStepPayload` shape
- CODE handling for questions remains question-adapter-specific

- [ ] **Step 4: Verify no page behavior changed yet**

Run: `npm run build`

Expected: build passes before UI extraction begins.

---

## Task 3: Build shared typed-editor state manager

**Files:**
- Create: `teaching-platform/frontend/src/views/teacher/components/typed-editor/useTypedEditor.ts`

- [ ] **Step 1: Move shared validation into one place**

Extract the current validation behavior from `Questions.vue` and `LabSteps.vue` into one composable that returns:
- the current draft
- methods for type switching and reset
- computed validation messages
- fill-blank synchronization helpers

- [ ] **Step 2: Keep fill-blank token sync as a first-class capability**

Preserve the question editor’s current behavior where inserting and editing tokens keeps blank answers in sync whenever possible.

- [ ] **Step 3: Expose page-safe APIs**

The composable should expose methods like:

```ts
setKind(kind)
hydrateFromQuestion(item)
hydrateFromLabStep(step)
buildQuestionDraftPayload(...)
buildLabStepDraftPayload(...)
```

- [ ] **Step 4: Verify the composable compiles in isolation**

Run: `npm run build`

Expected: shared editor state compiles before any page adopts it.

---

## Task 4: Extract shared typed-editor UI sections

**Files:**
- Create: `teaching-platform/frontend/src/views/teacher/components/typed-editor/TypedEditorShell.vue`
- Create: `teaching-platform/frontend/src/views/teacher/components/typed-editor/ObjectiveEditor.vue`
- Create: `teaching-platform/frontend/src/views/teacher/components/typed-editor/FillBlankEditor.vue`
- Create: `teaching-platform/frontend/src/views/teacher/components/typed-editor/SubjectiveEditor.vue`

- [ ] **Step 1: Build a shell component for validation and type switching**

`TypedEditorShell.vue` should provide:
- typed-editor heading/meta area
- validation banner region
- type selector slot or prop-driven type picker
- central content slot

- [ ] **Step 2: Build the objective editor**

`ObjectiveEditor.vue` should support:
- SINGLE
- MULTI
- JUDGE

and preserve the high-density card/selection interaction already proven in the question dialog.

- [ ] **Step 3: Build the fill-blank editor**

`FillBlankEditor.vue` should support:
- prompt editing
- insert token action
- blank answer list synced from prompt tokens

- [ ] **Step 4: Build the subjective editor**

`SubjectiveEditor.vue` should support:
- SHORT
- TEXT
- CODE

with label and helper text varying by `kind`, but no page-specific payload logic.

- [ ] **Step 5: Verify the shared editor pieces compile before page migration**

Run: `npm run build`

Expected: shared UI components compile cleanly before replacing page internals.

---

## Task 5: Migrate `Questions.vue` to the shared editor

**Files:**
- Modify: `teaching-platform/frontend/src/views/teacher/Questions.vue`

- [ ] **Step 1: Replace page-owned typed-editor state with shared composable usage**

Keep in `Questions.vue`:
- list/table/filter logic
- dialog open/close
- code preview generation
- difficulty/defaultScore/analysisText
- question create/update orchestration

Remove from `Questions.vue`:
- per-type editor parsing internals
- most validation internals
- raw per-type editor template duplication

- [ ] **Step 2: Preserve current question save behavior**

The dialog must still:
- auto-generate code on create
- support existing question create/update API
- keep current CODE compatibility handling

- [ ] **Step 3: Verify the question page before touching lab layout**

Run: `npm run build`

Manual checks:
- create/edit SINGLE
- create/edit MULTI
- create/edit JUDGE
- create/edit FILL
- create/edit SHORT
- create/edit CODE

Expected: question dialog behavior remains stable, but editor internals are now shared.

---

## Task 6: Migrate `LabSteps.vue` to the shared editor before layout overhaul

**Files:**
- Modify: `teaching-platform/frontend/src/views/teacher/LabSteps.vue`

- [ ] **Step 1: Replace page-owned rule editors with shared typed-editor sections**

Keep in `LabSteps.vue`:
- lab context fetch
- step list selection
- create/update/delete orchestration
- step-only fields (`stepNo`, `stepScore`, `allowPaste`)
- right-rail-specific summaries

Move out of `LabSteps.vue`:
- type-specific editor fragments
- parsing/validation/normalization internals

- [ ] **Step 2: Preserve current step save shape**

Saving must still build exactly the existing lab-step payload fields and use the current API client functions.

- [ ] **Step 3: Verify step CRUD before layout changes**

Run: `npm run build`

Manual checks:
- create step
- update step
- delete step
- switch between existing steps
- switch types within current step draft

Expected: lab-step behavior remains stable before the workbench layout refactor.

---

## Task 7: Add workbench height containment support

**Files:**
- Modify: `teaching-platform/frontend/src/layout/index.vue`
- Modify: `teaching-platform/frontend/src/views/teacher/LabSteps.vue`

- [ ] **Step 1: Add a page-scoped height boundary**

Make the minimum shell changes necessary so the lab steps page can lock itself to the available viewport height without breaking other teacher pages.

- [ ] **Step 2: Add `min-height: 0` / `overflow: hidden` boundaries**

Ensure the lab steps page root and workbench root can contain child scroll areas.

- [ ] **Step 3: Verify other pages are unaffected**

Run: `npm run build`

Expected: build passes and layout shell changes remain page-safe.

---

## Task 8: Refactor `LabSteps.vue` into the three-column workbench

**Files:**
- Modify: `teaching-platform/frontend/src/views/teacher/LabSteps.vue`

- [ ] **Step 1: Compress the header and remove oversized context cards**

Convert the current hero/context area into a compact header strip containing:
- return action
- lab title / summary chips
- current editing status
- primary save action

- [ ] **Step 2: Build left / center / right columns at one hierarchy level**

Required column roles:
- left: step rail
- center: typed-editor main region
- right: score / execution constraints / sync-to-question-bank

- [ ] **Step 3: Assign independent scroll containers**

The following must own their own `overflow: auto`:
- step list body
- center editor body
- right rail body

- [ ] **Step 4: Verify page no longer long-scrolls as a full document**

Run: `npm run build`

Manual checks:
- long prompt text in center only scrolls center region
- many steps only scroll left region
- many right-rail items only scroll right region

Expected: page behaves as a true workbench, not a stacked form.

---

## Task 9: Add “保存时同步创建到题库” flow

**Files:**
- Modify: `teaching-platform/frontend/src/views/teacher/LabSteps.vue`
- Reuse: `teaching-platform/frontend/src/api/questions.ts`
- Reuse: `teaching-platform/frontend/src/views/teacher/components/typed-editor/adapters/questionAdapter.ts`

- [ ] **Step 1: Add the right-rail checkbox and copy**

Add a right-column section with:
- checkbox: `保存时同步创建到题库`
- explanatory note: `仅本次同步创建，后续独立维护`

- [ ] **Step 2: Implement strict two-stage save sequencing**

Required order:
1. validate shared editor
2. save lab step
3. if checkbox checked, create question from same shared draft

- [ ] **Step 3: Implement partial-success messaging**

Cases:
- lab-step fail -> fail
- lab-step success, question create fail -> partial success warning/error
- both success -> combined success message

- [ ] **Step 4: Verify no ongoing linkage is implied**

UI copy and behavior must not suggest future synchronization or binding.

- [ ] **Step 5: Verify the sync flow**

Run: `npm run build`

Manual checks:
- unchecked: step only
- checked + success: step then question
- checked + second fail: step preserved, message accurate

---

## Task 10: Final verification and cleanup

**Files:**
- Modify only if defects are found during verification

- [ ] **Step 1: Run LSP diagnostics on changed frontend files**

Check:
- shared typed-editor files
- `Questions.vue`
- `LabSteps.vue`
- `layout/index.vue`

- [ ] **Step 2: Run final frontend build**

Run: `npm run build`

Expected: build passes.

- [ ] **Step 3: Manually verify spec coverage**

Confirm:
- no whole-page scrolling on lab steps page
- three columns visible and independently scrollable
- typed-editor interaction matches between question and lab-step pages
- lab-step payload remains separate from question payload
- sync-to-question-bank is create-on-save only

- [ ] **Step 4: Document any residual caveats**

If any compatibility compromise remains, record it in the final summary instead of hiding it.

---

## Verification Commands

```bash
cd teaching-platform/frontend
npm run build
```

LSP checks should be run on all changed frontend files before claiming completion.

---

## Execution Notes

- Do not change backend contracts in this implementation.
- Do not introduce persistent lab-step/question binding.
- Do not collapse question and lab-step payloads into a fake universal API shape.
- Prefer smaller focused shared modules over one oversized editor component.
- Migrate `Questions.vue` before doing the large `LabSteps.vue` layout refactor so the shared editor core is proven in a safer page first.

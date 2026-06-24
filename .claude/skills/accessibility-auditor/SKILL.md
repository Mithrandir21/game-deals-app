---
name: accessibility-auditor
description: Screen-by-screen Android accessibility review — semantics, contentDescription, focus order, touch target size, color contrast, TalkBack flow, and custom view accessibility delegates. Use whenever the user mentions "a11y", "accessibility", "TalkBack", "VoiceOver", "WCAG", "Accessibility Scanner", or wants to make an app usable for users with disabilities. Also use before launching a new screen or feature to consumers.
---

# Accessibility Auditor

Accessibility is mostly about making sure assistive tech can read, navigate, and operate the UI. The work is mechanical, but the checklist is long. This skill is a structured pass.

## When to use

Triggers: "a11y", "accessibility", "TalkBack", "Accessibility Scanner", "WCAG", "contentDescription", "semantics", "focus order".

For "this works fine but feels confusing in TalkBack", still use this skill — confusion usually has a concrete cause from the list below.

## Process

### Phase 1: Run the easy automated checks

Don't waste effort on what tools catch. Before reviewing manually:

1. **Accessibility Scanner** (install from Play Store, run on the device). Catches contrast, touch targets, missing labels.
2. **Lint**: Android Studio's accessibility lints (`ContentDescription`, `KeyboardInaccessibleWidget`, etc.).
3. **Compose UI tests**: `composeTestRule.onRoot().printToLog("a11y")` shows the semantics tree.

Collect the report. Manual review focuses on what these miss.

### Phase 2: Walk each screen with TalkBack

Turn on TalkBack and use the app without looking. Note where you get stuck.

For each screen, check:

**Reading order**
- Does TalkBack announce elements in the order a sighted user would read them (top-down, left-right)?
- Are headers announced as headers? In Compose: `Modifier.semantics { heading() }`.
- Are decorative elements skipped? They should have `contentDescription = null` or `Modifier.clearAndSetSemantics { }`.

**Labels**
- Every interactive element has a meaningful label — not "Button", not "Image", but what it does.
- Icon-only buttons have `contentDescription`. "Close", "Add", not the icon name.
- Form fields have associated labels (visible text + `Modifier.semantics { contentDescription = ... }` if the label isn't directly above).
- State is announced: toggles say "on/off", checkboxes say "checked/unchecked". Compose's `Switch`/`Checkbox` do this automatically; custom controls don't.

**Touch targets**
- Minimum 48.dp × 48.dp. Use `Modifier.minimumInteractiveComponentSize()` in Compose, or `android:minWidth`/`android:minHeight` in XML.
- Adjacent interactive areas should be visually separable — TalkBack focus should land on each cleanly.

**Color and contrast**
- Text contrast against background ≥ 4.5:1 (small text) or 3:1 (large text / icons). Accessibility Scanner checks this.
- State is not communicated by color alone. Add an icon, label, or border. (Red error text should also have an error icon or "Error:" prefix.)
- Test in dark mode separately.

**Focus management**
- Modal dialogs trap focus until dismissed.
- After navigation, focus lands on a sensible element (usually the screen title or the first interactive element).
- Custom focus order: in Compose, `Modifier.focusProperties { next = ...; previous = ... }`.

**Custom views and Composables**
- Custom views need an `AccessibilityDelegate` if they have multiple interactive areas (chip groups, custom switches, sliders).
- Compose: `Modifier.semantics { role = Role.Button; onClick { ... } }` for non-Button clickables.
- Group related semantics with `Modifier.semantics(mergeDescendants = true)` so TalkBack reads "Card titled X, subtitle Y, double tap to open" instead of three separate announcements.

**Live regions**
- For status updates that should be announced when they change (toast-like, validation), use `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`.

**Text sizing**
- Try the largest font size in system settings. Layout should not clip text, and important controls should not disappear.
- Use `sp` for text, `dp` for layout. Never hardcode text sizes in `dp`.

### Phase 3: Check less-common but high-impact areas

**Web content (WebViews)**
- Inherits web accessibility. Make sure the loaded page itself is accessible.
- `WebSettings.setAccessibilityEnabled(true)` is no-op on modern Android (default), but verify the content uses semantic HTML.

**Media**
- Videos: provide captions or transcripts.
- Audio: provide text alternative.

**Forms**
- Errors are announced (live region or directly on focus).
- Field requirements (e.g. "required", "8+ characters") are part of the label or announced on focus.

**Keyboards and switch access**
- Try tabbing through with a hardware keyboard. Every interactive element should be reachable.
- Avoid traps: drag-and-drop, custom gestures without keyboard equivalents.

**RTL languages**
- Layout flips correctly with `android:supportsRtl="true"`. Use `start`/`end` instead of `left`/`right`.
- Custom drawables that imply direction (arrows) flip too.

### Phase 4: Document findings

For each issue:

```
Screen: [name]
Issue: [what TalkBack announces vs. what it should]
Severity: Blocker / High / Medium / Low
Fix: [one or two lines of code or a brief description]
```

Severity guide:
- **Blocker**: User can't complete a primary task with assistive tech.
- **High**: User can complete the task but with significant confusion or extra steps.
- **Medium**: Awkward but not blocking.
- **Low**: Style / consistency.

### Phase 5: Prevent regressions

- Add Compose accessibility tests using `assertContentDescriptionEquals`, `assertIsSelected`, etc.
- Run Accessibility Scanner on each new screen before merging.
- Add a checklist to the PR template: "TalkBack works? Touch targets ≥ 48dp? Contrast checked?"

## Output

A report:

```
# Accessibility Review: [App / Screen]

## Summary
Scanned [N] screens. Found [X] blocker, [Y] high, [Z] medium issues.

## Strengths
[2–3 bullets]

## Issues
[Grouped by screen, sorted by severity]

## Recommended order
1. Blockers (this sprint)
2. Highs (next sprint)
3. Mediums (backlog)
```

## Common pitfalls

- **"My designer didn't include alt text."** Write it yourself based on purpose, not appearance.
- **Setting `contentDescription` to the visible label too.** TalkBack reads it twice. If the visible text is the label, leave `contentDescription` null.
- **Treating "Accessibility Scanner has no warnings" as done.** It catches a fraction. TalkBack walkthroughs find the rest.
- **`Modifier.clickable` without `Modifier.semantics { role = Role.Button }`.** TalkBack doesn't know it's a button.
- **Disabling accessibility for "performance".** Modern devices handle it fine. Disabling breaks usability.

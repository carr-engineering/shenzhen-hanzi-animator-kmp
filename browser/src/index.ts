import { createStrokeSession } from './engine.js';
import { firstCharacter } from './cache.js';
import { defaultLabels, defaultStyle } from './defaults.js';
import { createBinaryStrokeOrderProvider } from './provider.js';
import { drawCharacter, outlinePath } from './render.js';
import type { AnimatorOptions, AnimatorStyle, EngineSession, HanziAnimator, HanziPractice, Point, SessionState, StrokeOrder } from './types.js';
export * from './types.js';
export { createBinaryStrokeOrderProvider } from './provider.js';
export { defaultLabels, defaultStyle, defaultLightStyle, defaultDarkStyle, lightComponentColors, darkComponentColors } from './defaults.js';

/** Uses the shared Kotlin state machine for all playback and recognition decisions. */
export function createHanziAnimator(canvas: HTMLCanvasElement, initial: AnimatorOptions = {}): HanziAnimator {
  const context = canvas.getContext('2d'); if (!context) throw new Error('Canvas 2D is unavailable');
  let options = initial, provider = options.provider ?? createBinaryStrokeOrderProvider(options.data);
  let session: EngineSession | null = null, order: StrokeOrder | null = null, paths: Path2D[] = [];
  let generation = 0, controller: AbortController | null = null, disposed = false, frame = 0, animationBase: number | null = null;
  let activePointer: number | null = null, trace: Point[] = [], flashUntil = 0, hintStart = 0;
  const labels = () => ({...defaultLabels, ...options.labels});
  const style = (): AnimatorStyle => ({...defaultStyle, ...options.style});
  const assertOptions = () => {
    for (const [name, value] of [['maxCanvasSize', options.maxCanvasSize ?? 360], ['padding', options.padding ?? 12]] as const) if (!Number.isFinite(value) || value < 0 || (name === 'maxCanvasSize' && value === 0)) throw new RangeError(`${name} must be finite and ${name === 'padding' ? 'non-negative' : 'positive'}`);
    if (!Number.isInteger(options.hintAfterMistakes ?? 2) || (options.hintAfterMistakes ?? 2) <= 0) throw new RangeError('hintAfterMistakes must be a positive integer');
    for (const value of [style().revealWidth, style().traceWidth]) if (!Number.isFinite(value) || value <= 0) throw new RangeError('Stroke widths must be positive');
  };
  const applyLayout = () => { assertOptions(); canvas.style.display = 'block'; canvas.style.width = '100%'; canvas.style.maxWidth = `${options.maxCanvasSize ?? 360}px`; canvas.style.aspectRatio = '1'; canvas.style.marginInline = 'auto'; canvas.setAttribute('aria-label', labels().canvas); };
  const cancelTrace = () => {
    if (activePointer !== null && canvas.hasPointerCapture(activePointer)) canvas.releasePointerCapture(activePointer);
    activePointer = null; trace = [];
  };
  const state = () => session?.snapshot() ?? null;
  const draw = (now = performance.now()) => {
    const rect = canvas.getBoundingClientRect(), dpr = window.devicePixelRatio || 1;
    const w = Math.max(1, Math.round(rect.width * dpr)), h = Math.max(1, Math.round(rect.height * dpr));
    if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; cancelTrace(); }
    context.setTransform(dpr, 0, 0, dpr, 0, 0);
    const snapshot = state();
    canvas.style.touchAction = snapshot?.mode === 'Quiz' && !snapshot.complete ? 'none' : 'auto';
    drawCharacter(context, order, paths, snapshot, style(), rect.width, rect.height, options.padding ?? 12, trace, now < flashUntil, Math.min(1, Math.max(0, (now - hintStart) / 520)));
  };
  const changed = () => { if (disposed) return; options.onStateChange?.(state()); if (disposed) return; draw(); schedule(); };
  const tick = (now: number) => {
    frame = 0; if (disposed) return;
    const before = state();
    if (before?.playing) { animationBase ??= now - before.elapsedMillis; session!.advance(now - animationBase); options.onStateChange?.(state()); }
    if (flashUntil && now >= flashUntil) { flashUntil = 0; trace = []; }
    draw(now); schedule();
  };
  const schedule = () => { const s = state(); if (!frame && (s?.playing || flashUntil > performance.now() || (s?.hintVisible && hintStart + 520 > performance.now()))) frame = requestAnimationFrame(tick); };
  const mutate = (method: keyof EngineSession) => {
    if (!session || disposed) return; cancelTrace(); animationBase = null;
    const before = state(); (session[method] as () => unknown)();
    if (state()?.hintVisible && !before?.hintVisible) hintStart = performance.now();
    changed();
  };
  const point = (event: PointerEvent): Point => {
    const rect = canvas.getBoundingClientRect(), size = Math.max(1, Math.min(rect.width, rect.height) - (options.padding ?? 12) * 2);
    return [(event.clientX - rect.left - (rect.width - size) / 2) * 1024 / size, (event.clientY - rect.top - (rect.height - size) / 2) * 1024 / size];
  };
  const pointerDown = (event: PointerEvent) => {
    const s = state(); if (disposed || activePointer !== null || !event.isPrimary || event.button !== 0 || s?.mode !== 'Quiz' || s.complete) return;
    event.preventDefault(); activePointer = event.pointerId; trace = [point(event)]; flashUntil = 0; canvas.setPointerCapture(event.pointerId); draw();
  };
  const pointerMove = (event: PointerEvent) => {
    if (activePointer !== event.pointerId) return; event.preventDefault();
    const events = event.getCoalescedEvents?.() ?? [event]; for (const sample of events.length ? events : [event]) trace.push(point(sample)); draw();
  };
  const pointerUp = (event: PointerEvent) => {
    if (activePointer !== event.pointerId || !session) return; event.preventDefault(); trace.push(point(event));
    const before = state(), activeSession = session, request = generation, result = session.submitStroke(trace.flat()), after = state();
    activePointer = null; if (canvas.hasPointerCapture(event.pointerId)) canvas.releasePointerCapture(event.pointerId);
    if (!result.accepted && result.status !== 'EmptyInput') flashUntil = performance.now() + 220; else trace = [];
    if (result.hintVisible && !before?.hintVisible) hintStart = performance.now();
    options.onStrokeResult?.(result);
    if (!disposed && generation === request && session === activeSession && result.complete && !before?.complete && after) options.onComplete?.({totalMistakes: after.totalMistakes, mistakesPerStroke: after.mistakesPerStroke});
    changed();
  };
  const pointerCancel = (event: PointerEvent) => { if (activePointer === event.pointerId) { cancelTrace(); draw(); } };
  const resize = () => { cancelTrace(); draw(); };
  const observer = new ResizeObserver(resize); observer.observe(canvas); window.addEventListener('resize', resize);
  canvas.addEventListener('pointerdown', pointerDown); canvas.addEventListener('pointermove', pointerMove); canvas.addEventListener('pointerup', pointerUp); canvas.addEventListener('pointercancel', pointerCancel); canvas.addEventListener('lostpointercapture', pointerCancel);
  applyLayout(); draw();
  const api: HanziAnimator = {
    async setCharacter(text) {
      if (disposed) return; const character = firstCharacter(text); options = {...options, character};
      const request = ++generation; controller?.abort(); const abort = new AbortController(); controller = abort; cancelTrace(); session = null; order = null; paths = []; animationBase = null;
      if (frame) cancelAnimationFrame(frame); frame = 0; flashUntil = 0; changed();
      if (!character) { options.onLoadState?.('missing'); return; }
      options.onLoadState?.('loading');
      if (disposed || request !== generation) return;
      try {
        const result = await provider.load(character, abort.signal);
        if (disposed || request !== generation) return;
        if (!result) { options.onLoadState?.('missing'); return; }
        order = result; paths = result.strokes.map(outlinePath); session = createStrokeSession(result, options as Record<string, unknown>);
        options.onLoadState?.('loaded'); changed();
      } catch (error) {
        if (disposed || request !== generation || abort.signal.aborted) return;
        options.onLoadState?.('error', error);
      }
    },
    updateOptions(next) {
      if (disposed) return;
      options = {...options, ...next, style: {...options.style, ...next.style}, labels: {...options.labels, ...next.labels}}; applyLayout();
      const reload = next.provider !== undefined || next.data !== undefined;
      if (reload) provider = options.provider ?? createBinaryStrokeOrderProvider(options.data);
      if (next.animation || next.recognition || next.hintAfterMistakes !== undefined) { cancelTrace(); animationBase = null; if (order) session = createStrokeSession(order, options as Record<string, unknown>); }
      if (next.character !== undefined || reload) void api.setCharacter(options.character ?? ''); else changed();
    },
    state, play: () => mutate('play'), pause: () => mutate('pause'), stepForward: () => mutate('stepForward'), stepBackward: () => mutate('stepBackward'), revealAll: () => mutate('revealAll'), reset: () => mutate('reset'),
    startQuiz: () => mutate('startQuiz'), showHint: () => mutate('showHint'),
    setOutline(visible) { session?.setOutline(visible); changed(); },
    dispose() { if (disposed) return; disposed = true; generation++; controller?.abort(); cancelTrace(); if (frame) cancelAnimationFrame(frame); observer.disconnect(); window.removeEventListener('resize', resize); canvas.removeEventListener('pointerdown', pointerDown); canvas.removeEventListener('pointermove', pointerMove); canvas.removeEventListener('pointerup', pointerUp); canvas.removeEventListener('pointercancel', pointerCancel); canvas.removeEventListener('lostpointercapture', pointerCancel); canvas.style.touchAction = 'auto'; session = null; },
  };
  if (options.character) void api.setCharacter(options.character);
  return api;
}

/** Accessible DOM controls with 48px targets; supply custom labels/styles or use the canvas-only API. */
export function mountHanziPractice(container: HTMLElement, initial: AnimatorOptions = {}): HanziPractice {
  let options = initial;
  const element = document.createElement('section'); element.className = 'hanzi-practice';
  const canvas = document.createElement('canvas'); const controls = document.createElement('div'); controls.className = 'hanzi-controls'; controls.style.cssText = 'display:flex;flex-wrap:wrap;justify-content:center;gap:4px 2px;margin-top:8px';
  const status = document.createElement('p'); status.role = 'status'; status.setAttribute('aria-live', 'polite'); status.style.cssText = 'min-height:1.5em;margin:12px 0;text-align:center';
  const buttons = new Map<string, HTMLButtonElement>();
  const makeButton = (name: string, action: () => void) => { const button = document.createElement('button'); button.type = 'button'; button.style.cssText = 'min-height:48px;min-width:48px;max-width:100%;overflow-wrap:anywhere;padding:8px 12px;font:inherit;cursor:pointer'; button.addEventListener('click', action); controls.append(button); buttons.set(name, button); return button; };
  let loaded = false, loadState = 'missing';
  const label = () => ({...defaultLabels, ...options.labels});
  const updateControls = (state: SessionState | null) => {
    const labels = label();
    const minSize = options.controlMinSize ?? 48, horizontal = options.controlHorizontalGap ?? 2, vertical = options.controlVerticalGap ?? 4, gap = options.canvasControlGap ?? 8;
    if (![minSize, horizontal, vertical, gap].every(Number.isFinite) || minSize <= 0 || horizontal < 0 || vertical < 0 || gap < 0) throw new RangeError('Control sizes/gaps must be finite and non-negative (target size positive)');
    controls.style.gap = `${vertical}px ${horizontal}px`; controls.style.marginTop = `${gap}px`;
    for (const [key, button] of buttons) { const text = key === 'play' && state?.playing ? labels.pause : labels[key as keyof typeof labels]; if (button.textContent !== text) button.textContent = text; button.style.minWidth = `${minSize}px`; button.style.minHeight = `${minSize}px`; button.disabled = !loaded; }
    const play = buttons.get('play')!; const playText = state?.playing ? labels.pause : labels.play; if (play.textContent !== playText) play.textContent = playText;
    buttons.get('quiz')!.disabled = !loaded || !practiceCapable;
    buttons.get('hint')!.disabled = state?.mode !== 'Quiz' || state.complete;
    const outline = buttons.get('outline')!; outline.setAttribute('aria-pressed', String(state?.outlineVisible ?? true));
    buttons.get('retry')!.hidden = loadState !== 'error'; buttons.get('retry')!.disabled = false;
  };
  let practiceCapable = false;
  const wrap = (): AnimatorOptions => ({...options,
    onLoadState(state, error) {
      loadState = state; loaded = state === 'loaded';
      practiceCapable = loaded && !!api.state()?.supportsPractice;
      status.textContent = label()[state === 'loaded' ? 'ready' : state]; updateControls(api.state()); options.onLoadState?.(state, error);
    },
    onStateChange(state) { updateControls(state); options.onStateChange?.(state); },
    onStrokeResult(result) { status.textContent = result.accepted ? label().accepted : label().tryAgain; options.onStrokeResult?.(result); },
    onComplete(summary) { status.textContent = label().complete; options.onComplete?.(summary); }
  });
  // Mount before creating the animator so the initial backing resolution is correct.
  element.append(canvas, controls, status); container.append(element);
  const api = createHanziAnimator(canvas, {...wrap(), character: undefined});
  makeButton('play', () => api.state()?.playing ? api.pause() : api.play()); makeButton('previous', () => api.stepBackward()); makeButton('next', () => api.stepForward()); makeButton('reveal', () => api.revealAll()); makeButton('reset', () => { api.reset(); status.textContent = label().ready; }); makeButton('quiz', () => { api.startQuiz(); status.textContent = practiceCapable ? label().ready : label().practiceUnavailable; }); makeButton('hint', () => api.showHint()); makeButton('outline', () => api.setOutline(!api.state()?.outlineVisible)); makeButton('retry', () => void api.setCharacter(options.character ?? ''));
  updateControls(null);
  const mounted: HanziPractice = {...api, element, canvas,
    setCharacter(character) { options = {...options, character}; return api.setCharacter(character); },
    updateOptions(next) { options = {...options, ...next, labels: {...options.labels, ...next.labels}, style: {...options.style, ...next.style}}; const callbacks = wrap(); api.updateOptions({...next, labels: options.labels, style: options.style, onStateChange: callbacks.onStateChange, onLoadState: callbacks.onLoadState, onStrokeResult: callbacks.onStrokeResult, onComplete: callbacks.onComplete}); updateControls(api.state()); },
    dispose() { api.dispose(); element.remove(); }
  };
  if (initial.character) void mounted.setCharacter(initial.character);
  return mounted;
}

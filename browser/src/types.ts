/** Coordinates are normalized y-down, in a 1024 × 1024 glyph viewport. */
export type Point = readonly [number, number];
/** Command codes: 0=MOVE (2 coordinates), 1=LINE (2), 2=QUAD (4), 3=CUBIC (6), 4=CLOSE (0). All coordinates are absolute. */
export interface Stroke { readonly commands: readonly number[]; readonly coordinates: readonly number[]; readonly median: readonly Point[]; readonly isRadical: boolean; readonly componentIndex: number | null }
/** Supply normalized command buffers or obtain an order from the binary provider. Treat arrays as immutable. */
export interface StrokeOrder { readonly character: string; readonly strokes: readonly Stroke[]; readonly components: readonly string[] | null; readonly supportsPractice: boolean }
export interface EngineArchive { readonly characters: string[]; readonly byteLength: number; strokeOrder(character: string): StrokeOrder | null }
export interface SessionState { mode: 'Idle' | 'Animating' | 'Quiz'; playing: boolean; supportsPractice: boolean; elapsedMillis: number; currentStroke: number; progress: number[]; outlineVisible: boolean; hintVisible: boolean; complete: boolean; totalMistakes: number; mistakesPerStroke: number[] }
export interface RecognitionResult { accepted: boolean; status: string; complete: boolean; hintVisible: boolean; totalMistakes: number }
export interface EngineSession {
  snapshot(): SessionState; play(): void; pause(): void; advance(elapsedMillis: number): void;
  stepForward(): void; stepBackward(): void; revealAll(): void; reset(): void; startQuiz(): boolean;
  submitStroke(points: number[]): RecognitionResult; showHint(): void; setOutline(visible: boolean): void;
}
export type ByteLoader = (resourceKey: string, signal: AbortSignal) => Promise<Uint8Array>;
export interface StrokeOrderProvider { load(character: string, signal: AbortSignal): Promise<StrokeOrder | null> }
export interface BinaryProviderOptions { baseUrl?: string; shardCount?: number; loadBytes?: ByteLoader; cacheBytes?: number; cacheCharacters?: number }
export interface AnimatorStyle { background: string; outline: string; stroke: string; radical: string; hint: string; trace: string; error: string; grid: string; showGrid: boolean; componentColors: readonly string[]; revealWidth: number; traceWidth: number; medianStartExtension: number }
export interface AnimatorLabels { canvas: string; play: string; pause: string; previous: string; next: string; reveal: string; reset: string; quiz: string; hint: string; outline: string; loading: string; missing: string; error: string; retry: string; ready: string; complete: string; accepted: string; tryAgain: string; practiceUnavailable: string }
export interface AnimationOptions { strokeDurationMillis?: number; minStrokeDurationMillis?: number; maxStrokeDurationMillis?: number; speedMultiplier?: number; strokeSpeedViewportUnitsPerSecond?: number; delayBetweenStrokesMillis?: number; loop?: boolean }
export interface RecognitionOptions { leniency?: number; startTolerance?: number; endTolerance?: number; averageDistanceTolerance?: number; outlineVisibleDistanceMultiplier?: number; maxDistanceTolerance?: number; coverageDistanceTolerance?: number; minCoverage?: number; minLengthRatio?: number; maxLengthRatio?: number; frechetTolerance?: number; sampleCount?: number; laterStrokeDistanceAdvantage?: number }
export interface AnimatorOptions {
  character?: string; provider?: StrokeOrderProvider; data?: BinaryProviderOptions; style?: Partial<AnimatorStyle>; labels?: Partial<AnimatorLabels>;
  maxCanvasSize?: number; padding?: number; controlMinSize?: number; controlHorizontalGap?: number; controlVerticalGap?: number; canvasControlGap?: number; hintAfterMistakes?: number; animation?: AnimationOptions; recognition?: RecognitionOptions;
  onStateChange?: (state: SessionState | null) => void; onLoadState?: (state: 'loading' | 'missing' | 'loaded' | 'error', error?: unknown) => void;
  onStrokeResult?: (result: RecognitionResult) => void; onComplete?: (summary: { totalMistakes: number; mistakesPerStroke: number[] }) => void;
}
export interface HanziAnimator {
  setCharacter(character: string): Promise<void>; updateOptions(options: Partial<AnimatorOptions>): void; state(): SessionState | null;
  play(): void; pause(): void; stepForward(): void; stepBackward(): void; revealAll(): void; reset(): void; startQuiz(): void; showHint(): void; setOutline(visible: boolean): void; dispose(): void;
}
export interface HanziPractice extends HanziAnimator { readonly element: HTMLElement; readonly canvas: HTMLCanvasElement }

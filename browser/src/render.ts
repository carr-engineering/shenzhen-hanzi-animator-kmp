import type { AnimatorStyle, Point, SessionState, Stroke, StrokeOrder } from './types.js';
export function outlinePath(stroke: Stroke): Path2D {
  const path = new Path2D(); let offset = 0;
  for (const command of stroke.commands) {
    const c = stroke.coordinates;
    switch (command) {
      case 0: path.moveTo(c[offset]!, c[offset + 1]!); offset += 2; break;
      case 1: path.lineTo(c[offset]!, c[offset + 1]!); offset += 2; break;
      case 2: path.quadraticCurveTo(c[offset]!, c[offset + 1]!, c[offset + 2]!, c[offset + 3]!); offset += 4; break;
      case 3: path.bezierCurveTo(c[offset]!, c[offset + 1]!, c[offset + 2]!, c[offset + 3]!, c[offset + 4]!, c[offset + 5]!); offset += 6; break;
      case 4: path.closePath(); break;
      default: throw new Error('Unsupported outline command');
    }
  }
  return path;
}
function line(ctx: CanvasRenderingContext2D, points: readonly Point[], progress: number, extension = 0) {
  if (points.length < 2 || progress <= 0) return;
  const first = points[0]!, second = points[1]!;
  const dx = second[0] - first[0], dy = second[1] - first[1], length = Math.hypot(dx, dy);
  const start: Point = length ? [first[0] - dx / length * extension, first[1] - dy / length * extension] : first;
  const all = extension ? [start, ...points] : points;
  let total = 0; for (let i = 1; i < all.length; i++) total += Math.hypot(all[i]![0] - all[i - 1]![0], all[i]![1] - all[i - 1]![1]);
  let remaining = total * progress; ctx.beginPath(); ctx.moveTo(all[0]![0], all[0]![1]);
  for (let i = 1; i < all.length; i++) {
    const a = all[i - 1]!, b = all[i]!, segment = Math.hypot(b[0] - a[0], b[1] - a[1]);
    if (remaining >= segment) { ctx.lineTo(b[0], b[1]); remaining -= segment; }
    else { const ratio = segment ? remaining / segment : 0; ctx.lineTo(a[0] + (b[0] - a[0]) * ratio, a[1] + (b[1] - a[1]) * ratio); break; }
  }
  ctx.stroke();
}
export function drawCharacter(ctx: CanvasRenderingContext2D, order: StrokeOrder | null, paths: Path2D[], state: SessionState | null, style: AnimatorStyle, width: number, height: number, padding: number, trace: readonly Point[], flash: boolean, hintProgress: number) {
  ctx.clearRect(0, 0, width, height); ctx.fillStyle = style.background; ctx.fillRect(0, 0, width, height);
  const size = Math.max(1, Math.min(width, height) - padding * 2), scale = size / 1024;
  ctx.save(); ctx.translate((width - size) / 2, (height - size) / 2); ctx.scale(scale, scale);
  if (style.showGrid) { ctx.strokeStyle = style.grid; ctx.lineWidth = 2 / scale; ctx.setLineDash([8 / scale, 5 / scale]); ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(1024, 1024); ctx.moveTo(1024, 0); ctx.lineTo(0, 1024); ctx.moveTo(512, 0); ctx.lineTo(512, 1024); ctx.moveTo(0, 512); ctx.lineTo(1024, 512); ctx.stroke(); ctx.setLineDash([]); }
  if (order && state) order.strokes.forEach((stroke, i) => {
    const path = paths[i]!, progress = state.progress[i] ?? 0;
    if (state.outlineVisible) { ctx.fillStyle = style.outline; ctx.fill(path); }
    let color = style.stroke;
    if (order.components && style.componentColors.length) color = stroke.componentIndex === null ? style.stroke : style.componentColors[stroke.componentIndex] ?? style.stroke;
    else if (stroke.isRadical) color = style.radical;
    if (progress >= 1) { ctx.fillStyle = color; ctx.fill(path); }
    else if (progress > 0) { ctx.save(); ctx.clip(path); ctx.strokeStyle = color; ctx.lineWidth = style.revealWidth; ctx.lineCap = 'round'; ctx.lineJoin = 'round'; line(ctx, stroke.median, progress, style.medianStartExtension); ctx.restore(); }
    if (state.mode === 'Quiz' && state.hintVisible && i === state.currentStroke) { ctx.save(); ctx.clip(path); ctx.strokeStyle = style.hint; ctx.lineWidth = style.revealWidth; ctx.lineCap = 'round'; ctx.lineJoin = 'round'; line(ctx, stroke.median, hintProgress, style.medianStartExtension); ctx.restore(); }
  });
  if (state?.complete) { ctx.strokeStyle = style.radical; ctx.lineWidth = 4 / scale; ctx.strokeRect(0, 0, 1024, 1024); }
  ctx.strokeStyle = flash ? style.error : style.trace; ctx.lineWidth = style.traceWidth; ctx.lineCap = 'round'; ctx.lineJoin = 'round'; line(ctx, trace, 1); ctx.restore();
}

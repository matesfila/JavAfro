import { useRef, type PointerEvent } from 'react';

/** The shortest drag (horizontal, or vertical when enabled) that counts as a swipe. */
export const SWIPE_MIN_PX = 50;
/** A click this soon after a swipe is the swipe's own click, not a tap. */
const SWIPE_CLICK_MS = 500;

/**
 * Horizontal swipe on a button: right calls `onSwipe(1)`, left `onSwipe(-1)`. With
 * `onVerticalSwipe`, up calls `onVerticalSwipe(1)` and down `onVerticalSwipe(-1)`.
 * Spread `handlers` on the button and check `consumeClick()` in its click handler,
 * because the browser still fires a click at the end of the swipe.
 * The button needs `touch-action: pan-y` (`none` for vertical swipes), otherwise the browser
 * takes over the gesture.
 */
export function useSwipe(onSwipe: (direction: 1 | -1) => void, onVerticalSwipe?: (direction: 1 | -1) => void) {
  const start = useRef<{ x: number; y: number } | null>(null);
  const swipedAt = useRef(Number.NEGATIVE_INFINITY);
  const handler = useRef(onSwipe); handler.current = onSwipe;
  const verticalHandler = useRef(onVerticalSwipe); verticalHandler.current = onVerticalSwipe;
  const api = useRef({
    handlers: {
      onPointerDown(event: PointerEvent<HTMLElement>) {
        start.current = { x: event.clientX, y: event.clientY };
        // Keep receiving the pointer when the finger leaves the button during the swipe.
        try { event.currentTarget.setPointerCapture(event.pointerId); } catch { /* not supported */ }
      },
      onPointerUp(event: PointerEvent<HTMLElement>) {
        const from = start.current;
        start.current = null;
        if (!from) return;
        const dx = event.clientX - from.x, dy = event.clientY - from.y;
        if (Math.abs(dx) >= SWIPE_MIN_PX && Math.abs(dx) > 2 * Math.abs(dy)) {
          swipedAt.current = Date.now();
          handler.current(dx > 0 ? 1 : -1);
        } else if (verticalHandler.current && Math.abs(dy) >= SWIPE_MIN_PX && Math.abs(dy) > 2 * Math.abs(dx)) {
          swipedAt.current = Date.now();
          // Screen y grows downwards, so a swipe up has a negative dy.
          verticalHandler.current(dy < 0 ? 1 : -1);
        }
      },
      onPointerCancel() { start.current = null; },
    },
    /** True (once) when the click only ends a swipe. */
    consumeClick() {
      if (Date.now() - swipedAt.current >= SWIPE_CLICK_MS) return false;
      swipedAt.current = Number.NEGATIVE_INFINITY;
      return true;
    },
  });
  return api.current;
}

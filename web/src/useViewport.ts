import { useEffect, useState } from 'react';

// Keep this query in sync with the compact layout rules in style.css.
const compactQuery = '(max-width: 680px), (max-width: 960px) and (max-height: 500px)';
const touchInputQuery = '(pointer: coarse)';

function viewport() {
  const visual = window.visualViewport;
  // A zoomed visual viewport is smaller even when the keyboard is closed.
  const height = visual ? visual.height * visual.scale : window.innerHeight;
  return { compact: window.matchMedia(compactQuery).matches, height,
    touchInput: window.matchMedia(touchInputQuery).matches,
    keyboard: window.innerHeight - height > 120 };
}

/** visualViewport also shrinks for the mobile keyboard, unlike iOS viewport units. */
export function useViewport() {
  const [size, setSize] = useState(viewport);
  useEffect(() => {
    const update = () => setSize(viewport());
    const media = window.matchMedia(compactQuery);
    const touchInput = window.matchMedia(touchInputQuery);
    const meta = document.querySelector<HTMLMetaElement>('meta[name="viewport"]');
    const normalViewport = meta?.content;
    let restoreTimer: number | undefined;
    function restoreZoomLimits() {
      window.clearTimeout(restoreTimer);
      if (meta && normalViewport) meta.content = normalViewport;
    }
    function rotate() {
      if (!meta || !normalViewport || !media.matches) return;
      // Request the initial scale during rotation, then allow normal pinch zoom again.
      meta.content = normalViewport + ', minimum-scale=1, maximum-scale=1';
      window.clearTimeout(restoreTimer);
      restoreTimer = window.setTimeout(restoreZoomLimits, 500);
    }
    const orientation = window.screen.orientation;
    if (orientation) orientation.addEventListener('change', rotate);
    else window.addEventListener('orientationchange', rotate);
    media.addEventListener('change', update);
    touchInput.addEventListener('change', update);
    window.addEventListener('resize', update);
    window.visualViewport?.addEventListener('resize', update);
    update();
    return () => {
      if (orientation) orientation.removeEventListener('change', rotate);
      else window.removeEventListener('orientationchange', rotate);
      if (restoreTimer !== undefined) restoreZoomLimits();
      media.removeEventListener('change', update);
      touchInput.removeEventListener('change', update);
      window.removeEventListener('resize', update);
      window.visualViewport?.removeEventListener('resize', update);
    };
  }, []);
  return size;
}

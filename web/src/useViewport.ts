import { useEffect, useState } from 'react';

// Keep this query in sync with the compact layout rules in style.css.
const compactQuery = '(max-width: 680px), (max-width: 960px) and (max-height: 500px)';

function viewport() {
  const height = window.visualViewport?.height ?? window.innerHeight;
  return { compact: window.matchMedia(compactQuery).matches, height,
    keyboard: window.innerHeight - height > 120 };
}

/** visualViewport also shrinks for the mobile keyboard, unlike iOS viewport units. */
export function useViewport() {
  const [size, setSize] = useState(viewport);
  useEffect(() => {
    const update = () => setSize(viewport());
    const media = window.matchMedia(compactQuery);
    media.addEventListener('change', update);
    window.addEventListener('resize', update);
    window.visualViewport?.addEventListener('resize', update);
    update();
    return () => {
      media.removeEventListener('change', update);
      window.removeEventListener('resize', update);
      window.visualViewport?.removeEventListener('resize', update);
    };
  }, []);
  return size;
}

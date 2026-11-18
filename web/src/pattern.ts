/** Spaces separate note tokens, never pulses. Compact notation remains character based. */
export function patternBars(source: string): string[][] {
  const spaced = /\s/.test(source);
  return source.split('|').map(bar => {
    const trimmed = bar.trim();
    if (!trimmed) throw new Error('Pattern: prázdny takt.');
    const notes = spaced ? trimmed.split(/\s+/).map(note => note === '..' ? '.' : note) : [...trimmed];
    for (const note of notes) {
      if (!/^(?:[a-zA-Z][a-zA-Z0-9]*|\.)$/.test(note)) throw new Error(`Neplatná nota „${note}“.`);
    }
    return notes;
  });
}

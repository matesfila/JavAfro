import manifest from '../../src/main/resources/samples/djembe.json';
import type { Instrument } from './rhythm';

const drums = { dundun: 'dundun.wav', sangban: 'sangban.wav', kensereni: 'kenkeni.wav' };
const notes: Record<string, { bank: string; gain: number; probability: number }> = manifest.notes;
const banks: Record<string, string[]> = manifest.banks;
export interface Sound { sample: string; gain: number; rate: number }

export function supportsNote(instrument: Instrument, note: string): boolean {
  return instrument === 'djembe' ? Object.hasOwn(notes, note) : note === 'x';
}
export function samplesForInstrument(instrument: Instrument): string[] {
  return instrument === 'djembe' ? Object.values(banks).flat() : [drums[instrument]];
}

/** A voice's sample, dynamics and pitch stay fixed for its entire decay. */
export function selectSound(instrument: Instrument, symbol: string, humanize: number,
                            random: () => number = Math.random): Sound | null {
  if (!supportsNote(instrument, symbol)) throw new Error(`Neznámy úder ${symbol} pre ${instrument}.`);
  if (instrument !== 'djembe') return { sample: drums[instrument], gain: 1, rate: 1 };
  const note = notes[symbol];
  if (note.probability < 1 && random() >= note.probability) return null;
  const variants = banks[note.bank];
  const sample = variants[Math.min(variants.length - 1, Math.floor(random() * variants.length))];
  const amount = humanize / 100;
  return { sample,
    gain: (note.gain + (amount ? (random() * 2 - 1) * manifest.dynamic * amount : 0)) * manifest.outputGain,
    rate: 1 + (amount ? (random() * 2 - 1) * manifest.pitch * amount : 0) };
}

import afroSource from '../../src/main/resources/samples/afroplayer-samples.yaml?raw';
import { parse } from 'yaml';
import type { Instrument } from './rhythm';
import { HUMANIZE_PERCENT_MAX } from './audio-constants';
import { config } from './config';

interface AfroManifest {
  banks: Record<string, { samples: string[]; gain: number; rate: number; pan: number }>;
  instruments: Record<string, { bell?: string;
    notes: Record<string, { bank: string; probability: number; gain: number }> }>;
}
const afro: AfroManifest = parse(afroSource, { version: '1.1', uniqueKeys: true });
const freesound: Partial<Record<Instrument, string>> = {
  dundun_freesound: 'jimmyjames/dundun.wav', sangban_freesound: 'jimmyjames/sangban.wav', kensereni_freesound: 'jimmyjames/kenkeni.wav',
};
const freesoundNotes: Partial<Record<Instrument, string>> = {
  dundun_freesound: 'Dd', sangban_freesound: 'Ss', kensereni_freesound: 'Kk',
};
const freesoundBell = (instrument: Instrument) => instrument.replace('_freesound', '_bell') as Instrument;
const canonical = (instrument: Instrument) => instrument === 'kenken' ? 'kensereni'
  : instrument === 'kenken_bell' ? 'kensereni_bell' : instrument;
export interface Sound { sample: string; gain: number; rate: number; pan?: number }

export function supportsNote(instrument: Instrument, note: string): boolean {
  const definition = afro.instruments[canonical(instrument)];
  return definition ? Object.hasOwn(definition.notes, note)
    : note.length === 1 && !!freesound[instrument] && (note === 'x' || !!freesoundNotes[instrument]?.includes(note));
}
export function samplesForInstrument(instrument: Instrument): string[] {
  const definition = afro.instruments[canonical(instrument)];
  if (!definition) return freesound[instrument]
    ? [freesound[instrument]!, ...samplesForInstrument(freesoundBell(instrument))] : [];
  const bankIds = new Set(Object.values(definition.notes).map(note => note.bank));
  if (definition.bell) bankIds.add(definition.bell);
  return [...bankIds].flatMap(id => afro.banks[id].samples);
}

/** Primary voice compatibility helper. Scheduling uses selectSounds to include automatic bells. */
export function selectSound(instrument: Instrument, symbol: string, humanize: number,
                            random: () => number = Math.random): Sound | null {
  return selectSounds(instrument, symbol, humanize, random)[0] ?? null;
}

/** One successful drum hit creates its drum and bell together; skipped hits create neither. */
export function selectSounds(instrument: Instrument, symbol: string, humanize: number,
                             random: () => number = Math.random): Sound[] {
  if (!supportsNote(instrument, symbol)) throw new Error('Neznámy úder ' + symbol + ' pre ' + instrument + '.');
  if (!Number.isFinite(humanize) || humanize < 0 || humanize > HUMANIZE_PERCENT_MAX) throw new Error('Humanize musí byť 0–100.');
  const amount = humanize / HUMANIZE_PERCENT_MAX;
  const variation = (limit: number) => amount ? (random() * 2 - 1) * limit * amount : 0;
  const choose = (files: string[]) => files[Math.min(files.length - 1, Math.floor(random() * files.length))];
  const definition = afro.instruments[canonical(instrument)];
  if (!definition) return symbol === 'x'
    ? selectSounds(freesoundBell(instrument), 'x', humanize, random)
    : [{ sample: freesound[instrument]!, gain: 1, rate: 1 }];
  const note = definition.notes[symbol];
  if (note.probability < 1 && random() >= note.probability) return [];
  const voice = (id: string, noteGain = 1): Sound => {
    const bank = afro.banks[id];
    return { sample: choose(bank.samples), gain: bank.gain * noteGain + variation(config.humanizeDynamic),
      rate: bank.rate + variation(config.humanizePitch), pan: bank.pan };
  };
  const result = [voice(note.bank, note.gain)];
  if (definition.bell && symbol !== 'x') result.push(voice(definition.bell));
  return result;
}

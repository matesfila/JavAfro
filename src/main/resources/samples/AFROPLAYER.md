# Original AfroPlayer instruments

Source: the user's `C:/Projects/AfroPlayer/src/core/afp-sampler.rb` and the FLAC
files in `C:/Projects/AfroPlayer/resources/samples/`.

`afroplayer-samples.yaml` is shared by Java and Web Audio. It contains all original
bank gains, rates, pan positions and note probabilities for the migrated instruments.
It references 83 recordings:

| Source directory | Used banks | Recordings |
| --- | --- | ---: |
| `djembe_tomasmarek` | Djembe bass/tone/slap/closed tone/closed slap/alternate slap | 29 |
| `dunduns_rudo` | Dundun open/closed, sangban open/closed, kenkeni open/closed, their three open bells | 47 |
| `djembe_kontakt` | DJEMBEBASS2, DJEMBE2/3, DJEMBESLAP2/3/4, DJEMBESLAP1 | 7 |

Conversion: mono signed PCM WAV, 44,100 Hz, 16 bits; no normalization, trimming,
compression or baked-in speed changes. The original rate values are applied during playback.
Decoded audio is cached. Each hit selects its multisample, dynamic offset and rate
once, then keeps them throughout the entire tail. Bells retain their own tails and
independent multisample/dynamic/rate choices.

As in Ruby, global `HUMANIZE_DYNAMIC` (0.07) and `HUMANIZE_PITCH` (0.005)
are playback settings, configured as `playback.humanizeDynamic` and
`playback.humanizePitch` in `javafro.yaml`. The current humanize percentage
scales both offsets. Base `gain` (Ruby `amp`), `rate` and `pan` belong to each
sample bank; there is no additional `outputGain` in the sample manifest.

Each sampler requires an explicit note through `voice(note, humanize)`. Only the
returned voice can render samples, report its length or play a tone; the sampler
itself has no default sound.

For bass drums, `X` and the compatibility alias `x` play an open stroke, `i/I`
play a closed stroke, and `A/B/C` play an open stroke with probability 12/40/80%.
Every successful drum stroke also plays its matching bell at the same timestamp.
If a probability stroke is skipped, its bell is skipped too. `b` plays only the bell.
Separate bell tracks accept `b`, `x` or `X` and do not add another bell.

`djembe2` keeps original `D/o/x/i` (bass/tone/slap/closed) and `E/p/y` (60%),
`F/q/z` (12%). The closed sound is the original DJEMBESLAP1 recording, not a
substitution from the Tomáš Marek djembe. Modern `d/t/s` and `D/T/S` aliases are
also accepted, with lowercase at 70% of the corresponding accent gain.

The Java engine currently mixes mono. Original pan positions are retained in the
manifest; the web engine applies them with StereoPannerNode. All other gain/rate,
sample, note and bell behavior is shared.

These files are separate from the CC0 Freesound recordings. No redistribution
license was found alongside these source FLACs. Keep their original provenance
and conditions; do not relabel them as CC0. The djembe_kontakt directory name is
source provenance, not a claim of a redistribution license.

To repeat conversion of the 54 dundun/sangban/kensereni/bell/djembe2 recordings
(the 29 Tomáš Marek djembe recordings were imported separately; requires local FFmpeg and `npm ci` in `web/`):

```powershell
.\tools\import-afroplayer-samples.ps1 -SourceRoot C:/Projects/AfroPlayer/resources/samples -Ffmpeg 'C:/usr/Subtitle Edit/ffmpeg/ffmpeg.exe'
```

The old `DundunInstrument`, `SangbanInstrument`, `KensereniInstrument` and their
CC0 WAVs are unchanged, exposed in YAML as `dundun_freesound`, `sangban_freesound`,
and `kensereni_freesound`.

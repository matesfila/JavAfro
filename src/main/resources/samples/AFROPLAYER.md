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

For bass drums, `D/d` (dundun), `S/s` (sangban), `K/k` (kensereni) play an open stroke only on their own track; `i/I`
play a closed stroke, and `A/B/C` play an open stroke with probability 12/40/80%.
Every successful drum stroke also plays its matching bell at the same timestamp.
If a probability stroke is skipped, its bell is skipped too. `x` plays only the bell.
Separate bell tracks accept `x` and do not add another bell.

`djembe2` uses `B/o/S/i` (bass/tone/slap/closed) and `E/p/y` (60%),
`F/q/z` (12%). The closed sound is the original DJEMBESLAP1 recording, not a
substitution from the Tomáš Marek djembe. Modern `b/t/s` and `B/T/S` symbols are
also accepted, with lowercase at 70% of the corresponding accent gain.

The Java engine currently mixes mono. Original pan positions are retained in the
manifest; the web engine applies them with StereoPannerNode. All other gain/rate,
sample, note and bell behavior is shared.

## Recording credits and license status

Provenance supplied by Matúš Fila on 2026-11-06:

| Recordings | Recording by | Performer | Instruments |
| --- | --- | --- | --- |
| 29 djembe recordings (`djembe_tomasmarek`) | Matúš Fila | Tomáš Marek | Tomáš Marek's djembe |
| 47 bass drum and bell recordings (`dunduns_rudo`) | Matúš Fila | Matúš Fila | Instruments belonging to the Prague group Aneboafro; Matúš Fila was a member at the time |
| 7 `djembe2` recordings (`djembe_kontakt`) | Unknown | Unknown | Unknown |

Matúš Fila personally recorded the first two sets. The historical directory
names are retained for traceability; `dunduns_rudo` is not a recording credit.
This provenance statement does not specify a public sample reuse license or
document Tomáš Marek's consent to public use of his recorded performance.
Do not label these recordings as CC0 or infer an unrestricted reuse license.

The origin of `djembe2` is not remembered. At Matúš Fila's request, these seven
recordings are excluded from any license granted for his recordings. No license
is granted for `djembe2` by this document. The `djembe_kontakt` name does not
establish its source or license. This exclusion is documentation only: the
files remain in the application and its distribution/offline bundle.

The separate JIMMYJAMES112 Freesound recordings retain their documented CC0
status in [README.md](README.md).

To repeat conversion of the 54 dundun/sangban/kensereni/bell/djembe2 recordings
(the 29 Tomáš Marek djembe recordings were imported separately; requires local FFmpeg and `npm ci` in `web/`):

```powershell
.\tools\import-afroplayer-samples.ps1 -SourceRoot C:/Projects/AfroPlayer/resources/samples -Ffmpeg 'C:/usr/Subtitle Edit/ffmpeg/ffmpeg.exe'
```

The old `DundunInstrument`, `SangbanInstrument`, `KensereniInstrument` and their
CC0 WAV audio is unchanged, exposed in YAML as `dundun_freesound`, `sangban_freesound`,
and `kensereni_freesound`.

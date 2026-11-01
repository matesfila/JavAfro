"""Convert the three documented Freesound MP3 previews to bundled PCM WAVs.

Requires numpy and soundfile (libsndfile with MP3 support).
Usage: python tools/prepare-drum-samples.py <directory-containing-ID.mp3-files>
"""
from pathlib import Path
import sys
import numpy as np
import soundfile as sf

source = Path(sys.argv[1])
destination = Path(__file__).resolve().parents[1] / "src/main/resources/samples"
destination.mkdir(parents=True, exist_ok=True)
for name, sound_id in [("dundun", 170180), ("sangban", 170177), ("kenkeni", 170179)]:
    samples, rate = sf.read(source / f"{sound_id}.mp3", always_2d=True)
    assert rate == 44100, f"Unexpected sample rate: {rate}"
    mono = samples.mean(axis=1)
    peak = float(np.max(np.abs(mono)))
    assert peak > 0, f"Silent sample: {name}"
    # Remove leading encoding/silence delay, leaving 2 ms before the audible attack.
    active = np.flatnonzero(np.abs(mono) >= peak * 0.01)
    trim = max(0, int(active[0]) - round(rate * 0.002))
    mono = mono[trim:] * (0.95 / peak)
    sf.write(destination / f"{name}.wav", mono, rate, subtype="PCM_16")
    print(f"{name}: {len(mono) / rate:.3f}s; trimmed {trim} samples; source peak {peak:.4f}; output peak 0.95")

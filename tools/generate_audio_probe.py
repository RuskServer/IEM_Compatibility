"""定位テスト用の短い広帯域ノイズ。外部素材なし。numpy/soundfile が必要。"""
from pathlib import Path
import numpy as np
import soundfile as sf
rate = 48000
n = int(rate * .45)
rng = np.random.default_rng(5313)
envelope = np.minimum(np.arange(n) / (rate * .025), 1) * np.minimum(np.arange(n)[::-1] / (rate * .08), 1)
mono = np.clip(rng.normal(0, .15, n), -.6, .6) * envelope
# 相関のあるステレオ。空間化前後で音量を比較しやすく、逆相による打ち消しを避ける。
stereo = np.column_stack((mono, mono * .9))
root = Path(__file__).resolve().parents[1] / 'src/client/resources/assets/iem_compatibility/sounds'
root.mkdir(parents=True, exist_ok=True)
for name, data in [('mono', mono), ('stereo', stereo)]:
    sf.write(root / f'audio_probe_{name}.ogg', data, rate, format='OGG', subtype='VORBIS')

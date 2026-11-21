#!/usr/bin/env python3
"""Phonétise des segments de texte français pour la voix neuronale (Piper, voix fr_FR-siwis).

Lit sur l'entrée standard un segment par ligne ; écrit sur la sortie standard, dans le même ordre, une ligne par
segment : ses phrases en phonèmes séparées par des tabulations.

Utilisé par `:pack-builder` à la fabrication des packs (voir `--phonemizer`), jamais dans l'app : le paquet `piper-tts`
embarque espeak-ng (GPL-3), qui reste donc dans l'outil de fabrication.

    pip install piper-tts
    python3 tools/phonemize.py --model fr_FR-siwis-medium.onnx < segments.txt
"""
import argparse
import sys

from piper import PiperVoice


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--model", required=True, help="voix Piper (.onnx, avec son .onnx.json à côté)")
    args = parser.parse_args()

    voice = PiperVoice.load(args.model)
    for line in sys.stdin:
        text = line.rstrip("\n")
        sentences = ["".join(sentence) for sentence in voice.phonemize(text)] if text.strip() else []
        sys.stdout.write("\t".join(sentences) + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    main()

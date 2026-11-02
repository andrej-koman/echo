package dev.andrej.echo.speech.speakers

import dev.andrej.echo.ai.ModelSpec

/** pyannote segmentation 3.0, int8. Pinned to a commit of an ungated HuggingFace repo. */
val SPEAKER_SEGMENTATION = ModelSpec(
    url = "https://huggingface.co/csukuangfj/sherpa-onnx-pyannote-segmentation-3-0/resolve/" +
        "9403a6902bb58e3d5ae8c7e77c3422de279db2e0/model.int8.onnx",
    fileName = "pyannote-segmentation-3-0.int8.onnx",
    bytes = 1_540_506,
)

/** The embedding model sherpa-onnx's own diarization examples use, which its default threshold suits. */
val SPEAKER_EMBEDDING = ModelSpec(
    url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/" +
        "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx",
    fileName = "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx",
    bytes = 39_593_761,
)

#include <dlfcn.h>
#include <jni.h>

#include <cstdint>
#include <cstdio>
#include <vector>

#include "sherpa-onnx/c-api/c-api.h"

namespace {

struct Api {
    decltype(&SherpaOnnxCreateOfflineSpeakerDiarization) create;
    decltype(&SherpaOnnxDestroyOfflineSpeakerDiarization) destroy;
    decltype(&SherpaOnnxOfflineSpeakerDiarizationProcessWithCallback) process;
    decltype(&SherpaOnnxOfflineSpeakerDiarizationResultGetNumSegments) numSegments;
    decltype(&SherpaOnnxOfflineSpeakerDiarizationResultSortByStartTime) sorted;
    decltype(&SherpaOnnxOfflineSpeakerDiarizationDestroySegment) destroySegments;
    decltype(&SherpaOnnxOfflineSpeakerDiarizationDestroyResult) destroyResult;
};

template <typename T>
bool bind(void *lib, const char *name, T &fn) {
    fn = reinterpret_cast<T>(dlsym(lib, name));
    return fn != nullptr;
}

/** The library is already loaded by the Kotlin side; this only looks it up. */
const Api *api() {
    static Api a{};
    static bool ok = [] {
        void *lib = dlopen("libsherpa-onnx-c-api.so", RTLD_NOW);
        if (lib == nullptr) return false;
        return bind(lib, "SherpaOnnxCreateOfflineSpeakerDiarization", a.create) &&
               bind(lib, "SherpaOnnxDestroyOfflineSpeakerDiarization", a.destroy) &&
               bind(lib, "SherpaOnnxOfflineSpeakerDiarizationProcessWithCallback", a.process) &&
               bind(lib, "SherpaOnnxOfflineSpeakerDiarizationResultGetNumSegments", a.numSegments) &&
               bind(lib, "SherpaOnnxOfflineSpeakerDiarizationResultSortByStartTime", a.sorted) &&
               bind(lib, "SherpaOnnxOfflineSpeakerDiarizationDestroySegment", a.destroySegments) &&
               bind(lib, "SherpaOnnxOfflineSpeakerDiarizationDestroyResult", a.destroyResult);
    }();
    return ok ? &a : nullptr;
}

/** Read here, not passed in from Kotlin: an hour of float samples is past the Java heap limit. */
bool readPcm16(const char *path, std::vector<float> &out) {
    FILE *file = fopen(path, "rb");
    if (file == nullptr) return false;
    int16_t block[32768];
    size_t read;
    while ((read = fread(block, sizeof(int16_t), 32768, file)) > 0) {
        for (size_t i = 0; i < read; i++) out.push_back(block[i] / 32768.0f);
    }
    fclose(file);
    return true;
}

struct Progress {
    JNIEnv *env;
    jobject listener;
    jmethodID onProgress;
};

}  // namespace

/** Returns start, end (seconds) and speaker per turn, flattened; null when anything fails. */
extern "C" JNIEXPORT jfloatArray JNICALL
Java_dev_andrej_echo_speech_speakers_SpeakerNative_diarize(
        JNIEnv *env, jobject, jstring segmentationModel, jstring embeddingModel, jstring pcmPath,
        jint threads, jobject listener) {
    const Api *a = api();
    if (a == nullptr) return nullptr;

    const char *segmentation = env->GetStringUTFChars(segmentationModel, nullptr);
    const char *embedding = env->GetStringUTFChars(embeddingModel, nullptr);
    const char *pcm = env->GetStringUTFChars(pcmPath, nullptr);

    SherpaOnnxOfflineSpeakerDiarizationConfig config{};
    config.segmentation.pyannote.model = segmentation;
    config.segmentation.num_threads = threads;
    config.embedding.model = embedding;
    config.embedding.num_threads = threads;

    jfloatArray turns = nullptr;
    std::vector<float> samples;
    const SherpaOnnxOfflineSpeakerDiarization *sd = a->create(&config);
    if (sd != nullptr && readPcm16(pcm, samples) && !samples.empty()) {
        Progress progress{
                env,
                listener,
                env->GetMethodID(env->GetObjectClass(listener), "onProgress", "(I)V"),
        };
        // Runs on the calling thread, which is what makes reusing its JNIEnv safe. Its return
        // value is ignored by sherpa-onnx, so a run can't be stopped part way.
        auto callback = [](int32_t done, int32_t total, void *data) -> int32_t {
            auto *p = static_cast<Progress *>(data);
            p->env->CallVoidMethod(p->listener, p->onProgress, total > 0 ? 100 * done / total : 0);
            return 0;
        };
        const SherpaOnnxOfflineSpeakerDiarizationResult *result =
                a->process(sd, samples.data(), static_cast<int32_t>(samples.size()), callback, &progress);
        if (result != nullptr) {
            int32_t count = a->numSegments(result);
            const SherpaOnnxOfflineSpeakerDiarizationSegment *segments = a->sorted(result);
            std::vector<float> flat;
            for (int32_t i = 0; i < count; i++) {
                flat.push_back(segments[i].start);
                flat.push_back(segments[i].end);
                flat.push_back(static_cast<float>(segments[i].speaker));
            }
            if (segments != nullptr) a->destroySegments(segments);
            a->destroyResult(result);
            turns = env->NewFloatArray(static_cast<jsize>(flat.size()));
            env->SetFloatArrayRegion(turns, 0, static_cast<jsize>(flat.size()), flat.data());
        }
    }
    if (sd != nullptr) a->destroy(sd);

    env->ReleaseStringUTFChars(segmentationModel, segmentation);
    env->ReleaseStringUTFChars(embeddingModel, embedding);
    env->ReleaseStringUTFChars(pcmPath, pcm);
    return turns;
}

#include <jni.h>

#include <atomic>
#include <string>

#include "whisper.h"

namespace {

struct Handle {
    whisper_context *ctx;
    std::atomic<bool> abort{false};
};

struct Progress {
    JNIEnv *env;
    jobject listener;
    jmethodID onProgress;
};

Handle *from(jlong handle) { return reinterpret_cast<Handle *>(handle); }

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_dev_andrej_echo_speech_whisper_WhisperNative_init(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, params);
    env->ReleaseStringUTFChars(modelPath, path);
    if (ctx == nullptr) return 0;
    return reinterpret_cast<jlong>(new Handle{ctx});
}

extern "C" JNIEXPORT void JNICALL
Java_dev_andrej_echo_speech_whisper_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    Handle *h = from(handle);
    whisper_free(h->ctx);
    delete h;
}

/** The progress callback runs on the calling thread, which is what makes reusing its JNIEnv safe. */
extern "C" JNIEXPORT jint JNICALL
Java_dev_andrej_echo_speech_whisper_WhisperNative_full(
        JNIEnv *env, jobject, jlong handle, jfloatArray samples, jstring language, jint threads,
        jobject listener) {
    Handle *h = from(handle);
    h->abort = false;

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;

    const char *lang = language == nullptr ? nullptr : env->GetStringUTFChars(language, nullptr);
    params.language = lang;

    Progress progress{
            env,
            listener,
            env->GetMethodID(env->GetObjectClass(listener), "onProgress", "(I)V"),
    };
    params.progress_callback = [](whisper_context *, whisper_state *, int percent, void *data) {
        auto *p = static_cast<Progress *>(data);
        p->env->CallVoidMethod(p->listener, p->onProgress, percent);
    };
    params.progress_callback_user_data = &progress;
    params.abort_callback = [](void *data) { return static_cast<Handle *>(data)->abort.load(); };
    params.abort_callback_user_data = h;

    jsize count = env->GetArrayLength(samples);
    jfloat *pcm = env->GetFloatArrayElements(samples, nullptr);
    int result = whisper_full(h->ctx, params, pcm, count);
    env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);

    if (lang != nullptr) env->ReleaseStringUTFChars(language, lang);
    return h->abort ? -1 : result;
}

/** Called from another thread while [full] runs; ggml checks the flag between graph nodes. */
extern "C" JNIEXPORT void JNICALL
Java_dev_andrej_echo_speech_whisper_WhisperNative_abort(JNIEnv *, jobject, jlong handle) {
    from(handle)->abort = true;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_andrej_echo_speech_whisper_WhisperNative_segmentCount(JNIEnv *, jobject, jlong handle) {
    return whisper_full_n_segments(from(handle)->ctx);
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_andrej_echo_speech_whisper_WhisperNative_segmentStartMs(JNIEnv *, jobject, jlong handle, jint i) {
    return whisper_full_get_segment_t0(from(handle)->ctx, i) * 10;
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_andrej_echo_speech_whisper_WhisperNative_segmentEndMs(JNIEnv *, jobject, jlong handle, jint i) {
    return whisper_full_get_segment_t1(from(handle)->ctx, i) * 10;
}

/** Bytes, not a jstring: Whisper can split a multibyte character, and NewStringUTF aborts on that. */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_andrej_echo_speech_whisper_WhisperNative_segmentText(JNIEnv *env, jobject, jlong handle, jint i) {
    std::string text = whisper_full_get_segment_text(from(handle)->ctx, i);
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(text.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    return bytes;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_andrej_echo_speech_whisper_WhisperNative_detectedLanguage(JNIEnv *env, jobject, jlong handle) {
    return env->NewStringUTF(whisper_lang_str(whisper_full_lang_id(from(handle)->ctx)));
}

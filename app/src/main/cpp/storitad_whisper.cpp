#include <jni.h>
#include <android/log.h>
#include <string>
#include "whisper.h"

#define LOG_TAG "storitad-whisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_uk_storitad_capture_whisper_WhisperNative_nativeLoadModel(
    JNIEnv *env, jclass, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    auto *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (ctx == nullptr) {
        LOGE("whisper_init_from_file_with_params failed");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_uk_storitad_capture_whisper_WhisperNative_nativeTranscribe(
    JNIEnv *env, jclass, jlong ctxHandle,
    jfloatArray pcm, jint /*sampleRate*/) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxHandle);
    if (ctx == nullptr) return env->NewStringUTF("");

    jsize n = env->GetArrayLength(pcm);
    jfloat *samples = env->GetFloatArrayElements(pcm, nullptr);

    struct whisper_full_params params =
        whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language       = "en";
    params.n_threads      = 4;
    params.print_progress = false;
    params.print_special  = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate      = false;

    int rc = whisper_full(ctx, params, samples, n);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    if (rc != 0) {
        LOGE("whisper_full returned %d", rc);
        return env->NewStringUTF("");
    }

    std::string out;
    int segs = whisper_full_n_segments(ctx);
    for (int i = 0; i < segs; ++i) {
        out += whisper_full_get_segment_text(ctx, i);
    }
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_uk_storitad_capture_whisper_WhisperNative_nativeRelease(
    JNIEnv *, jclass, jlong ctxHandle) {
    if (ctxHandle != 0) {
        whisper_free(reinterpret_cast<whisper_context *>(ctxHandle));
    }
}

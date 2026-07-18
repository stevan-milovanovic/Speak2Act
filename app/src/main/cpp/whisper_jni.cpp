#include <jni.h>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_rs_smobile_speak2act_feature_voicerecorder_data_whisper_WhisperLib_00024Companion_initContext(
        JNIEnv *env, jobject /* thiz */, jstring model_path_str) {
    const char *model_path = env->GetStringUTFChars(model_path_str, nullptr);
    LOGI("Loading whisper model from '%s'", model_path);
    whisper_context_params params = whisper_context_default_params();
    // Flash attention gives an additional on-device speed-up (supported by the tiny model).
    params.flash_attn = true;
    struct whisper_context *context =
            whisper_init_from_file_with_params(model_path, params);
    env->ReleaseStringUTFChars(model_path_str, model_path);
    if (context == nullptr) {
        LOGW("Failed to initialise whisper context");
    }
    return (jlong) context;
}

JNIEXPORT void JNICALL
Java_rs_smobile_speak2act_feature_voicerecorder_data_whisper_WhisperLib_00024Companion_freeContext(
        JNIEnv * /* env */, jobject /* thiz */, jlong context_ptr) {
    auto *context = (struct whisper_context *) context_ptr;
    if (context != nullptr) {
        whisper_free(context);
    }
}

JNIEXPORT jint JNICALL
Java_rs_smobile_speak2act_feature_voicerecorder_data_whisper_WhisperLib_00024Companion_fullTranscribe(
        JNIEnv *env, jobject /* thiz */, jlong context_ptr, jint num_threads,
        jfloatArray audio_data, jstring language_str) {
    auto *context = (struct whisper_context *) context_ptr;
    if (context == nullptr) {
        return -1;
    }

    jfloat *audio = env->GetFloatArrayElements(audio_data, nullptr);
    const jsize audio_length = env->GetArrayLength(audio_data);

    const char *language = env->GetStringUTFChars(language_str, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    // "auto" enables on-device language detection (DE/FR/IT/EN ...) followed by
    // transcription. detect_language MUST stay false: when true, whisper_full only
    // detects the language and returns early without producing any transcript.
    params.language = language;
    params.detect_language = false;
    params.n_threads = num_threads;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    whisper_reset_timings(context);

    LOGI("Running whisper_full on %d samples (threads=%d, lang=%s)",
         (int) audio_length, (int) num_threads, language);

    jint result = whisper_full(context, params, audio, audio_length);
    if (result != 0) {
        LOGW("whisper_full failed with code %d", (int) result);
    }

    env->ReleaseFloatArrayElements(audio_data, audio, JNI_ABORT);
    env->ReleaseStringUTFChars(language_str, language);
    return result;
}

JNIEXPORT jint JNICALL
Java_rs_smobile_speak2act_feature_voicerecorder_data_whisper_WhisperLib_00024Companion_getTextSegmentCount(
        JNIEnv * /* env */, jobject /* thiz */, jlong context_ptr) {
    auto *context = (struct whisper_context *) context_ptr;
    if (context == nullptr) {
        return 0;
    }
    return whisper_full_n_segments(context);
}

JNIEXPORT jstring JNICALL
Java_rs_smobile_speak2act_feature_voicerecorder_data_whisper_WhisperLib_00024Companion_getTextSegment(
        JNIEnv *env, jobject /* thiz */, jlong context_ptr, jint index) {
    auto *context = (struct whisper_context *) context_ptr;
    if (context == nullptr) {
        return env->NewStringUTF("");
    }
    const char *text = whisper_full_get_segment_text(context, index);
    return env->NewStringUTF(text);
}

JNIEXPORT jstring JNICALL
Java_rs_smobile_speak2act_feature_voicerecorder_data_whisper_WhisperLib_00024Companion_getSystemInfo(
        JNIEnv *env, jobject /* thiz */) {
    const char *info = whisper_print_system_info();
    return env->NewStringUTF(info);
}

} // extern "C"

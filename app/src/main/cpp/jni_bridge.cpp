#include "jni_bridge.h"
#include <android/log.h>
#include <jni.h>

#define LOG_TAG "WarpedLLAMA"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

LlamaEngine& LlamaEngine::getInstance() {
    static LlamaEngine instance;
    return instance;
}

LlamaEngine::~LlamaEngine() {
    unload();
}

bool LlamaEngine::loadModel(const std::string& path, int nThreads, int nCtx) {
    if (loaded) unload();

    LOGD("Loading model: %s (threads=%d, ctx=%d)", path.c_str(), nThreads, nCtx);

    // TODO: Integrate real llama.cpp model loading
    // struct llama_model_params model_params = llama_model_default_params();
    // model_params.n_gpu_layers = 0;
    // llama_model = llama_load_model_from_file(path.c_str(), model_params);
    // if (!llama_model) { LOGE("Failed to load model"); return false; }
    //
    // struct llama_context_params ctx_params = llama_context_default_params();
    // ctx_params.n_ctx = nCtx;
    // ctx_params.n_threads = nThreads;
    // llama_context = llama_new_context_with_model(llama_model, ctx_params);
    // if (!llama_context) { LOGE("Failed to create context"); llama_free_model(llama_model); return false; }

    modelPath = path;
    loaded = true;
    LOGD("Model loaded successfully (stub)");
    return true;
}

void LlamaEngine::generate(const std::string& prompt, TokenCallback callback) {
    if (!loaded) {
        callback("Model not loaded", true);
        return;
    }

    LOGD("Generate: %s", prompt.substr(0, 100).c_str());
    shouldStop = false;

    // TODO: Real llama.cpp inference loop
    // std::string formatted = "<|user|>\n" + prompt + "\n<|assistant|>\n";
    // auto tokens = llama_tokenize(llama_context, formatted, true);
    // llama_batch batch = llama_batch_init(512, 0, 1);
    // ... eval loop with llama_decode, llama_sample, etc.

    // Stub: emit placeholder tokens
    callback("This is a placeholder response. Real llama.cpp inference requires the native library to be compiled from source (see build instructions).", false);
    callback("", true); // done signal
}

void LlamaEngine::stop() {
    LOGD("Stop requested");
    shouldStop = true;
}

void LlamaEngine::unload() {
    if (!loaded) return;

    LOGD("Unloading model");

    // TODO: llama_free(llama_context);
    // TODO: llama_free_model(llama_model);

    loaded = false;
    modelPath.clear();
}

bool LlamaEngine::isLoaded() const {
    return loaded;
}

std::string LlamaEngine::getModelInfo() const {
    if (!loaded) return "";

    // TODO: Extract real metadata from GGUF header
    return "{\"path\":\"" + modelPath + "\"}";
}

// ===== JNI FUNCTIONS =====

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_warped_data_local_inference_LlamaEngine_nativeLoadModel(
    JNIEnv* env, jobject /* this */, jstring path, jint nThreads, jint nCtx) {
    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    bool result = LlamaEngine::getInstance().loadModel(pathStr, nThreads, nCtx);
    env->ReleaseStringUTFChars(path, pathStr);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_warped_data_local_inference_LlamaEngine_nativeGenerate(
    JNIEnv* env, jobject /* this */, jstring prompt, jobject callback) {
    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string promptCpp(promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;Z)V");

    LlamaEngine::getInstance().generate(promptCpp, [env, callback, onTokenMethod](const std::string& token, bool done) {
        jstring jToken = env->NewStringUTF(token.c_str());
        env->CallVoidMethod(callback, onTokenMethod, jToken, done ? JNI_TRUE : JNI_FALSE);
        env->DeleteLocalRef(jToken);
    });
}

JNIEXPORT void JNICALL
Java_com_warped_data_local_inference_LlamaEngine_nativeStop(
    JNIEnv* /* env */, jobject /* this */) {
    LlamaEngine::getInstance().stop();
}

JNIEXPORT void JNICALL
Java_com_warped_data_local_inference_LlamaEngine_nativeUnload(
    JNIEnv* /* env */, jobject /* this */) {
    LlamaEngine::getInstance().unload();
}

JNIEXPORT jboolean JNICALL
Java_com_warped_data_local_inference_LlamaEngine_nativeIsLoaded(
    JNIEnv* /* env */, jobject /* this */) {
    return LlamaEngine::getInstance().isLoaded() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_warped_data_local_inference_LlamaEngine_nativeGetModelInfo(
    JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF(LlamaEngine::getInstance().getModelInfo().c_str());
}

} // extern "C"

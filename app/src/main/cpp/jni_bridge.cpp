#include "jni_bridge.h"
#include <android/log.h>
#include <jni.h>
#include <llama.h>
#include <ggml.h>
#include <vector>
#include <string>
#include <cstring>

#define LOG_TAG "WarpedLLAMA"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

LlamaEngine& LlamaEngine::getInstance() {
    static LlamaEngine instance;
    return instance;
}

LlamaEngine::~LlamaEngine() {
    unload();
}

std::string LlamaEngine::loadModel(const std::string& path, int nThreads, int nCtx, ProgressCallback progress) {
    if (loaded.load()) unload();

    LOGD("Loading model: %s (threads=%d, ctx=%d)", path.c_str(), nThreads, nCtx);

    if (progress) progress(0, "Validating file...");

    FILE* testFile = fopen(path.c_str(), "rb");
    if (!testFile) {
        return "Corrupted model file — please re-download";
    }
    fseek(testFile, 0, SEEK_END);
    long fileSize = ftell(testFile);
    fclose(testFile);
    if (fileSize < 32) {
        return "Corrupted model file — please re-download";
    }

    try {
        ggml_backend_load_all();

        if (progress) progress(10, "Configuring model parameters...");
        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = 0;
        model_params.use_mmap = true;
        model_params.use_mlock = false;

        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = nCtx;
        ctx_params.n_threads = nThreads;
        ctx_params.n_threads_batch = nThreads;

        if (progress) progress(25, "Mapping to memory...");

        llama_model_ptr = llama_load_model_from_file(path.c_str(), model_params);
        if (!llama_model_ptr) {
            return "Unsupported architecture — this model requires ARM64";
        }

        if (progress) progress(75, "Initializing context...");

        llama_context_ptr = llama_new_context_with_model(llama_model_ptr, ctx_params);
        if (!llama_context_ptr) {
            llama_free_model(llama_model_ptr);
            llama_model_ptr = nullptr;
            return "Out of memory — try a smaller quantization";
        }

        loadedModelPath = path;
        loaded.store(true);

        if (progress) progress(100, "Ready");

        LOGD("Model loaded successfully: %s (%ld bytes)", path.c_str(), fileSize);
        return "";
    } catch (const std::bad_alloc&) {
        if (llama_context_ptr) { llama_free(llama_context_ptr); llama_context_ptr = nullptr; }
        if (llama_model_ptr) { llama_free_model(llama_model_ptr); llama_model_ptr = nullptr; }
        LOGE("Out of memory loading model: %s", path.c_str());
        return "Out of memory — try a smaller quantization";
    } catch (const std::exception& e) {
        if (llama_context_ptr) { llama_free(llama_context_ptr); llama_context_ptr = nullptr; }
        if (llama_model_ptr) { llama_free_model(llama_model_ptr); llama_model_ptr = nullptr; }
        LOGE("Exception loading model: %s", e.what());
        return std::string("Failed to load model: ") + e.what();
    }
}

void LlamaEngine::generate(const std::string& prompt, TokenCallback callback) {
    if (!loaded.load()) {
        callback("Model not loaded", true);
        return;
    }

    shouldStop.store(false);

    try {
        // Tokenize prompt
        std::vector<llama_token> tokens;
        tokens.push_back(llama_token_bos(llama_model_ptr));
        int nPromptTokens = llama_tokenize(llama_model_ptr, prompt.c_str(), prompt.size(),
                                           tokens.data() + 1, llama_n_ctx(llama_context_ptr) - 2,
                                           true, false);
        if (nPromptTokens < 0) {
            int totalTokens = -nPromptTokens;
            tokens.resize(totalTokens + 1);
            nPromptTokens = llama_tokenize(llama_model_ptr, prompt.c_str(), prompt.size(),
                                           tokens.data() + 1, totalTokens,
                                           true, false);
        }
        tokens.resize(nPromptTokens + 1);

        int nPos = 0;
        int maxTokens = 512;
        int nPast = 0;

        // Default sampler (greedy)
        struct llama_sampler* smpl = llama_sampler_init_greedy();

        // Batch for prompt eval
        int nBatches = (tokens.size() + 511) / 512;
        for (int bi = 0; bi < nBatches; bi++) {
            int batchStart = bi * 512;
            int batchSize = std::min((int)tokens.size() - batchStart, 512);
            struct llama_batch batch = llama_batch_init(batchSize, 0, 1);
            for (int i = 0; i < batchSize; i++) {
                llama_batch_add(batch, tokens[batchStart + i], nPast + i, {0}, i == batchSize - 1 && bi == nBatches - 1);
            }
            if (llama_decode(llama_context_ptr, batch) != 0) {
                llama_batch_free(batch);
                llama_sampler_free(smpl);
                callback("Decode error", true);
                return;
            }
            nPast += batchSize;
            llama_batch_free(batch);
        }
        nPos = nPast;

        // Generation loop
        for (int i = 0; i < maxTokens; i++) {
            if (shouldStop.load()) {
                llama_sampler_free(smpl);
                callback("", true);
                return;
            }

            llama_token newToken = llama_sampler_sample(smpl, llama_context_ptr, -1);
            if (llama_token_is_eog(llama_model_ptr, newToken)) {
                llama_sampler_free(smpl);
                callback("", true);
                return;
            }

            char buf[256];
            int nChars = llama_token_to_piece(llama_model_ptr, newToken, buf, sizeof(buf), 0, true);
            if (nChars > 0) {
                std::string tokenStr(buf, nChars);
                callback(tokenStr, false);
            }

            struct llama_batch batch = llama_batch_init(1, 0, 1);
            llama_batch_add(batch, newToken, nPos++, {0}, true);
            if (llama_decode(llama_context_ptr, batch) != 0) {
                llama_batch_free(batch);
                llama_sampler_free(smpl);
                callback("Decode error", true);
                return;
            }
            llama_batch_free(batch);
        }

        llama_sampler_free(smpl);
        callback("", true);
    } catch (const std::exception& e) {
        LOGE("Generation exception: %s", e.what());
        callback("Generation error", true);
    }
}

void LlamaEngine::stop() {
    LOGD("Stop requested");
    shouldStop.store(true);
}

void LlamaEngine::unload() {
    if (!loaded.load()) return;

    LOGD("Unloading model");

    if (llama_context_ptr) {
        llama_free(llama_context_ptr);
        llama_context_ptr = nullptr;
    }
    if (llama_model_ptr) {
        llama_free_model(llama_model_ptr);
        llama_model_ptr = nullptr;
    }

    loaded.store(false);
    loadedModelPath.clear();

    LOGD("Model unloaded");
}

bool LlamaEngine::isLoaded() const {
    return loaded.load();
}

std::string LlamaEngine::getModelInfo() const {
    if (!loaded.load() || !llama_model_ptr) return "{}";

    char buf[1024];
    int n = llama_model_desc(llama_model_ptr, buf, sizeof(buf));
    std::string desc(n > 0 ? buf : "");

    size_t paramCount = llama_model_n_params(llama_model_ptr);
    int64_t nCtx = llama_n_ctx(llama_context_ptr);
    uint32_t ftype = llama_model_ftype(llama_model_ptr);

    std::string quantStr;
    switch (ftype) {
        case GGML_FTYPE_F32:     quantStr = "F32"; break;
        case GGML_FTYPE_F16:     quantStr = "F16"; break;
        case GGML_FTYPE_Q4_0:    quantStr = "Q4_0"; break;
        case GGML_FTYPE_Q4_1:    quantStr = "Q4_1"; break;
        case GGML_FTYPE_Q5_0:    quantStr = "Q5_0"; break;
        case GGML_FTYPE_Q5_1:    quantStr = "Q5_1"; break;
        case GGML_FTYPE_Q8_0:    quantStr = "Q8_0"; break;
        case GGML_FTYPE_Q2_K:    quantStr = "Q2_K"; break;
        case GGML_FTYPE_Q3_K:    quantStr = "Q3_K"; break;
        case GGML_FTYPE_Q4_K:    quantStr = "Q4_K"; break;
        case GGML_FTYPE_Q5_K:    quantStr = "Q5_K"; break;
        case GGML_FTYPE_Q6_K:    quantStr = "Q6_K"; break;
        case GGML_FTYPE_IQ2_XXS: quantStr = "IQ2_XXS"; break;
        case GGML_FTYPE_IQ2_XS:  quantStr = "IQ2_XS"; break;
        case GGML_FTYPE_IQ3_XXS: quantStr = "IQ3_XXS"; break;
        case GGML_FTYPE_IQ3_S:   quantStr = "IQ3_S"; break;
        case GGML_FTYPE_IQ1_S:   quantStr = "IQ1_S"; break;
        case GGML_FTYPE_IQ4_NL:  quantStr = "IQ4_NL"; break;
        case GGML_FTYPE_IQ4_XS:  quantStr = "IQ4_XS"; break;
        case GGML_FTYPE_BF16:    quantStr = "BF16"; break;
        case GGML_FTYPE_TQ1_0:   quantStr = "TQ1_0"; break;
        case GGML_FTYPE_TQ2_0:   quantStr = "TQ2_0"; break;
        default: quantStr = "Q" + std::to_string(ftype); break;
    }

    std::string paramStr;
    if (paramCount >= 1'000'000'000) {
        char pbuf[32];
        snprintf(pbuf, sizeof(pbuf), "%.1fB", paramCount / 1'000'000'000.0);
        paramStr = pbuf;
    } else if (paramCount >= 1'000'000) {
        char pbuf[32];
        snprintf(pbuf, sizeof(pbuf), "%.1fM", paramCount / 1'000'000.0);
        paramStr = pbuf;
    } else {
        paramStr = std::to_string(paramCount);
    }

    std::string archStr;
    int64_t pos = desc.find(" architecture ");
    if (pos != std::string::npos) {
        archStr = desc.substr(pos + 14);
        int64_t endPos = archStr.find_first_of(" ,\n\r\t");
        if (endPos != std::string::npos) {
            archStr = archStr.substr(0, endPos);
        }
    } else {
        archStr = desc.empty() ? "unknown" : desc;
    }

    char json[512];
    snprintf(json, sizeof(json),
        "{\"architecture\":\"%s\",\"parameterCount\":\"%s\",\"contextLength\":%lld,"
        "\"quantization\":\"%s\",\"desc\":\"%s\"}",
        archStr.c_str(), paramStr.c_str(), (long long)nCtx,
        quantStr.c_str(), desc.c_str());

    return std::string(json);
}

// ===== JNI FUNCTIONS =====

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_warped_data_local_inference_LlamaEngine_nativeLoadModel(
    JNIEnv* env, jobject /* this */, jstring path, jint nThreads, jint nCtx, jobject progressCallback) {
    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    std::string pathCpp(pathStr);
    env->ReleaseStringUTFChars(path, pathStr);

    ProgressCallback progressFn = nullptr;
    if (progressCallback) {
        jclass callbackClass = env->GetObjectClass(progressCallback);
        jmethodID onProgressMethod = env->GetMethodID(callbackClass, "onProgress", "(ILjava/lang/String;)V");
        if (onProgressMethod) {
            progressFn = [env, progressCallback, onProgressMethod](int percent, const std::string& message) {
                jstring jMsg = env->NewStringUTF(message.c_str());
                env->CallVoidMethod(progressCallback, onProgressMethod, percent, jMsg);
                env->DeleteLocalRef(jMsg);
            };
        }
    }

    std::string result = LlamaEngine::getInstance().loadModel(pathCpp, nThreads, nCtx, progressFn);
    return result.empty() ? nullptr : env->NewStringUTF(result.c_str());
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

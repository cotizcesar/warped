#ifndef JNI_BRIDGE_H
#define JNI_BRIDGE_H

#include <jni.h>
#include <string>
#include <functional>
#include <atomic>

using TokenCallback = std::function<void(const std::string&, bool)>;
using ProgressCallback = std::function<void(int, const std::string&)>;

struct llama_model;
struct llama_context;

class LlamaEngine {
public:
    static LlamaEngine& getInstance();

    std::string loadModel(const std::string& modelPath, int nThreads, int nCtx, ProgressCallback progress);
    void generate(const std::string& prompt, TokenCallback callback);
    void stop();
    void unload();
    bool isLoaded() const;
    std::string getModelInfo() const;

private:
    LlamaEngine() = default;
    ~LlamaEngine();

    std::atomic<bool> loaded{false};
    std::atomic<bool> shouldStop{false};
    llama_model* llama_model_ptr = nullptr;
    llama_context* llama_context_ptr = nullptr;
    std::string loadedModelPath;
};

#endif // JNI_BRIDGE_H

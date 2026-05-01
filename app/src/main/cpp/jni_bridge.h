#ifndef JNI_BRIDGE_H
#define JNI_BRIDGE_H

#include <jni.h>
#include <string>
#include <functional>

using TokenCallback = std::function<void(const std::string&, bool)>;

class LlamaEngine {
public:
    static LlamaEngine& getInstance();

    bool loadModel(const std::string& modelPath, int nThreads, int nCtx);
    void generate(const std::string& prompt, TokenCallback callback);
    void stop();
    void unload();
    bool isLoaded() const;
    std::string getModelInfo() const;

private:
    LlamaEngine() = default;
    ~LlamaEngine();

    bool loaded = false;
    bool shouldStop = false;
    void* llama_model = nullptr;
    void* llama_context = nullptr;
    std::string modelPath;
};

#endif // JNI_BRIDGE_H

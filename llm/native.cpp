#include "llama.h"
#include <jni.h>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
std::string bytes(JNIEnv *env, jbyteArray value, size_t limit) {
    if (!value) throw std::invalid_argument("Input assente.");
    jsize size = env->GetArrayLength(value);
    if (size <= 0 || static_cast<size_t>(size) > limit) throw std::invalid_argument("Input fuori limite.");
    std::string result(size, '\0');
    env->GetByteArrayRegion(value, 0, size, reinterpret_cast<jbyte *>(&result[0]));
    if (result.find('\0') != std::string::npos) throw std::invalid_argument("NUL in input.");
    return result;
}

std::string generate(const std::string &path, const std::string &prompt, const std::string &grammar) {
#ifndef __ANDROID__
    ggml_backend_load_all();
#endif
    auto mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    using Model = std::unique_ptr<llama_model, decltype(&llama_model_free)>;
    Model model(llama_model_load_from_file(path.c_str(), mp), llama_model_free);
    if (!model) throw std::runtime_error("Caricamento GGUF fallito: verificare spazio e RAM.");
    const auto *vocab = llama_model_get_vocab(model.get());
    const int length = -llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
    if (length <= 0 || length + 120 > 2048) throw std::runtime_error("Accorciare il segmento.");
    std::vector<llama_token> tokens(length);
    if (llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), length, true, true) != length)
        throw std::runtime_error("Tokenizzazione fallita.");
    auto cp = llama_context_default_params();
    cp.n_ctx = length + 120;
    cp.n_batch = length;
    cp.n_threads = cp.n_threads_batch = 2;
    using Context = std::unique_ptr<llama_context, decltype(&llama_free)>;
    Context context(llama_init_from_model(model.get(), cp), llama_free);
    if (!context) throw std::runtime_error("RAM insufficiente per il contesto.");
    using Sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;
    Sampler chain(llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
    auto *guard = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
    if (!chain || !guard) throw std::runtime_error("Grammatica non valida.");
    llama_sampler_chain_add(chain.get(), guard);
    llama_sampler_chain_add(chain.get(), llama_sampler_init_greedy());
    auto batch = llama_batch_get_one(tokens.data(), length);
    std::string answer;
    llama_token next;
    for (int i = 0; i < 120; ++i) {
        if (llama_decode(context.get(), batch)) throw std::runtime_error("Decodifica interrotta.");
        next = llama_sampler_sample(chain.get(), context.get(), -1);
        if (llama_vocab_is_eog(vocab, next)) return answer;
        char piece[512];
        int n = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, false);
        if (n < 0 || answer.size() + n > 2048) throw std::runtime_error("Risultato troppo lungo.");
        answer.append(piece, n);
        batch = llama_batch_get_one(&next, 1);
    }
    throw std::runtime_error("Risposta incompleta al limite token.");
}
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_it_michetti_studio_LocalModel_generate(JNIEnv *env, jclass, jbyteArray path, jbyteArray prompt, jbyteArray grammar) {
    try {
        auto answer = generate(bytes(env,path,4096), bytes(env,prompt,16000), bytes(env,grammar,524288));
        auto result = env->NewByteArray(answer.size());
        if (result) env->SetByteArrayRegion(result,0,answer.size(),reinterpret_cast<const jbyte *>(answer.data()));
        return result;
    } catch (const std::exception &error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return nullptr;
    }
}

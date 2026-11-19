#include "studio_asr.h"
#include "whisper.h"
#include <algorithm>
#include <cmath>
#include <memory>

namespace {
struct Job {
    studio_cancel_fn cancelled;
    void *data;
};

bool is_cancelled(void *opaque) {
    const auto *job = static_cast<const Job *>(opaque);
    return job->cancelled && job->cancelled(job->data);
}
}

int studio_asr_italian(const char *model_path, const float *pcm, int samples,
                       int threads, studio_cue_fn cue, studio_cancel_fn cancelled,
                       void *data) {
    if (!model_path || !*model_path || !pcm || samples < 1 || samples > 16000 * 600 ||
        !cue || threads < 1 || threads > 16)
        return 1;
    Job job{cancelled, data};
    if (is_cancelled(&job)) return 5;
    for (int i = 0; i < samples; ++i)
        if (!std::isfinite(pcm[i]) || std::abs(pcm[i]) > 1.0f) return 1;
    auto config = whisper_context_default_params();
    config.use_gpu = false;
    std::unique_ptr<whisper_context, decltype(&whisper_free)> ctx(
        whisper_init_from_file_with_params(model_path, config), whisper_free);
    if (!ctx) return 2;
    auto params = whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);
    params.language = "it";
    params.n_threads = threads;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.no_context = true;
    params.abort_callback = is_cancelled;
    params.abort_callback_user_data = &job;
    int err = whisper_full(ctx.get(), params, pcm, samples);
    if (is_cancelled(&job)) return 5;
    if (err) return 3;
    const int duration_ms = (samples + 15) / 16;
    int previous_end = 0;
    int emitted = 0;
    for (int i = 0; i < whisper_full_n_segments(ctx.get()); ++i) {
        if (is_cancelled(&job)) return 5;
        const char *text = whisper_full_get_segment_text(ctx.get(), i);
        while (*text == ' ') ++text;
        if (!*text) continue;
        const auto start = whisper_full_get_segment_t0(ctx.get(), i) * 10;
        const auto end = std::min<int64_t>(duration_ms, whisper_full_get_segment_t1(ctx.get(), i) * 10);
        if (start < previous_end || start >= end) return 4;
        if (cue(static_cast<int>(start), static_cast<int>(end), text, data)) return 6;
        previous_end = static_cast<int>(end);
        ++emitted;
    }
    return emitted ? 0 : 4;
}

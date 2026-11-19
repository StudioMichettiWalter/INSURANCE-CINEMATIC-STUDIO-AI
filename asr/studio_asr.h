#ifndef STUDIO_ASR_H
#define STUDIO_ASR_H
#ifdef __cplusplus
extern "C" {
#endif
/* PCM mono float normalizzato a 16000 Hz, massimo 10 minuti nello spike.
   Il chiamante verifica il modello rispetto al lockfile, conserva PCM e path
   per tutta la chiamata e copia il testo UTF-8 dentro il callback.
   Eseguire fuori dal thread UI. Codici: 0 OK, 1 input, 2 modello, 3 inferenza,
   4 timestamp o assenza di testo, 5 annullato, 6 callback fallito. */
typedef int (*studio_cue_fn)(int start_ms, int end_ms, const char *text, void *data);
typedef int (*studio_cancel_fn)(void *data);
int studio_asr_italian(const char *model_path, const float *pcm, int samples,
                       int threads, studio_cue_fn cue, studio_cancel_fn cancelled,
                       void *data);
#ifdef __cplusplus
}
#endif
#endif

#include "studio_asr.h"
#include <cstdio>
#include <fstream>
#include <iterator>
#include <vector>
#include <string>

static int cue(int start, int end, const char *text, void *opaque) {
    auto &out = *static_cast<std::string *>(opaque);
    std::printf("%d\t%d\t%s\n", start, end, text);
    if (start < 0 || end <= start || end > 3287) return 1;
    out += text;
    return 0;
}
static int stop(void *) { return 1; }
int main(int argc, char **argv) {
    if (argc != 3) return 1;
    std::ifstream input(argv[2], std::ios::binary);
    std::vector<char> bytes((std::istreambuf_iterator<char>(input)), {});
    if (bytes.empty() || bytes.size() % 2) return 2;
    std::vector<float> pcm;
    for (size_t i = 0; i < bytes.size(); i += 2) {
        const int word = static_cast<unsigned char>(bytes[i]) + 256 * static_cast<unsigned char>(bytes[i+1]);
        pcm.push_back((word >= 32768 ? word - 65536 : word) / 32768.f);
    }
    std::string result;
    if (studio_asr_italian(argv[1], pcm.data(), pcm.size(), 2, cue, stop, &result) != 5) return 3;
    if (studio_asr_italian(argv[1], pcm.data(), pcm.size(), 2, cue, nullptr, &result)) return 4;
    return result == "leggi le condizioni del contratto." ? 0 : 5;
}

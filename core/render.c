#include "scene.h"
#include <errno.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#ifdef _WIN32
#include <fcntl.h>
#include <io.h>
#endif

static int positive(const char *s,int max) {
    char *end;
    errno=0;
    long value=strtol(s,&end,10);
    if (errno || end==s || *end || value<=0 || value>max) return 0;
    return (int)value;
}

int main(int argc,char **argv) {
    if (argc!=4) {
        fprintf(stderr,"Uso: render WIDTH HEIGHT FRAMES > overlay.rgba\n");
        return 2;
    }
    int w=positive(argv[1],4096), h=positive(argv[2],4096), n=positive(argv[3],INT_MAX);
    if (!w || !h || !n || (size_t)w*h>8388608u) return 2;
#ifdef _WIN32
    if (_setmode(_fileno(stdout),_O_BINARY)==-1) return 1;
#endif
    size_t bytes=(size_t)w*h*4;
    unsigned char *pixels=malloc(bytes);
    if (!pixels) return 1;
    int result=0;
    for(int i=0;i<n;++i) {
        if(studio_render_rgba(pixels,w,h,i,n) || fwrite(pixels,1,bytes,stdout)!=bytes) {
            fprintf(stderr,"Errore al fotogramma %d\n",i); result=1; break;
        }
    }
    free(pixels);
    if(fflush(stdout)) result=1;
    return result;
}

#include "../../core/scene.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>

enum { W=160,H=256,N=W*H*4 };
static unsigned char first[N], second[N];
int main(void) {
    assert(studio_render_rgba(NULL,W,H,0,8)==-1);
    assert(studio_render_rgba(first,W,H,-1,8)==-1);
    assert(studio_render_rgba(first,W,H,8,8)==-1);
    assert(studio_render_rgba(first,4096,4096,0,1)==-1);
    assert(studio_render_rgba(first,0,H,0,1)==-1);
    assert(studio_render_rgba(first,W,H,4,9)==0);
    unsigned count[256]={0};
    for (size_t i=3;i<N;i+=4) count[first[i]]++;
    assert(count[0]>W*H/2);
    assert(count[95]>30);
    assert(count[255]>W*H/16);
    assert(count[0]+count[95]+count[255]==W*H);
    assert(studio_render_rgba(second,W,H,4,9)==0 && memcmp(first,second,N)==0);
    assert(studio_render_rgba(second,W,H,0,9)==0 && memcmp(first,second,N)!=0);
    assert(studio_render_rgba(second,W,H,8,9)==0 && memcmp(first,second,N)!=0);
    assert(studio_render_rgba(first,W,H,0,1)==0 && memcmp(first,second,N)==0);
    assert(studio_render_rgba(second,1,1,0,1)==0);
    printf("PASS bounds, single frame, determinism, motion, receiver shadow, opacity\n");
    return 0;
}

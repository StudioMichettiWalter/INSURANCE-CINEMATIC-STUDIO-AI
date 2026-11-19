#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <android/bitmap.h>
#include "scene.h"

JNIEXPORT jint JNICALL Java_it_michetti_studio_Export_render(JNIEnv *env, jclass cls, jobject bitmap, jint frame, jint count) {
    AndroidBitmapInfo info;
    void *pixels;
    if (AndroidBitmap_getInfo(env,bitmap,&info) || info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        info.stride != info.width*4 || AndroidBitmap_lockPixels(env, bitmap, &pixels)) return -1;
    int result = studio_render_rgba(pixels, info.width, info.height, frame, count);
    if (!result) {
        /* Canvas expects premultiplied RGB when compositing translucent shadows. */
        uint8_t *rgba=(uint8_t*)pixels;
        size_t count=(size_t)info.width*info.height;
        for(size_t i=0;i<count;++i,rgba+=4) {
            if(rgba[3] && rgba[3]<255)
                for(int channel=0;channel<3;++channel)
                    rgba[channel]=(uint8_t)((rgba[channel]*rgba[3]+127)/255);
        }
    }
    if (AndroidBitmap_unlockPixels(env,bitmap)) return -1;
    return result;
}
static uint8_t clip(int x) { return x < 0 ? 0 : (x > 255 ? 255 : x); }
JNIEXPORT jint JNICALL Java_it_michetti_studio_Export_yuv(JNIEnv *env,jclass cls,jobject bitmap,
        jobject ybuf,jint ys,jint yp,jobject ubuf,jint us,jint up,jobject vbuf,jint vs,jint vp) {
    AndroidBitmapInfo info;
    void *pixels;
    if (AndroidBitmap_getInfo(env,bitmap,&info) || info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888) return -1;
    uint8_t *y=(*env)->GetDirectBufferAddress(env,ybuf), *u=(*env)->GetDirectBufferAddress(env,ubuf), *v=(*env)->GetDirectBufferAddress(env,vbuf);
    int w=(int)info.width,h=(int)info.height;
    if (!y || !u || !v || w%2 || h%2 || ys<=0 || us<=0 || vs<=0 || yp<=0 || up<=0 || vp<=0 ||
        (int64_t)(h-1)*ys+(int64_t)(w-1)*yp>=(*env)->GetDirectBufferCapacity(env,ybuf) ||
        (int64_t)(h/2-1)*us+(int64_t)(w/2-1)*up>=(*env)->GetDirectBufferCapacity(env,ubuf) ||
        (int64_t)(h/2-1)*vs+(int64_t)(w/2-1)*vp>=(*env)->GetDirectBufferCapacity(env,vbuf) ||
        AndroidBitmap_lockPixels(env,bitmap,&pixels)) return -1;
    for (int row=0;row<h;row+=2) for (int col=0;col<w;col+=2) {
        int sr=0,sg=0,sb=0;
        for (int dy=0;dy<2;++dy) for (int dx=0;dx<2;++dx) {
            uint8_t *p=(uint8_t*)pixels+(row+dy)*info.stride+(col+dx)*4;
            int r=p[0],g=p[1],b=p[2];
            sr+=r;sg+=g;sb+=b;
            y[(row+dy)*ys+(col+dx)*yp]=clip(16+((47*r+157*g+16*b+128)>>8));
        }
        u[row/2*us+col/2*up]=clip(128+((-26*sr-87*sg+113*sb+512)>>10));
        v[row/2*vs+col/2*vp]=clip(128+((112*sr-102*sg-10*sb+512)>>10));
    }
    return AndroidBitmap_unlockPixels(env,bitmap);
}

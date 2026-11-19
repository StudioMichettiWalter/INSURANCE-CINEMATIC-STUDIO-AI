#include "studio_asr.h"
#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cstdio>
#include <exception>
#include <string>
#include <vector>

namespace {
std::atomic<jint> cancelled_ticket{-1};
struct Capture {
    jint ticket;
    std::string json;
    bool have_cue;
};
void error(JNIEnv *env, const char *message) {
    jclass cls=env->FindClass("java/io/IOException");
    if (cls) env->ThrowNew(cls,message);
}
int cancelled(void *p) {
    return static_cast<Capture *>(p)->ticket==cancelled_ticket.load();
}
int cue(int start,int end,const char *text,void *p) {
    auto &c=*static_cast<Capture *>(p);
    if(c.have_cue) c.json+=',';
    c.have_cue=true;
    c.json+="{\"startMs\":"+std::to_string(start)+",\"endMs\":"+std::to_string(end)+",\"text\":\"";
    for(const unsigned char *src=reinterpret_cast<const unsigned char *>(text);*src;++src){
        if(*src<32){
            char e[7]; std::snprintf(e,sizeof(e),"\\u%04x",static_cast<unsigned>(*src));
            c.json+=e;
        }else{
            if(*src=='"' || *src=='\\') c.json+='\\';
            c.json+=static_cast<char>(*src);
        }
    }
    c.json+="\"}";
    return 0;
}
}
extern "C" JNIEXPORT void JNICALL Java_it_michetti_studio_LocalWhisper_cancel(
    JNIEnv *, jclass, jint ticket) { cancelled_ticket.store(ticket); }

extern "C" JNIEXPORT jbyteArray JNICALL Java_it_michetti_studio_LocalWhisper_transcribe(
    JNIEnv *env,jclass,jstring model,jshortArray pcm,jint threads,jint ticket) {
    if(!model || !pcm || ticket<1){error(env,"Parametri ASR non validi.");return nullptr;}
    jsize size=env->GetArrayLength(pcm);
    if(size<1 || size>16000*600){error(env,"Durata ASR fuori intervallo.");return nullptr;}
    try {
        std::vector<float> floats(size);
        jshort block[4096];
        for(int i=0;i<size;i+=4096){
            int n=std::min<int>(4096,size-i);
            env->GetShortArrayRegion(pcm,i,n,block);
            if(env->ExceptionCheck()) return nullptr;
            for(int j=0;j<n;++j) floats[i+j]=block[j]/32768.f;
        }
        const char *path=env->GetStringUTFChars(model,nullptr);
        if(!path) return nullptr;
        Capture result{ticket,"{\"language\":\"it\",\"segments\":[",false};
        int rc;
        try {rc=studio_asr_italian(path,floats.data(),size,threads,cue,cancelled,&result);}
        catch(...) {env->ReleaseStringUTFChars(model,path);throw;}
        env->ReleaseStringUTFChars(model,path);
        if(rc){
            const char *messages[]={"","Input non valido.","Apertura modello fallita.","Inferenza fallita.",
                "Trascrizione vuota o intervalli non validi.","Trascrizione annullata.","Scrittura cue fallita."};
            error(env,(rc>0 && rc<7)?messages[rc]:"Errore ASR.");return nullptr;
        }
        result.json+="]}";
        if(result.json.size()>16*1024*1024){error(env,"Output ASR troppo lungo.");return nullptr;}
        auto arr=env->NewByteArray(static_cast<jsize>(result.json.size()));
        if(arr) env->SetByteArrayRegion(arr,0,env->GetArrayLength(arr),reinterpret_cast<const jbyte *>(result.json.data()));
        return arr;
    } catch(const std::exception &) { error(env,"Risorse insufficienti per ASR.");return nullptr; }
}

#include <jni.h>
#include <string>
#include <GLES2/gl2.h>
#include <GLES3/gl3.h>
#include <android/log.h>

extern "C" {
JNIEXPORT jstring JNICALL
Java_com_camerapbo_DrawGLSurfaceView_stringFromJNI(
        JNIEnv *env,
        jobject /* this */);

JNIEXPORT void JNICALL
Java_com_camerapbo_DrawGLSurfaceView_readPixel(
        JNIEnv *env,
        jobject /* this */,jint pbosize);
}


JNIEXPORT jstring JNICALL
Java_com_camerapbo_DrawGLSurfaceView_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}


JNIEXPORT void JNICALL
Java_com_camerapbo_DrawGLSurfaceView_readPixel(
        JNIEnv *env,
        jobject , jint pboSize) {
    void* buffer = glMapBufferRange(GL_PIXEL_PACK_BUFFER,0,pboSize,GL_MAP_READ_BIT);
}

#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring

JNICALL
Java_edu_washington_cs_ubicomplab_rdt_1reader_CombiledActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

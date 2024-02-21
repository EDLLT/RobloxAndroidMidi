#include <jni.h>
#include <unistd.h>
#include <string>

#include "jni/src/Logging.h"
#include "jni/src/TouchInput.h"

auto touchInput = android_touch::TouchInput::getNewInstance();
//android_touch::Logging::setMode(android_touch::Logging::Mode::Info);

extern "C" JNIEXPORT JNICALL jint Java_com_edllt_robloxmidi_AIDLService_nativeGetUid(
        JNIEnv *env, jobject instance) {
    return getuid();
}

extern "C" JNIEXPORT jint JNICALL Java_com_edllt_robloxmidi_AIDLService_touchDownCommit(
        JNIEnv *env,
        jobject /* this */,
        int x,
        int y,
        int contact) {

    touchInput->down(contact, x, y, 50);
    touchInput->commit();

    return 0;
}

extern "C" JNIEXPORT jint JNICALL Java_com_edllt_robloxmidi_AIDLService_touchUpCommit(
        JNIEnv *env,
        jobject /* this */,
        int contact) {

    touchInput->up(contact);
    touchInput->commit();

    return 0;
}

extern "C" JNIEXPORT jint JNICALL Java_com_edllt_robloxmidi_AIDLService_tapCommit(
        JNIEnv *env,
        jobject /* this */,
        int x,
        int y,
        int contact) {

    touchInput->down(contact, x, y, 50);
    touchInput->commit();
    touchInput->up(contact);
    touchInput->commit();

//    std::string hello = "C++ Integrated successfully along with the other touch things";

    return 0;
}
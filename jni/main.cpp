#include <android/log.h>
#include <stdio.h>
#include <string.h>
#include <sys/system_properties.h>
#include <unistd.h>

#include <jni.h>

#include "zygisk.hpp"

#define PROP_NAME "sys.buildtype"
#define LOG_TAG   "SwitchBuildType"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static void write_all(int fd, const void *buf, size_t len) {
    const char *p = (const char *)buf;
    while (len > 0) {
        ssize_t n = write(fd, p, len);
        if (n <= 0) return;
        p += n;
        len -= (size_t)n;
    }
}

static int read_line(int fd, char *buf, int cap) {
    int n = 0;
    while (n < cap - 1) {
        char c;
        if (read(fd, &c, 1) != 1) {
            buf[n] = 0;
            return -1;
        }
        if (c == '\n') break;
        buf[n++] = c;
    }
    buf[n] = 0;
    return n;
}

/* Runs as root, reads the in-memory property area - no file involved. */
static void companion_handler(int client) {
    char cmd;
    char proc[128];
    char target[PROP_VALUE_MAX] = {0};
    if (read(client, &cmd, 1) != 1 || cmd != 'g') {
        close(client);
        return;
    }
    read_line(client, proc, sizeof(proc));
    __system_property_get(PROP_NAME, target);
    write_all(client, target, strlen(target));
    write_all(client, "\n", 1);
    close(client);
}

static void read_build_type(JNIEnv *env, jclass build, jfieldID fid, char *out, size_t cap) {
    out[0] = 0;
    jstring cur = (jstring)env->GetStaticObjectField(build, fid);
    if (cur == nullptr) return;
    const char *c = env->GetStringUTFChars(cur, nullptr);
    if (c != nullptr) {
        snprintf(out, cap, "%s", c);
        env->ReleaseStringUTFChars(cur, c);
    }
}

class SwitchModule : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        target[0] = 0;
        proc[0] = 0;
        if (args->nice_name != nullptr) {
            const char *name = env->GetStringUTFChars(args->nice_name, nullptr);
            if (name != nullptr) {
                snprintf(proc, sizeof(proc), "%s", name);
                env->ReleaseStringUTFChars(args->nice_name, name);
            }
            env->ExceptionClear();
        }
        int fd = api->connectCompanion();
        if (fd >= 0) {
            char c = 'g';
            write_all(fd, &c, 1);
            write_all(fd, proc, strlen(proc));
            write_all(fd, "\n", 1);
            read_line(fd, target, sizeof(target));
            close(fd);
        }
        api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *args) override {
        (void)args;
        if (target[0] == 0) return;
        jclass build = env->FindClass("android/os/Build");
        if (build == nullptr) {
            env->ExceptionClear();
            LOGI("%s: FindClass(android/os/Build) failed", proc);
            return;
        }
        jfieldID fid = env->GetStaticFieldID(build, "TYPE", "Ljava/lang/String;");
        if (fid == nullptr) {
            env->ExceptionClear();
            LOGI("%s: Build.TYPE not found", proc);
            return;
        }
        char before[PROP_VALUE_MAX] = {0};
        char after[PROP_VALUE_MAX] = {0};
        read_build_type(env, build, fid, before, sizeof(before));
        jstring v = env->NewStringUTF(target);
        if (v == nullptr) {
            env->ExceptionClear();
            LOGI("%s: NewStringUTF failed", proc);
            return;
        }
        env->SetStaticObjectField(build, fid, v);
        env->ExceptionClear();
        read_build_type(env, build, fid, after, sizeof(after));
        LOGI("proc=%s pid=%d target=%s before=%s after=%s %s",
             proc, (int)getpid(), target, before, after,
             strcmp(after, target) == 0 ? "OK" : "FAIL");
    }

private:
    zygisk::Api *api = nullptr;
    JNIEnv *env = nullptr;
    char target[PROP_VALUE_MAX] = {};
    char proc[128] = {};
};

REGISTER_ZYGISK_MODULE(SwitchModule)
REGISTER_ZYGISK_COMPANION(companion_handler)

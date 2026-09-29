#include <stdio.h>
#include <string.h>
#include <sys/system_properties.h>
#include <sys/wait.h>
#include <unistd.h>

#define PROP_NAME "sys.buildtype"

static int valid_value(const char *v) {
    return strcmp(v, "user") == 0 || strcmp(v, "userdebug") == 0 || strcmp(v, "eng") == 0;
}

static int run(char *const argv[]) {
    pid_t pid = fork();
    if (pid < 0) return -1;
    if (pid == 0) {
        execvp(argv[0], argv);
        _exit(127);
    }
    int st = 0;
    if (waitpid(pid, &st, 0) < 0) return -1;
    return WIFEXITED(st) ? WEXITSTATUS(st) : -1;
}

/* resetprop -n edits the property area directly, bypassing property_service,
   which rejects custom names coming from an su shell. */
static int set_prop(const char *val) {
    char *a[] = {(char *)"resetprop", (char *)"-n", (char *)PROP_NAME, (char *)val, NULL};
    if (run(a) == 0) return 0;
    /* APatch 的 resetprop，开机脚本阶段 PATH 里不一定有 */
    char *ap[] = {(char *)"/data/adb/ap/bin/resetprop", (char *)"-n", (char *)PROP_NAME, (char *)val, NULL};
    if (run(ap) == 0) return 0;
    char *b[] = {(char *)"magisk", (char *)"resetprop", (char *)"-n", (char *)PROP_NAME, (char *)val, NULL};
    if (run(b) == 0) return 0;
    char *c[] = {(char *)"/data/adb/magisk/resetprop", (char *)"-n", (char *)PROP_NAME, (char *)val, NULL};
    if (run(c) == 0) return 0;
    return __system_property_set(PROP_NAME, val) == 0 ? 0 : -1;
}

static void print_status(void) {
    char v[PROP_VALUE_MAX] = {0};
    __system_property_get(PROP_NAME, v);
    printf("%s=%s\n", PROP_NAME, v[0] ? v : "(unset)");
}

int main(int argc, char **argv) {
    if (argc < 2) {
        print_status();
        return 0;
    }
    if (!valid_value(argv[1])) {
        fprintf(stderr, "usage: switch [user|userdebug|eng]\n");
        return 1;
    }
    if (set_prop(argv[1]) != 0) {
        fprintf(stderr, "set %s failed (need root and resetprop)\n", PROP_NAME);
        return 1;
    }
    char v[PROP_VALUE_MAX] = {0};
    __system_property_get(PROP_NAME, v);
    if (strcmp(v, argv[1]) != 0) {
        fprintf(stderr, "verify failed: %s=%s\n", PROP_NAME, v);
        return 1;
    }
    printf("%s=%s\n", PROP_NAME, v);
    return 0;
}

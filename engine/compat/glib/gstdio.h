/* Minimal GLib compatibility layer: POSIX file functions. */
#ifndef BG_COMPAT_GSTDIO_H
#define BG_COMPAT_GSTDIO_H
#include "../glib.h"
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#define g_fopen fopen
#define g_open open
#define g_stat stat
#define g_unlink unlink
#define g_remove remove
#define g_rename rename
#define g_mkdir mkdir
typedef struct stat GStatBuf;
#endif

/* Minimal GLib compatibility layer: printf family. */
#ifndef BG_COMPAT_GPRINTF_H
#define BG_COMPAT_GPRINTF_H
#include "../glib.h"
#define g_printf printf
#define g_fprintf fprintf
#define g_sprintf sprintf
#define g_snprintf snprintf
#define g_vsnprintf vsnprintf
#define g_vasprintf vasprintf
#endif

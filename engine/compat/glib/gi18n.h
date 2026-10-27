/* Minimal GLib compatibility layer: translations are not used by the engine. */
#ifndef BG_COMPAT_GI18N_H
#define BG_COMPAT_GI18N_H
#include "../glib.h"
#define _(s) (s)
#define N_(s) (s)
#define Q_(s) (s)
#define C_(ctx, s) (s)
#define NC_(ctx, s) (s)
#define ngettext(s, p, n) ((n) == 1 ? (s) : (p))
#define gettext(s) (s)
#define textdomain(d) ((void) 0)
#define bindtextdomain(d, dir) ((void) 0)
#define bind_textdomain_codeset(d, c) ((void) 0)
#endif

/*
 * Minimal GLib compatibility layer for the GNU Backgammon evaluation engine.
 *
 * The vendored gnubg sources (engine/gnubg) are compiled unmodified. They only
 * use a small part of GLib (memory/string helpers, file loading and a tiny XML
 * parser for match equity tables), so instead of cross-compiling the whole of
 * GLib for Android we provide just that subset here.
 *
 * Only what the engine subset needs is implemented. Anything else is
 * deliberately absent so that new dependencies show up as build errors.
 */
#ifndef BG_COMPAT_GLIB_H
#define BG_COMPAT_GLIB_H

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <alloca.h>
#include <math.h>
#include <time.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ---- basic types ---------------------------------------------------- */
typedef char gchar;
typedef unsigned char guchar;
typedef int gint;
typedef unsigned int guint;
typedef short gshort;
typedef unsigned short gushort;
typedef long glong;
typedef unsigned long gulong;
typedef int8_t gint8;
typedef uint8_t guint8;
typedef int16_t gint16;
typedef uint16_t guint16;
typedef int32_t gint32;
typedef uint32_t guint32;
typedef int64_t gint64;
typedef uint64_t guint64;
typedef float gfloat;
typedef double gdouble;
typedef size_t gsize;
typedef ptrdiff_t gssize;
typedef gint gboolean;
typedef void *gpointer;
typedef const void *gconstpointer;

#ifndef FALSE
#define FALSE (0)
#endif
#ifndef TRUE
#define TRUE (!FALSE)
#endif

#ifndef MIN
#define MIN(a, b) (((a) < (b)) ? (a) : (b))
#endif
#ifndef MAX
#define MAX(a, b) (((a) > (b)) ? (a) : (b))
#endif
#ifndef ABS
#define ABS(a) (((a) < 0) ? -(a) : (a))
#endif
#ifndef CLAMP
#define CLAMP(x, low, high) (((x) > (high)) ? (high) : (((x) < (low)) ? (low) : (x)))
#endif

#define G_PI 3.1415926535897932384626433832795028841971693993751
#define G_PI_2 1.5707963267948966192313216916397514420985846996876
#define G_N_ELEMENTS(arr) (sizeof(arr) / sizeof((arr)[0]))
#define G_GNUC_UNUSED __attribute__((__unused__))
#define G_GNUC_PRINTF(f, a) __attribute__((__format__(__printf__, f, a)))
#define G_LIKELY(e) __builtin_expect(!!(e), 1)
#define G_UNLIKELY(e) __builtin_expect(!!(e), 0)
#define G_STMT_START do
#define G_STMT_END while (0)
#define G_DIR_SEPARATOR '/'
#define G_DIR_SEPARATOR_S "/"
#define G_GSIZE_FORMAT "zu"
#define G_STRINGIFY_ARG(x) #x
#define G_STRINGIFY(x) G_STRINGIFY_ARG(x)
#define G_STRLOC __FILE__ ":" G_STRINGIFY(__LINE__)

/* Pretend to be a recent GLib so that code paths using g_memdup2 etc. are taken. */
#define GLIB_MAJOR_VERSION 2
#define GLIB_MINOR_VERSION 80
#define GLIB_MICRO_VERSION 0
#define GLIB_CHECK_VERSION(major, minor, micro) \
    (GLIB_MAJOR_VERSION > (major) || \
     (GLIB_MAJOR_VERSION == (major) && GLIB_MINOR_VERSION > (minor)) || \
     (GLIB_MAJOR_VERSION == (major) && GLIB_MINOR_VERSION == (minor) && GLIB_MICRO_VERSION >= (micro)))

/* ---- diagnostics ------------------------------------------------------ */
void bg_compat_log(int level, const char *fmt, ...) G_GNUC_PRINTF(2, 3);
#define BG_LOG_DEBUG 0
#define BG_LOG_WARN 1
#define BG_LOG_ERROR 2

#define g_print(...) bg_compat_log(BG_LOG_DEBUG, __VA_ARGS__)
#define g_printerr(...) bg_compat_log(BG_LOG_WARN, __VA_ARGS__)
#define g_message(...) bg_compat_log(BG_LOG_DEBUG, __VA_ARGS__)
#define g_warning(...) bg_compat_log(BG_LOG_WARN, __VA_ARGS__)
#define g_critical(...) bg_compat_log(BG_LOG_ERROR, __VA_ARGS__)
#define g_error(...) do { bg_compat_log(BG_LOG_ERROR, __VA_ARGS__); abort(); } while (0)

#if defined(G_DISABLE_ASSERT)
#define g_assert(expr) do { (void) 0; } while (0)
#define g_assert_not_reached() do { (void) 0; } while (0)
#else
#define g_assert(expr) do { if (G_UNLIKELY(!(expr))) { \
    bg_compat_log(BG_LOG_ERROR, "assertion failed: %s (%s:%d)", #expr, __FILE__, __LINE__); abort(); } } while (0)
#define g_assert_not_reached() do { \
    bg_compat_log(BG_LOG_ERROR, "should not be reached (%s:%d)", __FILE__, __LINE__); abort(); } while (0)
#endif
#define g_return_if_fail(expr) do { if (G_UNLIKELY(!(expr))) { \
    bg_compat_log(BG_LOG_WARN, "%s: assertion '%s' failed", __func__, #expr); return; } } while (0)
#define g_return_val_if_fail(expr, val) do { if (G_UNLIKELY(!(expr))) { \
    bg_compat_log(BG_LOG_WARN, "%s: assertion '%s' failed", __func__, #expr); return (val); } } while (0)

/* ---- memory ----------------------------------------------------------- */
gpointer g_malloc(gsize n);
gpointer g_malloc0(gsize n);
gpointer g_realloc(gpointer p, gsize n);
void g_free(gpointer p);
gpointer g_memdup2(gconstpointer mem, gsize byte_size);
#define g_memdup(mem, n) g_memdup2((mem), (gsize) (n))
#define g_new(type, n) ((type *) g_malloc(sizeof(type) * (gsize) (n)))
#define g_new0(type, n) ((type *) g_malloc0(sizeof(type) * (gsize) (n)))
#define g_renew(type, p, n) ((type *) g_realloc((p), sizeof(type) * (gsize) (n)))
#define g_alloca(size) alloca(size)

/* ---- strings ---------------------------------------------------------- */
gchar *g_strdup(const gchar *s);
gchar *g_strdup_printf(const gchar *fmt, ...) G_GNUC_PRINTF(1, 2);
gchar *g_strdup_vprintf(const gchar *fmt, va_list ap);
const gchar *g_strerror(gint errnum);
gdouble g_ascii_strtod(const gchar *nptr, gchar **endptr);
guint64 g_ascii_strtoull(const gchar *nptr, gchar **endptr, guint base);
gint g_ascii_strcasecmp(const gchar *s1, const gchar *s2);
gint g_ascii_strncasecmp(const gchar *s1, const gchar *s2, gsize n);
gchar *g_utf8_casefold(const gchar *str, gssize len);
gchar *g_build_filename(const gchar *first, ...);

/* ---- errors ----------------------------------------------------------- */
typedef struct {
    guint32 domain;
    gint code;
    gchar *message;
} GError;
void g_error_free(GError *error);

/* ---- files ------------------------------------------------------------ */
typedef enum {
    G_FILE_TEST_IS_REGULAR = 1 << 0,
    G_FILE_TEST_IS_SYMLINK = 1 << 1,
    G_FILE_TEST_IS_DIR = 1 << 2,
    G_FILE_TEST_IS_EXECUTABLE = 1 << 3,
    G_FILE_TEST_EXISTS = 1 << 4
} GFileTest;
gboolean g_file_test(const gchar *filename, GFileTest test);
gboolean g_file_get_contents(const gchar *filename, gchar **contents, gsize *length, GError **error);

typedef struct _GMappedFile GMappedFile;
GMappedFile *g_mapped_file_new(const gchar *filename, gboolean writable, GError **error);
gchar *g_mapped_file_get_contents(GMappedFile *file);
gsize g_mapped_file_get_length(GMappedFile *file);
void g_mapped_file_unref(GMappedFile *file);

/* ---- minimal SAX-style XML parser (used for match equity tables) ------ */
typedef struct _GMarkupParseContext GMarkupParseContext;
typedef enum { G_MARKUP_DEFAULT_FLAGS = 0 } GMarkupParseFlags;
typedef void (*GDestroyNotify)(gpointer data);
typedef struct {
    void (*start_element)(GMarkupParseContext *context, const gchar *element_name,
                          const gchar **attribute_names, const gchar **attribute_values,
                          gpointer user_data, GError **error);
    void (*end_element)(GMarkupParseContext *context, const gchar *element_name,
                        gpointer user_data, GError **error);
    void (*text)(GMarkupParseContext *context, const gchar *text, gsize text_len,
                 gpointer user_data, GError **error);
    void (*passthrough)(GMarkupParseContext *context, const gchar *passthrough_text,
                        gsize text_len, gpointer user_data, GError **error);
    void (*error)(GMarkupParseContext *context, GError *error, gpointer user_data);
} GMarkupParser;
GMarkupParseContext *g_markup_parse_context_new(const GMarkupParser *parser, GMarkupParseFlags flags,
                                                gpointer user_data, GDestroyNotify user_data_dnotify);
gboolean g_markup_parse_context_parse(GMarkupParseContext *context, const gchar *text, gssize text_len,
                                      GError **error);
void g_markup_parse_context_free(GMarkupParseContext *context);

/* ---- singly used containers (declared in headers, unused at runtime) --- */
typedef struct _GList {
    gpointer data;
    struct _GList *next;
    struct _GList *prev;
} GList;
typedef struct {
    gchar *str;
    gsize len;
    gsize allocated_len;
} GString;

/* ---- threading types (declared by multithread.h; unused since the engine
 *      is built without USE_MULTITHREAD) ---------------------------------- */
typedef struct { void *p; } GMutex;
typedef struct { void *p; } GCond;
typedef struct { void *p; } GPrivate;
typedef struct _GThread GThread;

/* ---- atomics (only used by the cache when multithreading is enabled) -- */
#define g_atomic_int_get(p) __atomic_load_n((p), __ATOMIC_SEQ_CST)
#define g_atomic_int_set(p, v) __atomic_store_n((p), (v), __ATOMIC_SEQ_CST)
#define g_atomic_int_add(p, v) ((void) __atomic_fetch_add((p), (v), __ATOMIC_SEQ_CST))
#define g_atomic_int_exchange_and_add(p, v) __atomic_fetch_add((p), (v), __ATOMIC_SEQ_CST)
#define g_atomic_int_dec_and_test(p) (__atomic_sub_fetch((p), 1, __ATOMIC_SEQ_CST) == 0)

#ifdef __cplusplus
}
#endif
#endif /* BG_COMPAT_GLIB_H */

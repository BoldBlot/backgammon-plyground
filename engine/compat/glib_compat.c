/*
 * Implementation of the minimal GLib compatibility layer (see glib.h).
 */
#include "glib.h"

#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#if defined(__ANDROID__)
#include <android/log.h>
#endif

/* ---- diagnostics ------------------------------------------------------ */

void
bg_compat_log(int level, const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
#if defined(__ANDROID__)
    int prio = level == BG_LOG_ERROR ? ANDROID_LOG_ERROR : level == BG_LOG_WARN ? ANDROID_LOG_WARN : ANDROID_LOG_DEBUG;
    __android_log_vprint(prio, "gnubg", fmt, ap);
#else
    if (level > BG_LOG_DEBUG || getenv("BG_ENGINE_VERBOSE")) {
        vfprintf(stderr, fmt, ap);
        fputc('\n', stderr);
    }
#endif
    va_end(ap);
}

/* ---- memory ----------------------------------------------------------- */

gpointer
g_malloc(gsize n)
{
    if (n == 0)
        return NULL;
    void *p = malloc(n);
    if (!p)
        g_error("out of memory allocating %zu bytes", n);
    return p;
}

gpointer
g_malloc0(gsize n)
{
    if (n == 0)
        return NULL;
    void *p = calloc(1, n);
    if (!p)
        g_error("out of memory allocating %zu bytes", n);
    return p;
}

gpointer
g_realloc(gpointer p, gsize n)
{
    if (n == 0) {
        free(p);
        return NULL;
    }
    void *q = realloc(p, n);
    if (!q)
        g_error("out of memory reallocating %zu bytes", n);
    return q;
}

void
g_free(gpointer p)
{
    free(p);
}

gpointer
g_memdup2(gconstpointer mem, gsize byte_size)
{
    if (!mem || byte_size == 0)
        return NULL;
    void *p = g_malloc(byte_size);
    memcpy(p, mem, byte_size);
    return p;
}

/* ---- strings ---------------------------------------------------------- */

gchar *
g_strdup(const gchar *s)
{
    if (!s)
        return NULL;
    size_t n = strlen(s) + 1;
    gchar *d = g_malloc(n);
    memcpy(d, s, n);
    return d;
}

gchar *
g_strdup_vprintf(const gchar *fmt, va_list ap)
{
    va_list ap2;
    va_copy(ap2, ap);
    int n = vsnprintf(NULL, 0, fmt, ap2);
    va_end(ap2);
    if (n < 0)
        return NULL;
    gchar *s = g_malloc((gsize) n + 1);
    vsnprintf(s, (size_t) n + 1, fmt, ap);
    return s;
}

gchar *
g_strdup_printf(const gchar *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    gchar *s = g_strdup_vprintf(fmt, ap);
    va_end(ap);
    return s;
}

const gchar *
g_strerror(gint errnum)
{
    return strerror(errnum);
}

/* Locale independent strtod: the engine data files always use '.' */
gdouble
g_ascii_strtod(const gchar *nptr, gchar **endptr)
{
    const char *p = nptr;
    while (isspace((unsigned char) *p))
        p++;
    double sign = 1.0;
    if (*p == '+' || *p == '-') {
        if (*p == '-')
            sign = -1.0;
        p++;
    }
    double value = 0.0;
    int digits = 0;
    while (isdigit((unsigned char) *p)) {
        value = value * 10.0 + (*p - '0');
        p++;
        digits++;
    }
    if (*p == '.') {
        p++;
        double scale = 0.1;
        while (isdigit((unsigned char) *p)) {
            value += (*p - '0') * scale;
            scale *= 0.1;
            p++;
            digits++;
        }
    }
    if (digits == 0) {
        if (endptr)
            *endptr = (gchar *) nptr;
        return 0.0;
    }
    if (*p == 'e' || *p == 'E') {
        const char *q = p + 1;
        int esign = 1, exp = 0, edigits = 0;
        if (*q == '+' || *q == '-') {
            if (*q == '-')
                esign = -1;
            q++;
        }
        while (isdigit((unsigned char) *q)) {
            exp = exp * 10 + (*q - '0');
            q++;
            edigits++;
        }
        if (edigits) {
            value *= pow(10.0, esign * exp);
            p = q;
        }
    }
    if (endptr)
        *endptr = (gchar *) p;
    return sign * value;
}

guint64
g_ascii_strtoull(const gchar *nptr, gchar **endptr, guint base)
{
    return (guint64) strtoull(nptr, endptr, (int) base);
}

gint
g_ascii_strcasecmp(const gchar *s1, const gchar *s2)
{
    while (*s1 && *s2) {
        int c1 = tolower((unsigned char) *s1++), c2 = tolower((unsigned char) *s2++);
        if (c1 != c2)
            return c1 - c2;
    }
    return tolower((unsigned char) *s1) - tolower((unsigned char) *s2);
}

gint
g_ascii_strncasecmp(const gchar *s1, const gchar *s2, gsize n)
{
    while (n-- > 0) {
        int c1 = tolower((unsigned char) *s1++), c2 = tolower((unsigned char) *s2++);
        if (c1 != c2 || !c1)
            return c1 - c2;
    }
    return 0;
}

/* ASCII-only case folding; sufficient for the engine's identifiers. */
gchar *
g_utf8_casefold(const gchar *str, gssize len)
{
    size_t n = len < 0 ? strlen(str) : (size_t) len;
    gchar *s = g_malloc(n + 1);
    for (size_t i = 0; i < n; i++)
        s[i] = (gchar) tolower((unsigned char) str[i]);
    s[n] = 0;
    return s;
}

gchar *
g_build_filename(const gchar *first, ...)
{
    va_list ap;
    size_t total = 0;
    const gchar *part;

    va_start(ap, first);
    for (part = first; part; part = va_arg(ap, const gchar *))
        total += strlen(part) + 1;
    va_end(ap);

    gchar *out = g_malloc(total + 1);
    out[0] = 0;
    va_start(ap, first);
    for (part = first; part; part = va_arg(ap, const gchar *)) {
        if (!*part)
            continue;
        size_t len = strlen(out);
        if (len > 0 && out[len - 1] != '/')
            strcat(out, "/");
        const gchar *p = part;
        if (len > 0)
            while (*p == '/')
                p++;
        strcat(out, p);
    }
    va_end(ap);
    return out;
}

/* ---- errors ----------------------------------------------------------- */

static GError *
new_error(const char *fmt, ...)
{
    GError *e = g_new0(GError, 1);
    va_list ap;
    va_start(ap, fmt);
    e->message = g_strdup_vprintf(fmt, ap);
    va_end(ap);
    return e;
}

void
g_error_free(GError *error)
{
    if (!error)
        return;
    g_free(error->message);
    g_free(error);
}

/* ---- files ------------------------------------------------------------ */

gboolean
g_file_test(const gchar *filename, GFileTest test)
{
    struct stat st;
    if ((test & G_FILE_TEST_EXISTS) && access(filename, F_OK) == 0)
        return TRUE;
    if (stat(filename, &st) != 0)
        return FALSE;
    if ((test & G_FILE_TEST_IS_REGULAR) && S_ISREG(st.st_mode))
        return TRUE;
    if ((test & G_FILE_TEST_IS_DIR) && S_ISDIR(st.st_mode))
        return TRUE;
    if ((test & G_FILE_TEST_IS_EXECUTABLE) && access(filename, X_OK) == 0 && !S_ISDIR(st.st_mode))
        return TRUE;
    return FALSE;
}

gboolean
g_file_get_contents(const gchar *filename, gchar **contents, gsize *length, GError **error)
{
    *contents = NULL;
    FILE *f = fopen(filename, "rb");
    if (!f) {
        if (error)
            *error = new_error("cannot open %s: %s", filename, strerror(errno));
        return FALSE;
    }
    if (fseek(f, 0, SEEK_END) != 0) {
        fclose(f);
        if (error)
            *error = new_error("cannot seek %s", filename);
        return FALSE;
    }
    long size = ftell(f);
    rewind(f);
    gchar *buf = g_malloc((gsize) size + 1);
    size_t got = fread(buf, 1, (size_t) size, f);
    fclose(f);
    if (got != (size_t) size) {
        g_free(buf);
        if (error)
            *error = new_error("short read on %s", filename);
        return FALSE;
    }
    buf[size] = 0;
    *contents = buf;
    if (length)
        *length = (gsize) size;
    return TRUE;
}

struct _GMappedFile {
    gchar *data;
    gsize length;
};

GMappedFile *
g_mapped_file_new(const gchar *filename, gboolean writable, GError **error)
{
    int fd = open(filename, writable ? O_RDWR : O_RDONLY);
    if (fd < 0) {
        if (error)
            *error = new_error("cannot open %s: %s", filename, strerror(errno));
        return NULL;
    }
    struct stat st;
    if (fstat(fd, &st) != 0) {
        close(fd);
        if (error)
            *error = new_error("cannot stat %s", filename);
        return NULL;
    }
    GMappedFile *m = g_new0(GMappedFile, 1);
    m->length = (gsize) st.st_size;
    if (m->length > 0) {
        void *p = mmap(NULL, m->length, writable ? PROT_READ | PROT_WRITE : PROT_READ, MAP_PRIVATE, fd, 0);
        if (p == MAP_FAILED) {
            close(fd);
            g_free(m);
            if (error)
                *error = new_error("cannot mmap %s", filename);
            return NULL;
        }
        m->data = p;
    }
    close(fd);
    return m;
}

gchar *
g_mapped_file_get_contents(GMappedFile *file)
{
    return file->data;
}

gsize
g_mapped_file_get_length(GMappedFile *file)
{
    return file->length;
}

void
g_mapped_file_unref(GMappedFile *file)
{
    if (!file)
        return;
    if (file->data)
        munmap(file->data, file->length);
    g_free(file);
}

/* ---- minimal XML parser ----------------------------------------------- */
/*
 * Supports what the match equity table files use: an XML declaration,
 * DOCTYPE, comments, elements with quoted attributes, self-closing elements,
 * character data and the five predefined entities. No CDATA, no namespaces.
 */

#define MAX_ATTRS 16

struct _GMarkupParseContext {
    const GMarkupParser *parser;
    gpointer user_data;
    GDestroyNotify dnotify;
};

GMarkupParseContext *
g_markup_parse_context_new(const GMarkupParser *parser, GMarkupParseFlags flags, gpointer user_data,
                           GDestroyNotify user_data_dnotify)
{
    (void) flags;
    GMarkupParseContext *c = g_new0(GMarkupParseContext, 1);
    c->parser = parser;
    c->user_data = user_data;
    c->dnotify = user_data_dnotify;
    return c;
}

void
g_markup_parse_context_free(GMarkupParseContext *context)
{
    if (!context)
        return;
    if (context->dnotify)
        context->dnotify(context->user_data);
    g_free(context);
}

/* Decode entities in [s, s+n) into a newly allocated string. */
static gchar *
decode_text(const char *s, size_t n, size_t *out_len)
{
    gchar *out = g_malloc(n + 1);
    size_t j = 0;
    for (size_t i = 0; i < n; i++) {
        if (s[i] == '&') {
            static const struct { const char *ent; char ch; } ents[] = {
                {"&lt;", '<'}, {"&gt;", '>'}, {"&amp;", '&'}, {"&quot;", '"'}, {"&apos;", '\''}
            };
            int matched = 0;
            for (size_t k = 0; k < G_N_ELEMENTS(ents); k++) {
                size_t el = strlen(ents[k].ent);
                if (i + el <= n && strncmp(s + i, ents[k].ent, el) == 0) {
                    out[j++] = ents[k].ch;
                    i += el - 1;
                    matched = 1;
                    break;
                }
            }
            if (!matched)
                out[j++] = s[i];
        } else {
            out[j++] = s[i];
        }
    }
    out[j] = 0;
    if (out_len)
        *out_len = j;
    return out;
}

static int
is_name_char(char c)
{
    return isalnum((unsigned char) c) || c == '_' || c == '-' || c == ':' || c == '.';
}

gboolean
g_markup_parse_context_parse(GMarkupParseContext *context, const gchar *text, gssize text_len, GError **error)
{
    const GMarkupParser *ps = context->parser;
    const char *p = text;
    const char *end = text + (text_len < 0 ? (gssize) strlen(text) : text_len);
    GError *err = NULL;

#define FAIL(msg) do { err = new_error("XML parse error at offset %ld: %s", (long) (p - text), msg); goto fail; } while (0)

    while (p < end && !err) {
        if (*p != '<') {
            const char *start = p;
            while (p < end && *p != '<')
                p++;
            if (ps->text) {
                size_t len;
                gchar *t = decode_text(start, (size_t) (p - start), &len);
                ps->text(context, t, len, context->user_data, &err);
                g_free(t);
            }
            continue;
        }
        /* p points at '<' */
        if (end - p >= 4 && strncmp(p, "<!--", 4) == 0) {
            const char *q = strstr(p + 4, "-->");
            if (!q || q >= end)
                FAIL("unterminated comment");
            p = q + 3;
            continue;
        }
        if (end - p >= 2 && (p[1] == '?' || p[1] == '!')) {
            /* XML declaration / processing instruction / DOCTYPE */
            const char *q = p + 2;
            int depth = 0;
            while (q < end && !(*q == '>' && depth == 0)) {
                if (*q == '[')
                    depth++;
                else if (*q == ']')
                    depth--;
                q++;
            }
            if (q >= end)
                FAIL("unterminated declaration");
            p = q + 1;
            continue;
        }
        if (end - p >= 2 && p[1] == '/') {
            const char *q = p + 2;
            const char *name = q;
            while (q < end && is_name_char(*q))
                q++;
            gchar *ename = decode_text(name, (size_t) (q - name), NULL);
            while (q < end && isspace((unsigned char) *q))
                q++;
            if (q >= end || *q != '>') {
                g_free(ename);
                FAIL("malformed end tag");
            }
            if (ps->end_element)
                ps->end_element(context, ename, context->user_data, &err);
            g_free(ename);
            p = q + 1;
            continue;
        }
        /* start tag */
        {
            const char *q = p + 1;
            const char *name = q;
            while (q < end && is_name_char(*q))
                q++;
            if (q == name)
                FAIL("missing element name");
            gchar *ename = decode_text(name, (size_t) (q - name), NULL);
            gchar *anames[MAX_ATTRS + 1], *avalues[MAX_ATTRS + 1];
            int nattr = 0, self_closing = 0;
            for (;;) {
                while (q < end && isspace((unsigned char) *q))
                    q++;
                if (q >= end)
                    break;
                if (*q == '/' && q + 1 < end && q[1] == '>') {
                    self_closing = 1;
                    q += 2;
                    break;
                }
                if (*q == '>') {
                    q++;
                    break;
                }
                const char *an = q;
                while (q < end && is_name_char(*q))
                    q++;
                const char *an_end = q;
                while (q < end && isspace((unsigned char) *q))
                    q++;
                if (q >= end || *q != '=' || an == an_end || nattr >= MAX_ATTRS) {
                    for (int i = 0; i < nattr; i++) {
                        g_free(anames[i]);
                        g_free(avalues[i]);
                    }
                    g_free(ename);
                    FAIL("malformed attribute");
                }
                q++;
                while (q < end && isspace((unsigned char) *q))
                    q++;
                char quote = q < end ? *q : 0;
                if (quote != '"' && quote != '\'') {
                    for (int i = 0; i < nattr; i++) {
                        g_free(anames[i]);
                        g_free(avalues[i]);
                    }
                    g_free(ename);
                    FAIL("unquoted attribute value");
                }
                const char *av = ++q;
                while (q < end && *q != quote)
                    q++;
                anames[nattr] = decode_text(an, (size_t) (an_end - an), NULL);
                avalues[nattr] = decode_text(av, (size_t) (q - av), NULL);
                nattr++;
                if (q < end)
                    q++;
            }
            anames[nattr] = NULL;
            avalues[nattr] = NULL;
            if (ps->start_element)
                ps->start_element(context, ename, (const gchar **) anames, (const gchar **) avalues,
                                  context->user_data, &err);
            if (!err && self_closing && ps->end_element)
                ps->end_element(context, ename, context->user_data, &err);
            for (int i = 0; i < nattr; i++) {
                g_free(anames[i]);
                g_free(avalues[i]);
            }
            g_free(ename);
            p = q;
        }
    }
    if (!err)
        return TRUE;

  fail:
    if (ps->error)
        ps->error(context, err, context->user_data);
    if (error)
        *error = err;
    else
        g_error_free(err);
    return FALSE;
#undef FAIL
}

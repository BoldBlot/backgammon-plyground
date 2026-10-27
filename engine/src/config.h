/*
 * Build configuration for the vendored GNU Backgammon engine subset.
 * Replaces the autoconf-generated config.h of the full gnubg build.
 */
#ifndef BG_ENGINE_CONFIG_H
#define BG_ENGINE_CONFIG_H

#define VERSION "1.08.003"
#define PACKAGE "gnubg"
#define GETTEXT_PACKAGE "gnubg"
#define PKGDATADIR "."
#define LOCALEDIR "."

#define HAVE_UNISTD_H 1
#define HAVE_SIGACTION 1
#define HAVE___BUILTIN_CLZ 1
#define HAVE___ATTRIBUTE__ 1
#define HAVE_FUNC_ATTRIBUTE_PURE 1
#define HAVE_POSIX_MEMALIGN 1

/* ENVIRONMENT32/64 are defined by gnubg's lib/neuralnet.h itself. */

/*
 * The engine runs on a single dedicated thread in the app, so gnubg's own
 * multithreading support (USE_MULTITHREAD) is left disabled.
 */

/* SIMD: NEON on ARM, SSE2 on x86. Selected from compiler predefined macros. */
#if !defined(BG_DISABLE_SIMD)
#if defined(__aarch64__) || (defined(__arm__) && defined(__ARM_NEON))
#define USE_SIMD_INSTRUCTIONS 1
#define USE_NEON 1
#define HAVE_NEON 1
#elif defined(__x86_64__) || (defined(__i386__) && defined(__SSE2__))
#define USE_SIMD_INSTRUCTIONS 1
#define USE_SSE2 1
#define HAVE_SSE 1
#endif
#endif

/* Runtime CPU feature probing is unnecessary: the ABI guarantees NEON/SSE2. */
#define DISABLE_CPUTEST 1

#endif /* BG_ENGINE_CONFIG_H */

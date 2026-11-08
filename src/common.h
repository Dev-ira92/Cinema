#pragma once
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LOG(...) fprintf(stderr, __VA_ARGS__), fprintf(stderr, "\n")

/* Application identity and error logger used by main.c. */
#ifndef APP_NAME
#define APP_NAME "CineHub"
#endif

#ifndef ERR
#define ERR(...) do { \
    fprintf(stderr, "[ERROR] "); \
    fprintf(stderr, __VA_ARGS__); \
    fputc('\n', stderr); \
} while (0)
#endif

typedef struct {
    unsigned char *d;
    size_t         n;
    size_t         cap;
    int            over;
} membuf_t;

char *read_file(const char *path, size_t *len_out);
int   write_file(const char *path, const void *data, size_t len);

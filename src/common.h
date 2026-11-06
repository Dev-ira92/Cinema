#pragma once
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LOG(...) fprintf(stderr, __VA_ARGS__), fprintf(stderr, "\n")

typedef struct {
    unsigned char *d;
    size_t         n;
} membuf_t;

int  http_get_ex(const char *url, membuf_t *out, void *abort_flag);
char *read_file(const char *path, size_t *len_out);
int   write_file(const char *path, const void *data, size_t len);

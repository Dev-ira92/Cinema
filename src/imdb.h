#ifndef IMDB_H
#define IMDB_H

#include <stddef.h>

#define IM_MAX_RESULTS 10
#define IM_MAX_SOURCES 12

typedef struct {
    char id[16];
    char title[128];
    char year[12];
    char type[16];
} im_hit_t;

typedef struct {
    char id[16];
    char title[128];
    char year[12];
    char rated[16];
    char runtime[24];
    char genre[96];
    char director[96];
    char actors[160];
    char plot[768];
    char rating[8];
    char poster[512];
} im_info_t;

typedef struct {
    char server[48];
    char quality[24];
    char url[512];
} im_source_t;

typedef int (*im_fetch_fn)(void *ud, const char *url,
                           char **body, size_t *len);

int im_search(im_fetch_fn f, void *ud, const char *key,
              const char *query, im_hit_t *out, int cap,
              char *err, size_t errcap);

int im_details(im_fetch_fn f, void *ud, const char *key,
               const char *id, im_info_t *out,
               char *err, size_t errcap);

int im_sources_for(const char *json, size_t len, const char *id,
                   im_source_t *out, int cap);

void im_url_encode(char *dst, size_t cap, const char *src);

int im_remote_sources(im_fetch_fn f, void *ud, const char *api_base,
                      const char *id, im_source_t *out, int cap);

int im_scrape_sources(im_fetch_fn f, void *ud, const char *id,
                      im_source_t *out, int cap);

#endif

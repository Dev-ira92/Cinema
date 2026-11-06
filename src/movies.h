#ifndef MOVIES_H
#define MOVIES_H

#include <SDL2/SDL.h>

#define M_TITLE   128
#define M_CAT     64
#define M_URL     512
#define M_DESC    1024

#define POSTER_W  240
#define POSTER_H  360

typedef struct {
    char title[M_TITLE];
    char category[M_CAT];
    char cat_label[M_CAT];
    char poster_url[M_URL];
    char video_url[M_URL];
    char desc[M_DESC];

    SDL_atomic_t pstate;
    unsigned char *px;
    SDL_Texture  *tex;
} movie_t;

typedef struct {
    movie_t *m;
    int      n;
    char   (*cats)[M_CAT];
    int      n_cats;
} movies_t;

int  movies_parse(movies_t *mv, const char *json, size_t len);
void movies_free(movies_t *mv);

#endif

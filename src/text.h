#ifndef TEXT_H
#define TEXT_H

#include <SDL2/SDL.h>
#include <stdint.h>

#define TEXT_MAX 512

int  text_visual(const char *utf8, uint32_t *out, int cap, int dir,
                 int *is_rtl);

typedef struct text_font text_font_t;

text_font_t *font_open(SDL_Renderer *r, const char *path);
void         font_close(text_font_t *f);
int          font_width(text_font_t *f, int px, const char *utf8, int dir);
int          font_draw(text_font_t *f, int box_x, int y, int box_w, int px,
                       SDL_Color c, const char *utf8, int dir);
int          font_draw_wrapped(text_font_t *f, int box_x, int y, int box_w,
                               int px, int line_h, int max_lines,
                               SDL_Color c, const char *utf8);
int          text_has_nonascii(const char *s);

#endif

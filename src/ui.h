#ifndef UI_H
#define UI_H

#include <SDL2/SDL.h>

typedef struct {
    SDL_Renderer *r;
    SDL_Texture  *font;
    SDL_Texture  *bg;
    int           screen_w, screen_h;
} ui_t;

extern const SDL_Color COL_BG;
extern const SDL_Color COL_PANEL;
extern const SDL_Color COL_SEL;
extern const SDL_Color COL_TEXT;
extern const SDL_Color COL_DIM;
extern const SDL_Color COL_ACCENT;
extern const SDL_Color COL_HUD;
extern const SDL_Color COL_LINE;
extern const SDL_Color COL_MAGIC;

int  ui_init(ui_t *ui, SDL_Renderer *r, int w, int h);
void ui_shutdown(ui_t *ui);
void ui_background(ui_t *ui);
void ui_rune_band(ui_t *ui, int y, int h);
void ui_glow_frame(ui_t *ui, int x, int y, int w, int h);
void ui_set_color(ui_t *ui, SDL_Color c);
void ui_fill_rect(ui_t *ui, int x, int y, int w, int h, SDL_Color c);
void ui_text(ui_t *ui, int x, int y, int scale, SDL_Color c,
             const char *fmt, ...);
int  ui_text_width(int scale, const char *s);
void ui_ellipsize(char *dst, size_t dstlen, const char *src, int max_chars);

#endif

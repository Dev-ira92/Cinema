#include "ui.h"
#include "common.h"
#include <stdarg.h>
#include "font8x8_basic.h"

#define GLYPH_W    8
#define GLYPH_H    8
#define ATLAS_COLS 16
#define ATLAS_ROWS 8

const SDL_Color COL_BG     = {  10,   8,  30, 255 };
const SDL_Color COL_PANEL  = {  30,  24,  70, 255 };
const SDL_Color COL_SEL    = {  62,  44, 124, 255 };
const SDL_Color COL_TEXT   = { 240, 236, 255, 255 };
const SDL_Color COL_DIM    = { 164, 156, 210, 255 };
const SDL_Color COL_ACCENT = { 120, 232, 255, 255 };
const SDL_Color COL_MAGIC  = { 196, 132, 255, 255 };
const SDL_Color COL_HUD    = {   0,   0,   0, 185 };
const SDL_Color COL_LINE   = { 150, 120, 255, 120 };

static SDL_Texture *make_background(SDL_Renderer *r)
{
    enum { BW = 4, BH = 256, NSTOP = 4 };
    static uint32_t px[BW * BH];
    static const double pos[NSTOP]    = { 0.00, 0.50, 0.82, 1.00 };
    static const int    col[NSTOP][3] = {
        { 44, 20, 92 }, { 22, 24, 82 },
        {  6, 44, 70 }, {  4, 10, 28 }
    };
    for (int y = 0; y < BH; y++) {
        double t = (double)y / (BH - 1);
        int    s = 0;
        while (s < NSTOP - 2 && t > pos[s + 1]) s++;
        double u = (t - pos[s]) / (pos[s + 1] - pos[s]);
        u = u * u * (3.0 - 2.0 * u);
        uint32_t c[3];
        for (int k = 0; k < 3; k++)
            c[k] = (uint32_t)(col[s][k] +
                   (col[s+1][k] - col[s][k]) * u + 0.5);
        uint32_t v = 0xFF000000u | (c[2]<<16) | (c[1]<<8) | c[0];
        for (int x = 0; x < BW; x++) px[y * BW + x] = v;
    }
    SDL_Texture *t = SDL_CreateTexture(r, SDL_PIXELFORMAT_ABGR8888,
                                       SDL_TEXTUREACCESS_STATIC, BW, BH);
    if (t) SDL_UpdateTexture(t, NULL, px, BW * 4);
    return t;
}

int ui_init(ui_t *ui, SDL_Renderer *r, int w, int h)
{
    memset(ui, 0, sizeof(*ui));
    ui->r = r; ui->screen_w = w; ui->screen_h = h;

    int aw = ATLAS_COLS * GLYPH_W;
    int ah = ATLAS_ROWS * GLYPH_H;
    uint32_t *px = calloc((size_t)aw * ah, sizeof(uint32_t));
    if (!px) return -1;

    for (int ch = 0; ch < 128; ch++) {
        int gx = (ch % ATLAS_COLS) * GLYPH_W;
        int gy = (ch / ATLAS_COLS) * GLYPH_H;
        for (int row = 0; row < GLYPH_H; row++) {
            unsigned char bits = font8x8_basic[ch][row];
            for (int col = 0; col < GLYPH_W; col++)
                if (bits & (1u << col))
                    px[(size_t)(gy+row)*aw + (gx+col)] = 0xFFFFFFFFu;
        }
    }
    ui->font = SDL_CreateTexture(r, SDL_PIXELFORMAT_ABGR8888,
                                 SDL_TEXTUREACCESS_STATIC, aw, ah);
    if (!ui->font) { free(px); return -1; }
    SDL_UpdateTexture(ui->font, NULL, px, aw * 4);
    SDL_SetTextureBlendMode(ui->font, SDL_BLENDMODE_BLEND);
    free(px);
    ui->bg = make_background(r);
    return 0;
}

void ui_shutdown(ui_t *ui)
{
    if (ui->font) SDL_DestroyTexture(ui->font); ui->font = NULL;
    if (ui->bg)   SDL_DestroyTexture(ui->bg);   ui->bg   = NULL;
}

void ui_set_color(ui_t *ui, SDL_Color c)
{
    SDL_SetRenderDrawColor(ui->r, c.r, c.g, c.b, c.a);
}

void ui_fill_rect(ui_t *ui, int x, int y, int w, int h, SDL_Color c)
{
    SDL_Rect rc = { x, y, w, h };
    SDL_SetRenderDrawBlendMode(ui->r, SDL_BLENDMODE_BLEND);
    ui_set_color(ui, c);
    SDL_RenderFillRect(ui->r, &rc);
}

int ui_text_width(int scale, const char *s)
{
    return (int)strlen(s) * GLYPH_W * scale;
}

void ui_text(ui_t *ui, int x, int y, int scale, SDL_Color c,
             const char *fmt, ...)
{
    char buf[512]; va_list ap;
    va_start(ap, fmt); vsnprintf(buf, sizeof buf, fmt, ap); va_end(ap);
    SDL_SetTextureColorMod(ui->font, c.r, c.g, c.b);
    SDL_SetTextureAlphaMod(ui->font, c.a);
    int cx = x;
    for (const unsigned char *p = (const unsigned char *)buf; *p; p++) {
        unsigned char ch = *p;
        if (ch >= 128) ch = '?';
        if (ch != ' ') {
            SDL_Rect src = { (ch%ATLAS_COLS)*GLYPH_W,
                             (ch/ATLAS_COLS)*GLYPH_H, GLYPH_W, GLYPH_H };
            SDL_Rect dst = { cx, y, GLYPH_W*scale, GLYPH_H*scale };
            SDL_RenderCopy(ui->r, ui->font, &src, &dst);
        }
        cx += GLYPH_W * scale;
    }
}

void ui_ellipsize(char *dst, size_t dstlen, const char *src, int max_chars)
{
    size_t n = strlen(src);
    if (max_chars < 4 || n <= (size_t)max_chars) {
        snprintf(dst, dstlen, "%s", src); return;
    }
    snprintf(dst, dstlen, "%.*s...", max_chars - 3, src);
}

static void sparkles(ui_t *ui)
{
    enum { N = 70 };
    static int           init;
    static short         sx[N], sy[N];
    static unsigned char ph[N], big[N];
    if (!init) {
        uint32_t s = 0x9E3779B9u;
        for (int i = 0; i < N; i++) {
            s = s*1664525u+1013904223u; sx[i]=(short)((s>>8)%(uint32_t)ui->screen_w);
            s = s*1664525u+1013904223u; sy[i]=(short)((s>>8)%(uint32_t)ui->screen_h);
            s = s*1664525u+1013904223u; ph[i]=(unsigned char)(s>>16);
            big[i]=(unsigned char)(i%9==0);
        }
        init=1;
    }
    uint32_t now = SDL_GetTicks();
    for (int i = 0; i < N; i++) {
        int t = (int)((now/9+(uint32_t)ph[i]*7)%512);
        int w = t < 256 ? t : 511-t;
        SDL_Color c = (i%3==0)?COL_MAGIC:(i%3==1)?COL_ACCENT:COL_TEXT;
        c.a = (unsigned char)(30 + w*150/255);
        if (big[i]) {
            ui_fill_rect(ui, sx[i]-5, sy[i], 11, 1, c);
            ui_fill_rect(ui, sx[i], sy[i]-5, 1, 11, c);
            ui_fill_rect(ui, sx[i]-1, sy[i]-1, 3, 3, c);
        } else {
            ui_fill_rect(ui, sx[i], sy[i], 2, 2, c);
        }
    }
}

void ui_background(ui_t *ui)
{
    if (ui->bg) {
        SDL_Rect dst = { 0, 0, ui->screen_w, ui->screen_h };
        SDL_RenderCopy(ui->r, ui->bg, NULL, &dst);
    } else {
        SDL_SetRenderDrawColor(ui->r, COL_BG.r, COL_BG.g, COL_BG.b, 255);
        SDL_RenderClear(ui->r);
    }
    sparkles(ui);
}

void ui_rune_band(ui_t *ui, int y, int h)
{
    const SDL_Color base = { 8, 6, 26, 255 };
    int cy = y + h / 2;
    ui_fill_rect(ui, 0, y, ui->screen_w, h, base);
    for (int x = 20, k = 0; x+16 < ui->screen_w; x += 44, k++) {
        SDL_Color c = (k&1) ? COL_ACCENT : COL_MAGIC;
        c.a = 95;
        ui_fill_rect(ui, x+2, cy-1, 12, 2, c);
        ui_fill_rect(ui, x+7, cy-6, 2, 12, c);
        ui_fill_rect(ui, x+6, cy-2, 4,  4, c);
    }
    ui_fill_rect(ui, 0, y+h, ui->screen_w, 2, COL_LINE);
}

static void border(ui_t *ui, int x, int y, int w, int h, int t, SDL_Color c)
{
    ui_fill_rect(ui, x,       y,       w,  t, c);
    ui_fill_rect(ui, x,       y+h-t,   w,  t, c);
    ui_fill_rect(ui, x,       y+t,     t,  h-2*t, c);
    ui_fill_rect(ui, x+w-t,   y+t,     t,  h-2*t, c);
}

void ui_glow_frame(ui_t *ui, int x, int y, int w, int h)
{
    const SDL_Color g1 = { 196, 132, 255, 36 };
    const SDL_Color g2 = { 196, 132, 255, 90 };
    border(ui, x-12, y-12, w+24, h+24, 6, g1);
    border(ui, x-6,  y-6,  w+12, h+12, 6, g2);
    border(ui, x,    y,    w,    h,    4, COL_ACCENT);
}

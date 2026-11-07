/* main.c - Cine Hub v0.8 (tema Cinema): mreza postera + kategorije + detalji + import JSON
 * + farsi tekst (TTF) + izvoz playliste (.m3u) za VLC + ugradjeni plejer
 * (faza 1: samo video, vidi player.c).
 * v0.9: "Enchanted" theme + IMDb (OMDb) search -> details -> your own sources.
 *
 * Pokrece se kroz websrv (Homebrew Loader), NE kroz elfldr:9021.
 */
#include <ctype.h>
#include <stdarg.h>
#include <SDL2/SDL.h>
#include <curl/curl.h>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>

#include "common.h"
#include "ui.h"
#include "movies.h"
#include "text.h"
#include "player.h"
#include "imdb.h"
#include "stb_image.h"

#ifndef APP_DIR
#define APP_DIR      "/data/homebrew/Cinema"
#endif
#define IMPORT_DIR   APP_DIR "/imports"
#define JSON_LOCAL   APP_DIR "/movies.json"
#define JSON_BACKUP  APP_DIR "/movies.json.bak"
#define JSON_URLFILE APP_DIR "/movies_url.txt"
#define FONT_FILE    APP_DIR "/font.ttf"
#define SOURCES_FILE    APP_DIR "/sources.json"    /* manual stream library     */
#define OMDB_KEYFILE    APP_DIR "/omdb_key.txt"    /* free key: omdbapi.com     */
#define SOURCE_API_FILE APP_DIR "/source_api.txt"  /* resolver URL (optional)   */
#define SEARCH_MAX   40

/* VLC for PS5 cita biblioteku iz svog foldera media (uvijek dostupan),
 * a iz /data/vlc i /data/media samo ako ima "full access". Playliste idu
 * u prvi od njih koji postoji, u podfolder CineHub. */
#ifndef VLC_APP_MEDIA
#define VLC_APP_MEDIA "/data/homebrew/PPSA85300/media"
#endif
#ifndef VLC_DATA_DIR
#define VLC_DATA_DIR  "/data/vlc"
#endif
#ifndef MEDIA_DIR
#define MEDIA_DIR     "/data/media"
#endif
#define EXPORT_SUB    "CineHub"
#define EXPORT_ALT    APP_DIR "/playlists"

#define COLS      6
#define CELL_W    (POSTER_W + 40)
#define CELL_H    (POSTER_H + 70)
#define GRID_X    60
#define GRID_Y    150
#define MAX_DL    (12 * 1024 * 1024)
#define MAX_JSON  (8 * 1024 * 1024)

#define MAX_IMP   64
#define IMP_ROW_H 52
#define IMP_TOP   190

typedef enum { SCR_GRID, SCR_DETAIL, SCR_IMPORT, SCR_PLAYER,
               SCR_SEARCH, SCR_RESULTS, SCR_INFO } screen_t;

typedef struct {
    char path[256];
    long size;
} imp_t;

/* One background network job (search or details + poster). */
typedef struct {
    SDL_Thread    *th;
    SDL_atomic_t   state;       /* 0 idle, 1 running, 2 ok, 3 failed */
    SDL_atomic_t   abort;       /* set by the main thread to cancel */
    int            kind;        /* JOB_SEARCH / JOB_INFO */
    char           key[48];
    char           arg[160];    /* query or imdb id */
    char           err[96];
    im_hit_t       hits[IM_MAX_RESULTS];
    int            n_hits;
    im_info_t      info;
    unsigned char *poster_px;   /* POSTER_W*POSTER_H*4, for the main thread */
    /* auto source resolution */
    char           api_base[256];              /* from source_api.txt (optional) */
    im_source_t    remote_src[IM_MAX_SOURCES]; /* filled by background thread   */
    int            n_remote;
} job_t;

typedef struct {
    ui_t        ui;
    text_font_t *font;          /* NULL ako nema font.ttf */
    movies_t    mv;
    int         have;           /* ima ucitanih filmova */
    int        *vis;            /* indeksi filmova u trenutnoj kategoriji */
    int         n_vis;
    int         cat;
    int         sel;            /* pozicija u vis[] */
    int         top_row;
    screen_t    screen;
    int         rows_fit;
    int         running;
    SDL_Thread *th;             /* nit za postere */
    player_t   *pl;             /* ugradjeni plejer (SCR_PLAYER) */
    screen_t    ret_screen;     /* where the player returns to */
    int         search_focus;   /* grid: search bar focused */

    /* IMDb search flow */
    char         omdb_key[48];
    char         source_api[256]; /* base URL from source_api.txt, empty = use scraper */
    char         q[SEARCH_MAX + 1];
    int          q_len, kb_r, kb_c;
    job_t        job;
    int          job_active;
    int          hit_sel;
    SDL_Texture *info_tex;
    im_source_t  src[IM_MAX_SOURCES];
    int          n_src, src_sel, src_top;

    /* import */
    imp_t       imp[MAX_IMP];
    int         n_imp, imp_sel, imp_top, imp_rows;
    char        msg[160];
    uint32_t    msg_until;

    /* ponavljanje d-pada */
    int         held_btn;
    uint32_t    held_since, held_last;
} app_t;

/* Zajednicka zastavica za zaustavljanje niti za postere i curl prenosa. */
static SDL_atomic_t g_stop;

static void set_msg(app_t *a, const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(a->msg, sizeof a->msg, fmt, ap);
    va_end(ap);
    a->msg_until = SDL_GetTicks() + 6000;
    LOG("%s", a->msg);
}

/* ---------------- HTTP ---------------- */

typedef struct { unsigned char *d; size_t n, cap; int over; } membuf_t;

static size_t on_data(char *p, size_t sz, size_t nm, void *ud)
{
    membuf_t *b = ud;
    size_t    n = sz * nm;

    if (b->n + n > MAX_DL) {
        b->over = 1;
        return 0;                       /* curl prekida prenos */
    }
    if (b->n + n + 1 > b->cap) {
        size_t nc = (b->cap ? b->cap * 2 : 65536);
        while (nc < b->n + n + 1)
            nc *= 2;
        unsigned char *nd = realloc(b->d, nc);
        if (!nd)
            return 0;
        b->d = nd;
        b->cap = nc;
    }
    memcpy(b->d + b->n, p, n);
    b->n += n;
    b->d[b->n] = 0;
    return n;
}

/* Vraca != 0 da prekine prenos (curl) kad se trazi zaustavljanje. */
static int on_progress(void *ud, curl_off_t dt, curl_off_t dn,
                       curl_off_t ut, curl_off_t un)
{
    return SDL_AtomicGet(&g_stop) || (ud && SDL_AtomicGet((SDL_atomic_t *)ud));
}

/* 0 = ok. Pozivalac oslobadja out->d. */
static int http_get_ex(const char *url, membuf_t *out, SDL_atomic_t *abort_flag)
{
    memset(out, 0, sizeof *out);
    CURL *c = curl_easy_init();
    if (!c)
        return -1;
    curl_easy_setopt(c, CURLOPT_URL, url);
    curl_easy_setopt(c, CURLOPT_WRITEFUNCTION, on_data);
    curl_easy_setopt(c, CURLOPT_WRITEDATA, out);
    curl_easy_setopt(c, CURLOPT_CONNECTTIMEOUT, 8L);
    curl_easy_setopt(c, CURLOPT_TIMEOUT, 40L);
    curl_easy_setopt(c, CURLOPT_NOSIGNAL, 1L);
    curl_easy_setopt(c, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(c, CURLOPT_NOPROGRESS, 0L);
    curl_easy_setopt(c, CURLOPT_XFERINFOFUNCTION, on_progress);
    curl_easy_setopt(c, CURLOPT_XFERINFODATA, abort_flag);
    /* TLS verification stays enabled. Install a CA bundle at
     * /data/homebrew/CineHub/cacert.pem if the target has no system bundle. */
    curl_easy_setopt(c, CURLOPT_SSL_VERIFYPEER, 1L);
    curl_easy_setopt(c, CURLOPT_SSL_VERIFYHOST, 2L);
    if (access(APP_DIR "/cacert.pem", R_OK) == 0)
        curl_easy_setopt(c, CURLOPT_CAINFO, APP_DIR "/cacert.pem");
    CURLcode rc = curl_easy_perform(c);
    long     http = 0;
    curl_easy_getinfo(c, CURLINFO_RESPONSE_CODE, &http);
    curl_easy_cleanup(c);

    if (rc != CURLE_OK || http >= 400 || out->n == 0) {
        free(out->d);
        memset(out, 0, sizeof *out);
        return -1;
    }
    return 0;
}

static int http_get(const char *url, membuf_t *out)
{
    return http_get_ex(url, out, NULL);
}

static char *read_file(const char *path, size_t *len)
{
    FILE *f = fopen(path, "rb");
    if (!f)
        return NULL;
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (n <= 0 || n > MAX_JSON) {
        fclose(f);
        return NULL;
    }
    char *b = malloc((size_t)n + 1);
    if (b && fread(b, 1, (size_t)n, f) != (size_t)n) {
        free(b);
        b = NULL;
    }
    fclose(f);
    if (b) {
        b[n] = 0;
        *len = (size_t)n;
    }
    return b;
}

/* ---------------- poster ---------------- */

/* Smanjuje RGBA na POSTER_W x POSTER_H (prosjek 2x2 uzorka po pikselu). */
static unsigned char *scale_poster(const unsigned char *s, int sw, int sh)
{
    unsigned char *o = malloc((size_t)POSTER_W * POSTER_H * 4);
    if (!o)
        return NULL;

    for (int y = 0; y < POSTER_H; y++) {
        for (int x = 0; x < POSTER_W; x++) {
            unsigned acc[4] = { 0, 0, 0, 0 };
            for (int k = 0; k < 4; k++) {
                int fx = (int)(((long long)(x * 2 + (k & 1)) * 2 + 1) * sw /
                               (POSTER_W * 4));
                int fy = (int)(((long long)(y * 2 + (k >> 1)) * 2 + 1) * sh /
                               (POSTER_H * 4));
                if (fx >= sw) fx = sw - 1;
                if (fy >= sh) fy = sh - 1;
                const unsigned char *p = s + ((size_t)fy * sw + fx) * 4;
                for (int ch = 0; ch < 4; ch++)
                    acc[ch] += p[ch];
            }
            unsigned char *d = o + ((size_t)y * POSTER_W + x) * 4;
            for (int ch = 0; ch < 4; ch++)
                d[ch] = (unsigned char)(acc[ch] / 4);
        }
    }
    return o;
}

static int poster_thread(void *ud)
{
    movies_t *mv = ud;

    for (int i = 0; i < mv->n && !SDL_AtomicGet(&g_stop); i++) {
        movie_t *m = &mv->m[i];
        membuf_t b;

        if (!m->poster_url[0] || http_get(m->poster_url, &b) != 0) {
            SDL_AtomicSet(&m->pstate, 3);
            continue;
        }
        int w = 0, h = 0, comp = 0;
        unsigned char *img = stbi_load_from_memory(b.d, (int)b.n, &w, &h,
                                                   &comp, 4);
        free(b.d);
        if (!img || w < 2 || h < 2) {
            if (img)
                stbi_image_free(img);
            ERR("poster %d: dekodiranje nije uspjelo", i);
            SDL_AtomicSet(&m->pstate, 3);
            continue;
        }
        m->px = scale_poster(img, w, h);
        stbi_image_free(img);
        SDL_AtomicSet(&m->pstate, m->px ? 1 : 3);
    }
    return 0;
}

/* Glavna nit: pretvara spremne piksele u teksture (najvise 2 po kadru). */
static void upload_posters(app_t *a, SDL_Renderer *r)
{
    int budget = 2;

    for (int i = 0; i < a->mv.n && budget > 0; i++) {
        movie_t *m = &a->mv.m[i];
        if (SDL_AtomicGet(&m->pstate) != 1)
            continue;
        m->tex = SDL_CreateTexture(r, SDL_PIXELFORMAT_RGBA32,
                                   SDL_TEXTUREACCESS_STATIC,
                                   POSTER_W, POSTER_H);
        if (m->tex)
            SDL_UpdateTexture(m->tex, NULL, m->px, POSTER_W * 4);
        free(m->px);
        m->px = NULL;
        SDL_AtomicSet(&m->pstate, m->tex ? 2 : 3);
        budget--;
    }
}

static void stop_posters(app_t *a)
{
    SDL_AtomicSet(&g_stop, 1);
    if (a->th)
        SDL_WaitThread(a->th, NULL);
    a->th = NULL;
    SDL_AtomicSet(&g_stop, 0);
}

/* ---------------- filter ---------------- */

static void rebuild_vis(app_t *a)
{
    a->n_vis = 0;
    for (int i = 0; i < a->mv.n; i++)
        if (a->cat == 0 ||
            strcmp(a->mv.m[i].cat_label, a->mv.cats[a->cat]) == 0)
            a->vis[a->n_vis++] = i;
    a->sel = 0;
    a->top_row = 0;
}

static void scroll_to_sel(app_t *a)
{
    int row = a->sel / COLS;
    if (row < a->top_row)
        a->top_row = row;
    if (row >= a->top_row + a->rows_fit)
        a->top_row = row - a->rows_fit + 1;
}

/* Preuzima nm (vlasnistvo prelazi na app), pokrece postere. */
static void install_movies(app_t *a, movies_t *nm)
{
    stop_posters(a);
    movies_free(&a->mv);
    free(a->vis);
    a->mv  = *nm;
    a->vis = malloc(sizeof(int) * (size_t)a->mv.n);
    a->cat = 0;
    a->have = a->vis != NULL;
    if (a->have) {
        rebuild_vis(a);
        a->th = SDL_CreateThread(poster_thread, "posters", &a->mv);
    }
}

/* ---------------- import ---------------- */

static int has_json_ext(const char *name)
{
    size_t n = strlen(name);
    return n > 5 && strcasecmp(name + n - 5, ".json") == 0;
}

static void scan_dir(app_t *a, const char *dir)
{
    DIR *dp = opendir(dir);
    if (!dp)
        return;

    struct dirent *de;
    while ((de = readdir(dp)) != NULL && a->n_imp < MAX_IMP) {
        if (de->d_name[0] == '.' || !has_json_ext(de->d_name))
            continue;

        imp_t *it = &a->imp[a->n_imp];
        struct stat st;
        snprintf(it->path, sizeof it->path, "%s/%s", dir, de->d_name);
        if (stat(it->path, &st) != 0 || !S_ISREG(st.st_mode) || st.st_size <= 0)
            continue;
        it->size = (long)st.st_size;
        a->n_imp++;
    }
    closedir(dp);
}

static void scan_imports(app_t *a)
{
    a->n_imp = 0;
    a->imp_sel = 0;
    a->imp_top = 0;
    scan_dir(a, IMPORT_DIR);
    for (int i = 0; i < 8; i++) {
        char d[24];
        snprintf(d, sizeof d, "/mnt/usb%d", i);
        scan_dir(a, d);
    }
}

static void do_import(app_t *a, const char *path)
{
    size_t len = 0;
    char  *js  = read_file(path, &len);

    if (!js) {
        set_msg(a, "Cannot read file (empty or over 8 MB)");
        return;
    }

    movies_t nm;
    int      n = movies_parse(&nm, js, len);
    if (n <= 0) {
        free(js);
        set_msg(a, "Invalid JSON or no movies with a title");
        return;
    }

    /* Stari movies.json se cuva kao .bak, pa se greska moze vratiti. */
    rename(JSON_LOCAL, JSON_BACKUP);
    FILE *f = fopen(JSON_LOCAL, "wb");
    int   saved = f && fwrite(js, 1, len, f) == len;
    if (f)
        fclose(f);
    free(js);

    install_movies(a, &nm);
    a->screen = SCR_GRID;
    if (saved)
        set_msg(a, "Imported %d movies (old list kept as movies.json.bak)", n);
    else
        set_msg(a, "Imported %d movies but could not save to %s", n, JSON_LOCAL);
}

static void open_import(app_t *a)
{
    scan_imports(a);
    a->screen = SCR_IMPORT;
}

/* ---------------- izvoz playliste za VLC ---------------- */

/* Pravi ime fajla samo od ASCII slova/cifara (farsi naslovi -> movie_N). */
static void safe_name(char *dst, size_t cap, const char *title, int idx)
{
    size_t o = 0;

    for (const unsigned char *p = (const unsigned char *)title;
         *p && o + 1 < cap; p++) {
        if (isalnum(*p)) {
            dst[o++] = (char)*p;
        } else if ((*p == ' ' || *p == '-' || *p == '_' || *p == '.') &&
                   o > 0 && dst[o - 1] != '_') {
            dst[o++] = '_';
        }
    }
    while (o > 0 && dst[o - 1] == '_')
        o--;
    dst[o] = '\0';
    if (o == 0)
        snprintf(dst, cap, "movie_%d", idx);
}

static int is_dir(const char *p)
{
    struct stat st;
    return stat(p, &st) == 0 && S_ISDIR(st.st_mode);
}

/* Trazeni folder: prvi postojeci VLC-ov folder + /CineHub, a ako nijedan ne
 * postoji, APP_DIR/playlists (pa se fajlovi kopiraju rucno, FTP-om). */
static const char *export_dir(char *buf, size_t cap)
{
    /* VLC_APP_MEDIA i /data/vlc se ne prave: nastaju kad se VLC pokrene. */
    static const char *const roots[] = { VLC_APP_MEDIA, VLC_DATA_DIR, NULL };

    for (int i = 0; roots[i]; i++) {
        if (!is_dir(roots[i]))
            continue;
        snprintf(buf, cap, "%s/%s", roots[i], EXPORT_SUB);
        mkdir(buf, 0777);
        if (is_dir(buf))
            return buf;
    }
    mkdir(MEDIA_DIR, 0777);
    snprintf(buf, cap, "%s/%s", MEDIA_DIR, EXPORT_SUB);
    mkdir(buf, 0777);
    if (is_dir(buf))
        return buf;

    mkdir(EXPORT_ALT, 0777);
    snprintf(buf, cap, "%s", EXPORT_ALT);
    return is_dir(buf) ? buf : NULL;
}

/* Pise .m3u sa filmovima list[0..n). Vraca broj upisanih ili -1. */
static int write_m3u(const char *path, app_t *a, const int *list, int n)
{
    FILE *f = fopen(path, "wb");
    int   w = 0;

    if (!f)
        return -1;
    fputs("#EXTM3U\n", f);
    for (int i = 0; i < n; i++) {
        const movie_t *m = &a->mv.m[list[i]];
        if (!m->video_url[0])
            continue;
        char t[M_TITLE];
        snprintf(t, sizeof t, "%s", m->title);
        for (char *c = t; *c; c++)
            if (*c == '\n' || *c == '\r')
                *c = ' ';
        fprintf(f, "#EXTINF:-1,%s\n%s\n", t, m->video_url);
        w++;
    }
    return fclose(f) == 0 ? w : -1;
}

static void export_one(app_t *a)
{
    if (!a->n_vis)
        return;
    int   idx = a->vis[a->sel];
    char  dir[200], name[96], path[320];

    if (!a->mv.m[idx].video_url[0]) {
        set_msg(a, "This movie has no video_url");
        return;
    }
    if (!export_dir(dir, sizeof dir)) {
        set_msg(a, "Cannot create a playlist folder");
        return;
    }
    safe_name(name, sizeof name, a->mv.m[idx].title, idx + 1);
    snprintf(path, sizeof path, "%s/%s.m3u", dir, name);
    if (write_m3u(path, a, &idx, 1) == 1)
        set_msg(a, "Saved %s - now open VLC > Playlists", path);
    else
        set_msg(a, "Could not write %s", path);
}

static void export_all(app_t *a)
{
    char dir[200], name[96], path[320];

    if (!a->n_vis)
        return;
    if (!export_dir(dir, sizeof dir)) {
        set_msg(a, "Cannot create a playlist folder");
        return;
    }
    if (a->cat == 0)
        snprintf(name, sizeof name, "_All");
    else
        safe_name(name, sizeof name, a->mv.cats[a->cat], a->cat);
    snprintf(path, sizeof path, "%s/%s.m3u", dir, name);
    int w = write_m3u(path, a, a->vis, a->n_vis);
    if (w >= 0)
        set_msg(a, "Saved %d movies to %s", w, path);
    else
        set_msg(a, "Could not write %s", path);
}

/* ---------------- plejer ---------------- */

static void play_url(app_t *a, const char *url, const char *title,
                     screen_t back)
{
    a->pl = player_open(a->ui.r, url, title, a->ui.screen_w, a->ui.screen_h);
    if (!a->pl) {
        set_msg(a, "Cannot start the player");
        return;
    }
    a->held_btn   = -1;
    a->ret_screen = back;
    a->screen     = SCR_PLAYER;
}

static void start_player(app_t *a)
{
    if (!a->n_vis)
        return;
    const movie_t *m = &a->mv.m[a->vis[a->sel]];

    if (!m->video_url[0]) {
        set_msg(a, "This movie has no video_url");
        return;
    }
    play_url(a, m->video_url, m->title, SCR_DETAIL);
}

static void stop_player(app_t *a)
{
    player_close(a->pl);
    a->pl = NULL;
    a->screen = a->ret_screen;
}

/* ---------------- crtanje ---------------- */

/* ---------------- drawing (theme "Enchanted") ---------------- */

static void frame(ui_t *ui, int x, int y, int w, int h, int t, SDL_Color c)
{
    ui_fill_rect(ui, x, y, w, t, c);
    ui_fill_rect(ui, x, y + h - t, w, t, c);
    ui_fill_rect(ui, x, y, t, h, c);
    ui_fill_rect(ui, x + w - t, y, t, h, c);
}

static void clear(ui_t *ui)
{
    ui_background(ui);
}

/* Tekst teme: TTF ako ima fonta (ljepsi), inace 8x8 bitmap. Vraca sirinu. */
static int label(app_t *a, int x, int y, int px, SDL_Color c, const char *fmt, ...)
{
    char    buf[512];
    va_list ap;

    va_start(ap, fmt);
    vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);
    if (a->font)
        return font_draw(a->font, x, y, 0, px, c, buf, -1);
    int scale = px >= 56 ? 5 : px >= 44 ? 4 : px >= 32 ? 3 : 2;
    ui_text(&a->ui, x, y, scale, c, "%s", buf);
    return ui_text_width(scale, buf);
}

static void draw_msg(app_t *a, int y)
{
    if (a->msg[0] && SDL_GetTicks() < a->msg_until)
        label(a, 60, y, 24, COL_ACCENT, "%s", a->msg);
}

/* Naziv kategorije za prikaz: izvorni (farsi) ako ima fonta. */
static const char *cat_display(app_t *a, int cat)
{
    if (cat == 0 || !a->font)
        return a->mv.cats[cat];
    for (int i = 0; i < a->mv.n; i++)
        if (strcmp(a->mv.m[i].cat_label, a->mv.cats[cat]) == 0 &&
            a->mv.m[i].category[0])
            return a->mv.m[i].category;
    return a->mv.cats[cat];
}

/* Skracuje UTF-8 string tako da stane u maxw piksela (rez na granici znaka). */
static void fit_text(app_t *a, int px, char *dst, size_t cap, const char *src,
                     int maxw)
{
    snprintf(dst, cap, "%s", src);
    if (font_width(a->font, px, dst, -1) <= maxw)
        return;
    size_t n = strlen(dst);
    while (n > 0) {
        n--;
        while (n > 0 && ((unsigned char)dst[n] & 0xC0) == 0x80)
            n--;                          /* ne rezi usred UTF-8 znaka */
        dst[n] = '\0';
        char t[256];
        snprintf(t, sizeof t, "%s...", dst);
        if (font_width(a->font, px, t, -1) <= maxw) {
            snprintf(dst, cap, "%s", t);
            return;
        }
    }
}

/* Header: rune band + "CINE HUB" logo (aurora cyan and white). */
static void draw_header(app_t *a, const char *sub)
{
    ui_rune_band(&a->ui, 0, 24);
    int w = label(a, 60, 36, 64, COL_ACCENT, "CINE");
    int x = 60 + w + 18;
    w = label(a, x, 36, 64, COL_TEXT, "HUB");
    if (sub)
        label(a, x + w + 36, 58, 34, COL_DIM, "%s", sub);
}

static void draw_footer(app_t *a, const char *hint)
{
    ui_t *ui = &a->ui;

    ui_fill_rect(ui, 60, ui->screen_h - 62, ui->screen_w - 120, 2, COL_LINE);
    label(a, 60, ui->screen_h - 48, 24, COL_DIM, "%s", hint);
}

static void draw_searchbar(app_t *a);

static void draw_grid(app_t *a)
{
    ui_t *ui = &a->ui;

    clear(ui);
    draw_header(a, NULL);
    draw_searchbar(a);
    {
        const char *cn = cat_display(a, a->cat);
        int x = 60;
        x += label(a, x, 114, 22, COL_DIM, "L1/R1") + 16;
        if (a->font && text_has_nonascii(cn))
            x += font_draw(a->font, x, 108, 0, 30, COL_ACCENT, cn, -1);
        else
            x += label(a, x, 108, 30, COL_ACCENT, "%s", cn);
        label(a, x + 28, 114, 22, COL_DIM, "(%d/%d)",
              a->n_vis ? a->sel + 1 : 0, a->n_vis);
    }

    int first = a->top_row * COLS;
    int last  = MIN(a->n_vis, (a->top_row + a->rows_fit) * COLS);

    for (int p = first; p < last; p++) {
        movie_t *m = &a->mv.m[a->vis[p]];
        int col = p % COLS;
        int row = p / COLS - a->top_row;
        int x = GRID_X + col * (CELL_W + 8);
        int y = GRID_Y + row * CELL_H;
        int sel = (p == a->sel);

        if (sel)
            ui_fill_rect(ui, x - 14, y - 14, POSTER_W + 28, CELL_H - 20,
                         COL_SEL);

        if (m->tex) {
            SDL_Rect dst = { x, y, POSTER_W, POSTER_H };
            SDL_RenderCopy(ui->r, m->tex, NULL, &dst);
        } else {
            ui_fill_rect(ui, x, y, POSTER_W, POSTER_H, COL_PANEL);
            label(a, x + 14, y + POSTER_H / 2 - 12, 22, COL_DIM, "%s",
                  SDL_AtomicGet(&m->pstate) == 3 ? "no poster" : "loading...");
        }
        if (sel)
            ui_glow_frame(ui, x - 4, y - 4, POSTER_W + 8, POSTER_H + 8);

        if (a->font) {
            char t[160];
            fit_text(a, 24, t, sizeof t, m->title, POSTER_W);
            font_draw(a->font, x, y + POSTER_H + 10, POSTER_W, 24,
                      sel ? COL_TEXT : COL_DIM, t, -1);
        } else {
            char t[40];
            ui_ellipsize(t, sizeof t, m->title, POSTER_W / 16);
            ui_text(ui, x, y + POSTER_H + 12, 2, sel ? COL_TEXT : COL_DIM,
                    "%s", t);
        }
    }

    draw_msg(a, ui->screen_h - 96);
    draw_footer(a, "D-pad move     X open     Square save playlist     "
                   "Triangle import     Options search     O exit");
}

static void draw_detail(app_t *a)
{
    ui_t *ui = &a->ui;
    movie_t *m = &a->mv.m[a->vis[a->sel]];

    clear(ui);
    draw_header(a, NULL);

    SDL_Rect dst = { 100, 150, POSTER_W * 2, POSTER_H * 2 };
    if (m->tex)
        SDL_RenderCopy(ui->r, m->tex, NULL, &dst);
    else
        ui_fill_rect(ui, dst.x, dst.y, dst.w, dst.h, COL_PANEL);
    ui_glow_frame(ui, dst.x - 4, dst.y - 4, dst.w + 8, dst.h + 8);

    int x  = 100 + POSTER_W * 2 + 80;
    int bw = ui->screen_w - x - 60;
    char t[160];

    if (a->font) {
        fit_text(a, 54, t, sizeof t, m->title, bw);
        font_draw(a->font, x, 150, bw, 54, COL_TEXT, t, -1);
    } else {
        ui_ellipsize(t, sizeof t, m->title, bw / 32);
        ui_text(ui, x, 160, 4, COL_TEXT, "%s", t);
    }
    ui_fill_rect(ui, x, 222, 120, 4, COL_ACCENT);       /* accent rule */

    if (a->font && m->category[0] && text_has_nonascii(m->category))
        font_draw(a->font, x, 238, bw, 32, COL_ACCENT, m->category, -1);
    else
        label(a, x, 238, 30, COL_ACCENT, "%s", m->cat_label);

    if (m->desc[0] && a->font) {
        font_draw_wrapped(a->font, x, 300, bw, 30, 44, 10, COL_DIM, m->desc);
    } else if (m->desc[0] && !text_has_nonascii(m->desc)) {
        char line[96];
        int  y = 300, n = (int)strlen(m->desc), pos = 0;
        while (pos < n && y < 700) {
            snprintf(line, sizeof line, "%.*s", 60, m->desc + pos);
            ui_text(ui, x, y, 2, COL_DIM, "%s", line);
            pos += 60;
            y += 28;
        }
    } else if (m->desc[0]) {
        label(a, x, 300, 24, COL_DIM, "(description: put font.ttf in %s)",
              APP_DIR);
    }

    char u[96];
    ui_ellipsize(u, sizeof u, m->video_url[0] ? m->video_url : "-", 70);
    label(a, x, 790, 22, COL_DIM, "Video: %s", u);

    ui_fill_rect(ui, x, 836, 270, 66, COL_ACCENT);       /* gumb "PLAY" */
    label(a, x + 34, 850, 34, COL_BG, "X   PLAY");
    label(a, x + 306, 858, 24, COL_DIM, "Square: save for VLC");

    draw_msg(a, ui->screen_h - 96);
    draw_footer(a, "X play     Square save for VLC     O back");
}

static void draw_import(app_t *a)
{
    ui_t *ui = &a->ui;

    clear(ui);
    draw_header(a, "Import JSON");
    label(a, 60, 108, 22, COL_DIM, "Looking in: %s  and  /mnt/usb0..7",
          IMPORT_DIR);
    label(a, 60, 140, 22, COL_DIM,
          "Replaces your current movie list (the old one is kept as .bak)");

    if (a->n_imp == 0) {
        label(a, 60, IMP_TOP + 20, 36, COL_TEXT, "No .json files found");
        label(a, 60, IMP_TOP + 80, 24, COL_DIM,
              "Copy a .json to the USB root or to %s", IMPORT_DIR);
    }

    int last = MIN(a->n_imp, a->imp_top + a->imp_rows);
    for (int i = a->imp_top; i < last; i++) {
        int y = IMP_TOP + (i - a->imp_top) * IMP_ROW_H;
        int sel = (i == a->imp_sel);
        if (sel) {
            ui_fill_rect(ui, 50, y - 8, ui->screen_w - 100, IMP_ROW_H - 4,
                         COL_SEL);
            ui_fill_rect(ui, 50, y - 8, 6, IMP_ROW_H - 4, COL_ACCENT);
        }
        char p[100];
        ui_ellipsize(p, sizeof p, a->imp[i].path, 90);
        label(a, 72, y, 24, sel ? COL_TEXT : COL_DIM, "%s", p);
        label(a, ui->screen_w - 260, y, 22, COL_DIM, "%ld KB",
              (a->imp[i].size + 1023) / 1024);
    }

    draw_msg(a, ui->screen_h - 96);
    draw_footer(a, "Up/Down choose     X import     O back");
}

static void draw_empty(app_t *a)
{
    ui_t *ui = &a->ui;

    clear(ui);
    draw_header(a, NULL);
    label(a, 60, 150, 40, COL_TEXT, "No movie list yet");
    label(a, 60, 215, 24, COL_DIM, "Put movies.json in: %s", APP_DIR);
    label(a, 60, 250, 24, COL_DIM, "or press Triangle to import a .json");
    label(a, 60, 285, 24, COL_DIM, "or press Options to search IMDb");
    draw_msg(a, ui->screen_h - 96);
    draw_footer(a, "Triangle import JSON     Options search     O exit");
}

/* ---------------- IMDb search flow ---------------- */

/* Screens: SCR_SEARCH (on-screen keyboard) -> SCR_RESULTS (OMDb hits) ->
 * SCR_INFO (details + the sources YOU listed in sources.json for that
 * IMDb id) -> player. Network work runs in one worker thread (job_t). */

enum { JOB_SEARCH = 1, JOB_INFO = 2 };

static const char *const KB_ROWS[4] = { "1234567890", "QWERTYUIOP",
                                        "ASDFGHJKL-", "ZXCVBNM,.'" };
static const char *const KB_ACT[4]  = { "SPACE", "DELETE", "CLEAR", "SEARCH" };
#define KB_ACT_ROW 4

static int kb_cols(int r)
{
    return r == KB_ACT_ROW ? 4 : 10;
}

static int job_fetch(void *ud, const char *url, char **body, size_t *len)
{
    membuf_t b;

    if (http_get_ex(url, &b, (SDL_atomic_t *)ud) != 0)
        return -1;
    *body = (char *)b.d;
    *len  = b.n;
    return 0;
}

static int job_thread(void *ud)
{
    job_t *j  = ud;
    int    ok = 0;

    j->err[0] = '\0';
    if (j->kind == JOB_SEARCH) {
        int n = im_search(job_fetch, &j->abort, j->key, j->arg, j->hits,
                          IM_MAX_RESULTS, j->err, sizeof j->err);
        j->n_hits = n > 0 ? n : 0;
        ok = n > 0;
        if (n == 0 && !j->err[0])
            snprintf(j->err, sizeof j->err, "No results");
    } else if (j->kind == JOB_INFO) {
        ok = im_details(job_fetch, &j->abort, j->key, j->arg, &j->info,
                        j->err, sizeof j->err) == 0;
        if (ok && j->info.poster[0]) {
            membuf_t b;
            if (http_get_ex(j->info.poster, &b, &j->abort) == 0) {
                int w = 0, h = 0, comp = 0;
                unsigned char *img = stbi_load_from_memory(b.d, (int)b.n, &w,
                                                           &h, &comp, 4);
                free(b.d);
                if (img && w >= 2 && h >= 2)
                    j->poster_px = scale_poster(img, w, h);
                if (img)
                    stbi_image_free(img);
            }
        }
    }
    /* ---- auto source resolution (JOB_INFO only) ---- */
    if (j->kind == JOB_INFO && ok && !SDL_AtomicGet(&j->abort)) {
        j->n_remote = 0;

        if (j->api_base[0]) {
            /* Option A: resolver server (Cloudflare Worker / Python) */
            int nr = im_remote_sources(job_fetch, &j->abort,
                                       j->api_base, j->info.id,
                                       j->remote_src, IM_MAX_SOURCES);
            j->n_remote = nr > 0 ? nr : 0;
        }

        if (j->n_remote == 0 && !SDL_AtomicGet(&j->abort)) {
            /* Option B: direct scraper — no server needed */
            int ns = im_scrape_sources(job_fetch, &j->abort,
                                       j->info.id,
                                       j->remote_src, IM_MAX_SOURCES);
            j->n_remote = ns > 0 ? ns : 0;
        }
    }
    /* ------------------------------------------------- */

    SDL_AtomicSet(&j->state, ok ? 2 : 3);
    return 0;
}

static void job_start(app_t *a, int kind, const char *arg)
{
    job_t *j = &a->job;

    if (a->job_active)
        return;
    if (!a->omdb_key[0]) {
        set_msg(a, "Missing OMDb key: put it in %s", OMDB_KEYFILE);
        return;
    }
    free(j->poster_px);
    j->poster_px = NULL;
    j->kind = kind;
    snprintf(j->key,      sizeof j->key,      "%s", a->omdb_key);
    snprintf(j->arg,      sizeof j->arg,      "%s", arg);
    snprintf(j->api_base, sizeof j->api_base, "%s", a->source_api);
    j->n_remote = 0;
    SDL_AtomicSet(&j->abort, 0);
    SDL_AtomicSet(&j->state, 1);
    j->th = SDL_CreateThread(job_thread, "imdb", j);
    if (!j->th) {
        SDL_AtomicSet(&j->state, 0);
        set_msg(a, "Cannot start the network thread");
        return;
    }
    a->job_active = 1;
}

static void job_shutdown(app_t *a)
{
    if (a->job_active) {
        SDL_AtomicSet(&a->job.abort, 1);
        SDL_WaitThread(a->job.th, NULL);
        a->job_active = 0;
    }
    free(a->job.poster_px);
    a->job.poster_px = NULL;
    if (a->info_tex)
        SDL_DestroyTexture(a->info_tex);
    a->info_tex = NULL;
}

static void install_info_tex(app_t *a)
{
    job_t *j = &a->job;

    if (a->info_tex) {
        SDL_DestroyTexture(a->info_tex);
        a->info_tex = NULL;
    }
    if (!j->poster_px)
        return;
    a->info_tex = SDL_CreateTexture(a->ui.r, SDL_PIXELFORMAT_RGBA32,
                                    SDL_TEXTUREACCESS_STATIC,
                                    POSTER_W, POSTER_H);
    if (a->info_tex)
        SDL_UpdateTexture(a->info_tex, NULL, j->poster_px, POSTER_W * 4);
    free(j->poster_px);
    j->poster_px = NULL;
}

static void load_source_api(app_t *a)
{
    size_t len = 0;
    char  *raw = read_file(SOURCE_API_FILE, &len);
    a->source_api[0] = '\0';
    if (!raw) return;
    while (len > 0 && (raw[len-1]=='\n' || raw[len-1]=='\r' || raw[len-1]==' '))
        raw[--len] = '\0';
    snprintf(a->source_api, sizeof a->source_api, "%s", raw);
    free(raw);
    LOG(a->source_api[0]
        ? "source resolver configured: %s"
        : "no source_api.txt — using local authorized sources only",
        a->source_api);
}

static void load_sources(app_t *a)
{
    a->n_src = a->src_sel = a->src_top = 0;

    /* 1. Local sources.json — highest priority (manual overrides) */
    size_t len = 0;
    char  *js  = read_file(SOURCES_FILE, &len);
    if (js) {
        int n = im_sources_for(js, len, a->job.info.id, a->src, IM_MAX_SOURCES);
        if (n > 0) a->n_src = n;
        free(js);
    }

    /* 2. Merge auto-resolved sources fetched by the background thread
     *    (from Worker server OR direct scraper, whichever ran)       */
    int room = IM_MAX_SOURCES - a->n_src;
    if (room > 0 && a->job.n_remote > 0) {
        int copy = a->job.n_remote < room ? a->job.n_remote : room;
        memcpy(a->src + a->n_src, a->job.remote_src,
               (size_t)copy * sizeof(im_source_t));
        a->n_src += copy;
    }
}

/* Main thread, every frame: collects a finished job. */
static void job_poll(app_t *a)
{
    job_t *j = &a->job;
    int    st;

    if (!a->job_active || (st = SDL_AtomicGet(&j->state)) < 2)
        return;
    SDL_WaitThread(j->th, NULL);
    j->th = NULL;
    a->job_active = 0;
    SDL_AtomicSet(&j->state, 0);

    if (SDL_AtomicGet(&j->abort) || st == 3) {
        if (!SDL_AtomicGet(&j->abort))
            set_msg(a, "%s", j->err[0] ? j->err : "Request failed");
        free(j->poster_px);
        j->poster_px = NULL;
        return;
    }
    if (j->kind == JOB_SEARCH) {
        a->hit_sel = 0;
        a->screen  = SCR_RESULTS;
    } else {
        install_info_tex(a);
        load_sources(a);
        a->screen = SCR_INFO;
    }
}

static void load_omdb_key(app_t *a)
{
    size_t len = 0;
    char  *k   = read_file(OMDB_KEYFILE, &len);
    size_t n   = 0;

    a->omdb_key[0] = '\0';
    if (!k)
        return;
    const char *p = k;
    while (*p && isspace((unsigned char)*p))
        p++;
    for (; *p && isalnum((unsigned char)*p) && n + 1 < sizeof a->omdb_key; p++)
        a->omdb_key[n++] = *p;
    a->omdb_key[n] = '\0';
    free(k);
}

static void open_search(app_t *a)
{
    a->screen       = SCR_SEARCH;
    a->search_focus = 0;
    a->held_btn     = -1;
}

/* ---- on-screen keyboard ---- */

static void kb_add(app_t *a, char ch)
{
    if (a->q_len >= SEARCH_MAX)
        return;
    if (ch == ' ' && (a->q_len == 0 || a->q[a->q_len - 1] == ' '))
        return;
    a->q[a->q_len++] = ch;
    a->q[a->q_len]   = '\0';
}

static void kb_del(app_t *a)
{
    if (a->q_len > 0)
        a->q[--a->q_len] = '\0';
}

static void kb_submit(app_t *a)
{
    while (a->q_len > 0 && a->q[a->q_len - 1] == ' ')
        a->q[--a->q_len] = '\0';
    if (a->q_len == 0) {
        set_msg(a, "Type a movie title first");
        return;
    }
    job_start(a, JOB_SEARCH, a->q);
}

static void kb_press(app_t *a)
{
    if (a->kb_r == KB_ACT_ROW) {
        switch (a->kb_c) {
        case 0: kb_add(a, ' ');   break;
        case 1: kb_del(a);        break;
        case 2: a->q_len = 0; a->q[0] = '\0'; break;
        default: kb_submit(a);    break;
        }
        return;
    }
    kb_add(a, KB_ROWS[a->kb_r][a->kb_c]);
}

static void kb_move(app_t *a, int dr, int dc)
{
    if (dc) {
        int n = kb_cols(a->kb_r);
        a->kb_c = (a->kb_c + dc + n) % n;
        return;
    }
    int nr = a->kb_r + dr;
    if (nr < 0 || nr > KB_ACT_ROW)
        return;
    if (nr == KB_ACT_ROW)
        a->kb_c = a->kb_c * 4 / 10;
    else if (a->kb_r == KB_ACT_ROW)
        a->kb_c = a->kb_c * 10 / 4 + 1;
    a->kb_r = nr;
}

/* ---- drawing ---- */

/* Width of a label as label() would draw it, for centering. */
static int label_w(app_t *a, int px, const char *s)
{
    if (a->font)
        return font_width(a->font, px, s, -1);
    int scale = px >= 56 ? 5 : px >= 44 ? 4 : px >= 32 ? 3 : 2;
    return ui_text_width(scale, s);
}

static void fit_any(app_t *a, int px, char *dst, size_t cap, const char *src,
                    int maxw)
{
    if (a->font) {
        fit_text(a, px, dst, cap, src, maxw);
        return;
    }
    int scale = px >= 56 ? 5 : px >= 44 ? 4 : px >= 32 ? 3 : 2;
    ui_ellipsize(dst, cap, src, maxw / (8 * scale));
}

/* Wrapped paragraph: TTF word wrap, or a simple word wrap with the bitmap font. */
static void wrap_text(app_t *a, int x, int y, int bw, int px, int line_h,
                      int max_lines, SDL_Color c, const char *s)
{
    if (a->font) {
        font_draw_wrapped(a->font, x, y, bw, px, line_h, max_lines, c, s);
        return;
    }
    int  per = MAX(8, bw / 16);                  /* scale 2 -> 16 px per glyph */
    int  n = (int)strlen(s), pos = 0;
    char line[128];

    for (int row = 0; row < max_lines && pos < n; row++) {
        int len = MIN(per, n - pos);
        if (pos + len < n) {
            int cut = len;
            while (cut > 0 && s[pos + cut] != ' ')
                cut--;
            if (cut > 0)
                len = cut;
        }
        snprintf(line, sizeof line, "%.*s", MIN(len, (int)sizeof line - 1),
                 s + pos);
        ui_text(&a->ui, x, y + row * line_h, 2, c, "%s", line);
        pos += len;
        while (pos < n && s[pos] == ' ')
            pos++;
    }
}

static void label_center(app_t *a, int cx, int y, int px, SDL_Color c,
                         const char *s)
{
    label(a, cx - label_w(a, px, s) / 2, y, px, c, "%s", s);
}

static void draw_busy(app_t *a)
{
    ui_t *ui = &a->ui;
    int   w = 560, h = 130;
    int   x = (ui->screen_w - w) / 2, y = (ui->screen_h - h) / 2;
    int   dots = (int)((SDL_GetTicks() / 350) % 4);
    const SDL_Color veil = { 0, 0, 0, 150 };

    ui_fill_rect(ui, 0, 0, ui->screen_w, ui->screen_h, veil);
    ui_fill_rect(ui, x, y, w, h, COL_PANEL);
    ui_glow_frame(ui, x, y, w, h);
    label(a, x + 40, y + 26, 36, COL_TEXT, "%s%.*s",
          a->job.kind == JOB_SEARCH ? "Searching" : "Loading", dots, "...");
    label(a, x + 40, y + 88, 22, COL_DIM, "O  cancel");
}

static void draw_searchbar(app_t *a)
{
    ui_t *ui = &a->ui;
    int   w = 620, h = 56, x = ui->screen_w - w - 60, y = 36;

    ui_fill_rect(ui, x, y, w, h, COL_PANEL);
    frame(ui, x, y, w, h, a->search_focus ? 4 : 2,
          a->search_focus ? COL_ACCENT : COL_LINE);
    label(a, x + 22, y + 12, 28, a->search_focus ? COL_TEXT : COL_DIM,
          "Search movie (IMDb)");
    label(a, x + w - 130, y + 18, 20, COL_DIM, "Options");
}

static void draw_search(app_t *a)
{
    ui_t *ui = &a->ui;
    int   fx = 360, fy = 130, fw = ui->screen_w - 720, fh = 84;

    clear(ui);
    draw_header(a, "Search IMDb");

    ui_fill_rect(ui, fx, fy, fw, fh, COL_PANEL);
    frame(ui, fx, fy, fw, fh, 3, COL_ACCENT);
    if (a->q_len == 0) {
        label(a, fx + 28, fy + 22, 36, COL_DIM, "Type a movie title...");
    } else {
        int tw = label(a, fx + 28, fy + 18, 40, COL_TEXT, "%s", a->q);
        if ((SDL_GetTicks() / 450) & 1)
            ui_fill_rect(ui, fx + 34 + tw, fy + 18, 4, 48, COL_ACCENT);
    }

    const int kw = 120, kh = 96, gap = 14, rowh = 110;
    int       x0 = (ui->screen_w - (10 * kw + 9 * gap)) / 2;
    int       y0 = 270;

    for (int r = 0; r < 4; r++) {
        for (int c = 0; c < 10; c++) {
            int  x = x0 + c * (kw + gap), y = y0 + r * rowh;
            int  sel = (a->kb_r == r && a->kb_c == c);
            char s[2] = { KB_ROWS[r][c], '\0' };

            ui_fill_rect(ui, x, y, kw, kh, sel ? COL_SEL : COL_PANEL);
            frame(ui, x, y, kw, kh, sel ? 4 : 2, sel ? COL_ACCENT : COL_LINE);
            label_center(a, x + kw / 2, y + 26, 40,
                         sel ? COL_TEXT : COL_DIM, s);
        }
    }
    int aw = (10 * kw + 9 * gap - 3 * gap) / 4;
    for (int c = 0; c < 4; c++) {
        int x = x0 + c * (aw + gap), y = y0 + 4 * rowh;
        int sel = (a->kb_r == KB_ACT_ROW && a->kb_c == c);

        ui_fill_rect(ui, x, y, aw, kh, sel ? COL_SEL : COL_PANEL);
        frame(ui, x, y, aw, kh, sel ? 4 : 2,
              sel ? COL_ACCENT : (c == 3 ? COL_MAGIC : COL_LINE));
        label_center(a, x + aw / 2, y + 30, 32,
                     sel ? COL_TEXT : COL_DIM, KB_ACT[c]);
    }

    draw_msg(a, ui->screen_h - 96);
    draw_footer(a, "D-pad move     X type     Square delete     R1 space     "
                   "Options search     O back");
}

static void draw_results(app_t *a)
{
    ui_t *ui = &a->ui;
    int   top = 150, rowh = 66;

    clear(ui);
    draw_header(a, "Results");
    label(a, 60, 108, 24, COL_DIM, "for \"%s\"", a->q);

    for (int i = 0; i < a->job.n_hits; i++) {
        const im_hit_t *h = &a->job.hits[i];
        int  y = top + 30 + i * rowh;
        int  sel = (i == a->hit_sel);
        char t[160];

        if (sel) {
            ui_fill_rect(ui, 50, y - 10, ui->screen_w - 100, rowh - 6, COL_SEL);
            ui_fill_rect(ui, 50, y - 10, 6, rowh - 6, COL_ACCENT);
        }
        fit_any(a, 30, t, sizeof t, h->title, ui->screen_w - 560);
        label(a, 76, y, 30, sel ? COL_TEXT : COL_DIM, "%s", t);
        label(a, ui->screen_w - 400, y + 4, 24, sel ? COL_ACCENT : COL_DIM,
              "%s   %s", h->year, h->type);
    }
    draw_msg(a, ui->screen_h - 96);
    draw_footer(a, "Up/Down choose     X open     O new search");
}

#define SRC_ROWS 6

static void draw_info(app_t *a)
{
    ui_t *ui = &a->ui;
    const im_info_t *in = &a->job.info;
    char  t[160], meta[200];
    int   n;

    clear(ui);
    draw_header(a, NULL);

    SDL_Rect dst = { 100, 140, 360, 540 };
    if (a->info_tex) {
        SDL_RenderCopy(ui->r, a->info_tex, NULL, &dst);
    } else {
        ui_fill_rect(ui, dst.x, dst.y, dst.w, dst.h, COL_PANEL);
        label_center(a, dst.x + dst.w / 2, dst.y + dst.h / 2 - 12, 24, COL_DIM,
                     "no poster");
    }
    ui_glow_frame(ui, dst.x - 4, dst.y - 4, dst.w + 8, dst.h + 8);

    int x  = dst.x + dst.w + 70;
    int bw = ui->screen_w - x - 60;

    fit_any(a, 54, t, sizeof t, in->title, bw);
    label(a, x, 130, 54, COL_TEXT, "%s", t);
    ui_fill_rect(ui, x, 200, 120, 4, COL_ACCENT);

    meta[0] = '\0';
    n = 0;
    if (in->year[0])    n += snprintf(meta + n, sizeof meta - (size_t)n, "%s", in->year);
    if (in->rated[0])   n += snprintf(meta + n, sizeof meta - (size_t)n, "%s%s", n ? "   |   " : "", in->rated);
    if (in->runtime[0]) n += snprintf(meta + n, sizeof meta - (size_t)n, "%s%s", n ? "   |   " : "", in->runtime);
    if (in->rating[0])  n += snprintf(meta + n, sizeof meta - (size_t)n, "%sIMDb %s", n ? "   |   " : "", in->rating);
    label(a, x, 216, 26, COL_ACCENT, "%s", meta);
    if (in->genre[0])
        label(a, x, 256, 24, COL_MAGIC, "%s", in->genre);

    int y = 296;
    if (in->director[0]) {
        fit_any(a, 22, t, sizeof t, in->director, bw - 170);
        label(a, x, y, 22, COL_DIM, "Director: %s", t);
        y += 32;
    }
    if (in->actors[0]) {
        fit_any(a, 22, t, sizeof t, in->actors, bw - 130);
        label(a, x, y, 22, COL_DIM, "Cast: %s", t);
        y += 32;
    }
    if (in->plot[0])
        wrap_text(a, x, y + 10, bw, 26, 38, 4, COL_TEXT, in->plot);

    /* sources the user listed for this IMDb id */
    label(a, x, 610, 28, COL_ACCENT, "Sources");
    ui_fill_rect(ui, x + 150, 626, bw - 150, 2, COL_LINE);
    if (a->n_src == 0) {
        label(a, x, 662, 24, COL_DIM, "No sources for %s in your library.", in->id);
        label(a, x, 700, 22, COL_DIM, "Add them to %s (see README)", SOURCES_FILE);
    } else {
        int last = MIN(a->n_src, a->src_top + SRC_ROWS);
        for (int i = a->src_top; i < last; i++) {
            int  ry = 650 + (i - a->src_top) * 50;
            int  sel = (i == a->src_sel);
            char s[80];

            if (sel) {
                ui_fill_rect(ui, x - 14, ry - 8, bw + 14, 46, COL_SEL);
                ui_fill_rect(ui, x - 14, ry - 8, 6, 46, COL_ACCENT);
            }
            fit_any(a, 28, s, sizeof s, a->src[i].server, bw - 420);
            label(a, x + 10, ry, 28, sel ? COL_TEXT : COL_DIM, "%s", s);
            label(a, x + bw - 330, ry + 2, 26, sel ? COL_ACCENT : COL_DIM,
                  "%s", a->src[i].quality);
            if (sel)
                label(a, x + bw - 160, ry + 4, 22, COL_ACCENT, "X  PLAY");
        }
    }
    draw_msg(a, ui->screen_h - 96);
    draw_footer(a, a->n_src ? "Up/Down choose source     X play     O back"
                            : "O back");
}

/* ---- input ---- */

static void flow_button(app_t *a, int b)
{
    if (a->job_active) {
        if (b == SDL_CONTROLLER_BUTTON_B)
            SDL_AtomicSet(&a->job.abort, 1);
        return;
    }
    if (a->screen == SCR_SEARCH) {
        switch (b) {
        case SDL_CONTROLLER_BUTTON_DPAD_UP:    kb_move(a, -1, 0); break;
        case SDL_CONTROLLER_BUTTON_DPAD_DOWN:  kb_move(a, 1, 0);  break;
        case SDL_CONTROLLER_BUTTON_DPAD_LEFT:  kb_move(a, 0, -1); break;
        case SDL_CONTROLLER_BUTTON_DPAD_RIGHT: kb_move(a, 0, 1);  break;
        case SDL_CONTROLLER_BUTTON_A:          kb_press(a);       break;
        case SDL_CONTROLLER_BUTTON_X:          kb_del(a);         break;
        case SDL_CONTROLLER_BUTTON_RIGHTSHOULDER: kb_add(a, ' '); break;
        case SDL_CONTROLLER_BUTTON_START:      kb_submit(a);      break;
        case SDL_CONTROLLER_BUTTON_B:          a->screen = SCR_GRID; break;
        default: break;
        }
    } else if (a->screen == SCR_RESULTS) {
        switch (b) {
        case SDL_CONTROLLER_BUTTON_DPAD_UP:
            if (a->hit_sel > 0) a->hit_sel--;
            break;
        case SDL_CONTROLLER_BUTTON_DPAD_DOWN:
            if (a->hit_sel + 1 < a->job.n_hits) a->hit_sel++;
            break;
        case SDL_CONTROLLER_BUTTON_A:
            if (a->job.n_hits)
                job_start(a, JOB_INFO, a->job.hits[a->hit_sel].id);
            break;
        case SDL_CONTROLLER_BUTTON_B:
            a->screen = SCR_SEARCH;
            break;
        default: break;
        }
    } else if (a->screen == SCR_INFO) {
        switch (b) {
        case SDL_CONTROLLER_BUTTON_DPAD_UP:
            if (a->src_sel > 0) a->src_sel--;
            if (a->src_sel < a->src_top) a->src_top = a->src_sel;
            break;
        case SDL_CONTROLLER_BUTTON_DPAD_DOWN:
            if (a->src_sel + 1 < a->n_src) a->src_sel++;
            if (a->src_sel >= a->src_top + SRC_ROWS)
                a->src_top = a->src_sel - SRC_ROWS + 1;
            break;
        case SDL_CONTROLLER_BUTTON_A:
            if (a->n_src)
                play_url(a, a->src[a->src_sel].url, a->job.info.title,
                         SCR_INFO);
            else
                set_msg(a, "No sources for this title (see README)");
            break;
        case SDL_CONTROLLER_BUTTON_B:
            a->screen = SCR_RESULTS;
            break;
        default: break;
        }
    }
}

/* ---------------- unos ---------------- */

static void move_sel(app_t *a, int d)
{
    if (!a->n_vis)
        return;
    int s = a->sel + d;
    if (s < 0 || s >= a->n_vis)
        return;
    a->sel = s;
    scroll_to_sel(a);
}

static void change_cat(app_t *a, int d)
{
    a->cat = (a->cat + d + a->mv.n_cats) % a->mv.n_cats;
    rebuild_vis(a);
}

static void import_move(app_t *a, int d)
{
    int s = a->imp_sel + d;
    if (s < 0 || s >= a->n_imp)
        return;
    a->imp_sel = s;
    if (s < a->imp_top)
        a->imp_top = s;
    if (s >= a->imp_top + a->imp_rows)
        a->imp_top = s - a->imp_rows + 1;
}

static void on_button(app_t *a, int b)
{
    if (a->screen == SCR_PLAYER) {
        if (player_button(a->pl, b))
            stop_player(a);
        return;
    }
    if (a->screen == SCR_SEARCH || a->screen == SCR_RESULTS ||
        a->screen == SCR_INFO) {
        flow_button(a, b);
        return;
    }
    if (a->screen == SCR_IMPORT) {
        switch (b) {
        case SDL_CONTROLLER_BUTTON_DPAD_UP:   import_move(a, -1); break;
        case SDL_CONTROLLER_BUTTON_DPAD_DOWN: import_move(a, 1);  break;
        case SDL_CONTROLLER_BUTTON_A:
            if (a->n_imp)
                do_import(a, a->imp[a->imp_sel].path);
            break;
        case SDL_CONTROLLER_BUTTON_B:
            a->screen = SCR_GRID;
            break;
        default:
            break;
        }
        return;
    }
    if (!a->have) {
        if (b == SDL_CONTROLLER_BUTTON_Y)
            open_import(a);
        else if (b == SDL_CONTROLLER_BUTTON_START)
            open_search(a);
        else if (b == SDL_CONTROLLER_BUTTON_B)
            a->running = 0;
        return;
    }
    if (a->screen == SCR_DETAIL) {
        if (b == SDL_CONTROLLER_BUTTON_B)
            a->screen = SCR_GRID;
        else if (b == SDL_CONTROLLER_BUTTON_A)
            start_player(a);
        else if (b == SDL_CONTROLLER_BUTTON_X)
            export_one(a);
        return;
    }
    if (a->search_focus) {                    /* grid: search bar has focus */
        if (b == SDL_CONTROLLER_BUTTON_A || b == SDL_CONTROLLER_BUTTON_START)
            open_search(a);
        else if (b == SDL_CONTROLLER_BUTTON_DPAD_DOWN ||
                 b == SDL_CONTROLLER_BUTTON_B)
            a->search_focus = 0;
        return;
    }
    if (b == SDL_CONTROLLER_BUTTON_START) {
        open_search(a);
        return;
    }
    if (b == SDL_CONTROLLER_BUTTON_DPAD_UP && a->sel < COLS) {
        a->search_focus = 1;
        return;
    }
    switch (b) {
    case SDL_CONTROLLER_BUTTON_DPAD_LEFT:  move_sel(a, -1);    break;
    case SDL_CONTROLLER_BUTTON_DPAD_RIGHT: move_sel(a, 1);     break;
    case SDL_CONTROLLER_BUTTON_DPAD_UP:    move_sel(a, -COLS); break;
    case SDL_CONTROLLER_BUTTON_DPAD_DOWN:  move_sel(a, COLS);  break;
    case SDL_CONTROLLER_BUTTON_LEFTSHOULDER:  change_cat(a, -1); break;
    case SDL_CONTROLLER_BUTTON_RIGHTSHOULDER: change_cat(a, 1);  break;
    case SDL_CONTROLLER_BUTTON_A:
        if (a->n_vis)
            a->screen = SCR_DETAIL;
        break;
    case SDL_CONTROLLER_BUTTON_Y:
        open_import(a);
        break;
    case SDL_CONTROLLER_BUTTON_X:
        export_all(a);
        break;
    case SDL_CONTROLLER_BUTTON_B:
        a->running = 0;
        break;
    default:
        break;
    }
}

static int is_dpad(int b)
{
    return b == SDL_CONTROLLER_BUTTON_DPAD_UP ||
           b == SDL_CONTROLLER_BUTTON_DPAD_DOWN ||
           b == SDL_CONTROLLER_BUTTON_DPAD_LEFT ||
           b == SDL_CONTROLLER_BUTTON_DPAD_RIGHT;
}

/* ---------------- ucitavanje spiska ---------------- */

static int load_movies(movies_t *mv)
{
    size_t len = 0;
    char  *js = NULL;

    /* Neobavezno: movies_url.txt sa jednim URL-om; ako uspije, ima prednost. */
    char *u = read_file(JSON_URLFILE, &len);
    if (u) {
        char *e = u + strcspn(u, "\r\n");
        *e = '\0';
        membuf_t b;
        if (u[0] && http_get(u, &b) == 0) {
            js = (char *)b.d;
            len = b.n;
            LOG("spisak filmova sa mreze (%zu B)", len);
        } else {
            ERR("movies_url.txt: preuzimanje nije uspjelo, koristim lokalni");
        }
        free(u);
    }
    if (!js) {
        js = read_file(JSON_LOCAL, &len);
        if (!js)
            ERR("nema %s", JSON_LOCAL);
    }
    if (!js)
        return -1;

    int n = movies_parse(mv, js, len);
    free(js);
    if (n < 0)
        ERR("movies.json se ne moze parsirati");
    return n;
}

/* ---------------- main ---------------- */

int main(int argc, char **argv)
{
    app_t a;
    memset(&a, 0, sizeof a);
    a.running = 1;
    a.held_btn = -1;

    LOG("start %s 1.0-dev", APP_NAME);

    if (SDL_Init(SDL_INIT_VIDEO) != 0) {
        ERR("SDL_Init VIDEO: %s", SDL_GetError());
        return 1;
    }
    curl_global_init(CURL_GLOBAL_DEFAULT);
    if (SDL_InitSubSystem(SDL_INIT_GAMECONTROLLER) != 0)
        ERR("SDL_Init GAMECONTROLLER: %s", SDL_GetError());

    SDL_Window *win = SDL_CreateWindow(APP_NAME, SDL_WINDOWPOS_UNDEFINED,
                                       SDL_WINDOWPOS_UNDEFINED, 1920, 1080,
                                       SDL_WINDOW_FULLSCREEN_DESKTOP);
    if (!win) {
        ERR("SDL_CreateWindow: %s", SDL_GetError());
        SDL_Quit();
        return 1;
    }
    /* Na PS5 akcelerisani renderer ne postoji (vidi ComicReader SETUP.md). */
    SDL_Renderer *ren = SDL_CreateRenderer(win, -1, SDL_RENDERER_SOFTWARE);
    if (!ren) {
        ERR("SDL_CreateRenderer: %s", SDL_GetError());
        SDL_DestroyWindow(win);
        SDL_Quit();
        return 1;
    }

    int sw = 1920, sh = 1080;
    SDL_GetRendererOutputSize(ren, &sw, &sh);
    if (ui_init(&a.ui, ren, sw, sh) != 0)
        return 1;
    a.font = font_open(ren, FONT_FILE);
    if (!a.font)
        LOG("nema fonta %s - farsi tekst ce biti '?'", FONT_FILE);
    load_omdb_key(&a);
    if (!a.omdb_key[0])
        LOG("no %s - IMDb search disabled until you add an OMDb key", OMDB_KEYFILE);
    load_source_api(&a);
    a.rows_fit = MAX(1, (sh - GRID_Y - 60) / CELL_H);
    a.imp_rows = MAX(1, (sh - IMP_TOP - 120) / IMP_ROW_H);

    /* Prvi kadar odmah, da ekran ne ostane crn dok se spisak ucitava. */
    clear(&a.ui);
    draw_header(&a, NULL);
    label(&a, 60, 150, 26, COL_DIM, "Loading movie list...");
    SDL_RenderPresent(ren);

    movies_t first;
    if (load_movies(&first) > 0)
        install_movies(&a, &first);

    SDL_GameController *gc = NULL;
    for (int i = 0; i < SDL_NumJoysticks(); i++)
        if (SDL_IsGameController(i) && (gc = SDL_GameControllerOpen(i)))
            break;
    if (!gc)
        ERR("kontroler nije nadjen");

    while (a.running) {
        SDL_Event ev;
        while (SDL_PollEvent(&ev)) {
            if (ev.type == SDL_QUIT) {
                a.running = 0;
            } else if (ev.type == SDL_CONTROLLERBUTTONDOWN) {
                int b = ev.cbutton.button;
                on_button(&a, b);
                if (is_dpad(b)) {
                    a.held_btn = b;
                    a.held_since = a.held_last = SDL_GetTicks();
                }
            } else if (ev.type == SDL_CONTROLLERBUTTONUP) {
                if (ev.cbutton.button == a.held_btn)
                    a.held_btn = -1;
            } else if (ev.type == SDL_CONTROLLERDEVICEADDED && !gc) {
                gc = SDL_GameControllerOpen(ev.cdevice.which);
            } else if (ev.type == SDL_KEYDOWN &&
                       ev.key.keysym.sym == SDLK_ESCAPE) {
                a.running = 0;
            }
        }

        /* Ponavljanje d-pada: 350 ms zadrska, pa svakih 90 ms. */
        if (a.held_btn >= 0) {
            uint32_t now = SDL_GetTicks();
            if (now - a.held_since > 350 && now - a.held_last > 90) {
                on_button(&a, a.held_btn);
                a.held_last = now;
            }
        }

        job_poll(&a);
        if (a.have)
            upload_posters(&a, ren);

        if (a.screen == SCR_PLAYER) {
            player_update(a.pl);
            player_draw(a.pl, &a.ui);
        } else if (a.screen == SCR_IMPORT)
            draw_import(&a);
        else if (a.screen == SCR_SEARCH)
            draw_search(&a);
        else if (a.screen == SCR_RESULTS)
            draw_results(&a);
        else if (a.screen == SCR_INFO)
            draw_info(&a);
        else if (!a.have)
            draw_empty(&a);
        else if (a.screen == SCR_GRID)
            draw_grid(&a);
        else
            draw_detail(&a);

        if (a.job_active)
            draw_busy(&a);
        SDL_RenderPresent(ren);
        SDL_Delay(16);
    }

    player_close(a.pl);
    job_shutdown(&a);
    stop_posters(&a);
    movies_free(&a.mv);
    free(a.vis);
    font_close(a.font);
    ui_shutdown(&a.ui);
    if (gc)
        SDL_GameControllerClose(gc);
    SDL_DestroyRenderer(ren);
    SDL_DestroyWindow(win);
    SDL_Quit();
    curl_global_cleanup();
    LOG("kraj");
    (void)argc; (void)argv;
    return 0;
}

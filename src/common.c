#include "common.h"
#include <SDL2/SDL.h>
#include <SDL2/SDL_net.h>
#include <stdatomic.h>

static int tls_recv_all(TCPsocket sock, void *buf, int need)
{
    int got = 0;
    while (got < need) {
        int r = SDLNet_TCP_Recv(sock, (char*)buf + got, need - got);
        if (r <= 0) return -1;
        got += r;
    }
    return 0;
}

int http_get_ex(const char *url, membuf_t *out, void *abort_flag)
{
    SDL_atomic_t *ab = (SDL_atomic_t*)abort_flag;
    out->d = NULL; out->n = 0;

    /* parse url */
    int https = strncmp(url, "https://", 8) == 0;
    const char *host_start = url + (https ? 8 : 7);
    const char *path_start = strchr(host_start, '/');
    if (!path_start) path_start = host_start + strlen(host_start);

    char host[256] = {0};
    int  hlen = (int)(path_start - host_start);
    if (hlen >= (int)sizeof host) return -1;
    memcpy(host, host_start, hlen);

    const char *path = *path_start ? path_start : "/";
    int port = https ? 443 : 80;

    char *colon = strchr(host, ':');
    if (colon) { port = atoi(colon+1); *colon = 0; }

    IPaddress ip;
    if (SDLNet_ResolveHost(&ip, host, (Uint16)port) < 0) return -1;
    if (ab && SDL_AtomicGet(ab)) return -1;

    TCPsocket sock = SDLNet_TCP_Open(&ip);
    if (!sock) return -1;

    char req[1024];
    snprintf(req, sizeof req,
        "GET %s HTTP/1.0\r\n"
        "Host: %s\r\n"
        "User-Agent: CineHub/1.0\r\n"
        "Accept: */*\r\n"
        "Connection: close\r\n\r\n",
        path, host);
    SDLNet_TCP_Send(sock, req, (int)strlen(req));

    membuf_t buf = {0};
    char tmp[4096];
    int  r;
    while ((r = SDLNet_TCP_Recv(sock, tmp, sizeof tmp)) > 0) {
        if (ab && SDL_AtomicGet(ab)) { SDLNet_TCP_Close(sock); free(buf.d); return -1; }
        unsigned char *nd = realloc(buf.d, buf.n + r + 1);
        if (!nd) { SDLNet_TCP_Close(sock); free(buf.d); return -1; }
        buf.d = nd;
        memcpy(buf.d + buf.n, tmp, r);
        buf.n += r;
    }
    SDLNet_TCP_Close(sock);
    if (!buf.d) return -1;
    buf.d[buf.n] = 0;

    /* split headers / body */
    const char *sep = strstr((char*)buf.d, "\r\n\r\n");
    if (!sep) { free(buf.d); return -1; }
    size_t body_off = (sep - (char*)buf.d) + 4;
    size_t body_len = buf.n - body_off;
    memmove(buf.d, buf.d + body_off, body_len);
    buf.d[body_len] = 0;
    buf.n = body_len;

    *out = buf;
    return 0;
}

char *read_file(const char *path, size_t *len_out)
{
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END);
    long sz = ftell(f);
    rewind(f);
    if (sz <= 0) { fclose(f); return NULL; }
    char *buf = malloc(sz + 1);
    if (!buf) { fclose(f); return NULL; }
    fread(buf, 1, sz, f);
    fclose(f);
    buf[sz] = 0;
    if (len_out) *len_out = (size_t)sz;
    return buf;
}

int write_file(const char *path, const void *data, size_t len)
{
    FILE *f = fopen(path, "wb");
    if (!f) return -1;
    fwrite(data, 1, len, f);
    fclose(f);
    return 0;
}

#include "imdb.h"
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

static void skip_ws(const char **p, const char *end)
{
    while (*p < end && isspace((unsigned char)**p)) (*p)++;
}

static const char *str_end(const char *p, const char *end)
{
    if (p >= end || *p != '"') return NULL;
    for (p++; p < end; p++) {
        if (*p == '\\') { p++; continue; }
        if (*p == '"')  return p + 1;
    }
    return NULL;
}

static const char *container_end(const char *p, const char *end)
{
    int depth = 0;
    for (; p < end; p++) {
        if (*p == '"') {
            p = str_end(p, end);
            if (!p) return NULL;
            p--; continue;
        }
        if (*p == '{' || *p == '[') depth++;
        else if ((*p == '}' || *p == ']') && --depth == 0) return p + 1;
    }
    return NULL;
}

static int hex4(const char *s, unsigned *out)
{
    unsigned v = 0;
    for (int i = 0; i < 4; i++) {
        char c = s[i]; v <<= 4;
        if      (c>='0'&&c<='9') v|=(unsigned)(c-'0');
        else if (c>='a'&&c<='f') v|=(unsigned)(c-'a'+10);
        else if (c>='A'&&c<='F') v|=(unsigned)(c-'A'+10);
        else return -1;
    }
    *out = v; return 0;
}

static void put_utf8(char *d, size_t cap, size_t *n, unsigned cp)
{
    char t[4]; int k;
    if      (cp<0x80)    { t[0]=(char)cp; k=1; }
    else if (cp<0x800)   { t[0]=(char)(0xC0|cp>>6);
                           t[1]=(char)(0x80|(cp&63)); k=2; }
    else if (cp<0x10000) { t[0]=(char)(0xE0|cp>>12);
                           t[1]=(char)(0x80|((cp>>6)&63));
                           t[2]=(char)(0x80|(cp&63)); k=3; }
    else                 { t[0]=(char)(0xF0|cp>>18);
                           t[1]=(char)(0x80|((cp>>12)&63));
                           t[2]=(char)(0x80|((cp>>6)&63));
                           t[3]=(char)(0x80|(cp&63)); k=4; }
    if (*n+(size_t)k < cap) { memcpy(d+*n,t,(size_t)k); *n+=(size_t)k; }
}

static const char *str_decode(const char *p, const char *end,
                               char *dst, size_t cap)
{
    size_t n=0;
    if (cap==0) return NULL;
    dst[0]='\0';
    if (p>=end || *p!='"') return NULL;
    p++;
    while (p<end && *p!='"') {
        char c=*p++;
        if (c!='\\') { if (n+1<cap) dst[n++]=c; continue; }
        if (p>=end) return NULL;
        char e=*p++;
        if (e=='u') {
            unsigned cp,lo;
            if (end-p<4||hex4(p,&cp)!=0) return NULL;
            p+=4;
            if (cp>=0xD800&&cp<0xDC00&&end-p>=6&&
                p[0]=='\\'&&p[1]=='u'&&hex4(p+2,&lo)==0&&
                lo>=0xDC00&&lo<0xE000) {
                cp=0x10000+((cp-0xD800)<<10)+(lo-0xDC00); p+=6;
            }
            put_utf8(dst,cap,&n,cp);
        } else {
            char o=(e=='n'||e=='r'||e=='t'||e=='b'||e=='f')?' ':e;
            if (n+1<cap) dst[n++]=o;
        }
    }
    if (p>=end) return NULL;
    dst[n]='\0'; return p+1;
}

static int obj_find(const char *o, const char *oend, const char *key,
                    const char **vb, const char **ve)
{
    const char *p=o; size_t kl=strlen(key);
    skip_ws(&p,oend);
    if (p>=oend||*p!='{') return 0;
    p++;
    for (;;) {
        skip_ws(&p,oend);
        if (p>=oend||*p=='}') return 0;
        if (*p==',') { p++; continue; }
        const char *kb=p, *ke=str_end(p,oend);
        if (!ke) return 0;
        p=ke; skip_ws(&p,oend);
        if (p>=oend||*p!=':') return 0;
        p++; skip_ws(&p,oend);
        if (p>=oend) return 0;
        const char *v=p, *vend;
        if      (*p=='"')           vend=str_end(p,oend);
        else if (*p=='{'||*p=='[')  vend=container_end(p,oend);
        else {
            vend=p;
            while (vend<oend&&*vend!=','&&*vend!='}'&&*vend!=']') vend++;
        }
        if (!vend) return 0;
        if ((size_t)(ke-kb)==kl+2&&memcmp(kb+1,key,kl)==0) {
            *vb=v; *ve=vend; return 1;
        }
        p=vend;
    }
}

static int get_str(const char *o, const char *oend, const char *key,
                   char *dst, size_t cap)
{
    const char *vb,*ve;
    dst[0]='\0';
    if (!obj_find(o,oend,key,&vb,&ve)||*vb!='"') return 0;
    if (!str_decode(vb,ve,dst,cap)) { dst[0]='\0'; return 0; }
    if (strcmp(dst,"N/A")==0) dst[0]='\0';
    return dst[0]!='\0';
}

static int arr_next(const char **p, const char *aend,
                    const char **ob, const char **oe)
{
    const char *q=*p;
    for (;;) {
        skip_ws(&q,aend);
        if (q>=aend||*q==']') return 0;
        if (*q==','||*q=='[') { q++; continue; }
        if (*q!='{') return 0;
        const char *e=container_end(q,aend);
        if (!e) return 0;
        *ob=q; *oe=e; *p=e; return 1;
    }
}

static void set_err(char *err, size_t cap, const char *msg)
{
    if (err&&cap) snprintf(err,cap,"%s",msg);
}

void im_url_encode(char *dst, size_t cap, const char *src)
{
    size_t n=0;
    if (cap==0) return;
    for (const unsigned char *p=(const unsigned char*)src; *p; p++) {
        if (isalnum(*p)||*p=='-'||*p=='_'||*p=='.'||*p=='~') {
            if (n+1>=cap) break;
            dst[n++]=(char)*p;
        } else {
            if (n+3>=cap) break;
            n+=(size_t)snprintf(dst+n,cap-n,"%%%02X",*p);
        }
    }
    dst[n]='\0';
}

static int omdb_get(im_fetch_fn f, void *ud, const char *url,
                    char **body, size_t *len, char *err, size_t errcap)
{
    *body=NULL;
    if (f(ud,url,body,len)!=0||!*body) {
        set_err(err,errcap,"Network error"); return -1;
    }
    const char *b=*body, *e=*body+*len;
    char resp[16];
    if (!get_str(b,e,"Response",resp,sizeof resp)||
        strcasecmp(resp,"True")!=0) {
        char msg[96];
        if (!get_str(b,e,"Error",msg,sizeof msg))
            snprintf(msg,sizeof msg,"Unexpected reply from OMDb");
        set_err(err,errcap,msg);
        free(*body); *body=NULL; return -2;
    }
    return 0;
}

int im_search(im_fetch_fn f, void *ud, const char *key,
              const char *query, im_hit_t *out, int cap,
              char *err, size_t errcap)
{
    char q[256],url[512]; char *body; size_t len=0;
    set_err(err,errcap,"");
    im_url_encode(q,sizeof q,query);
    snprintf(url,sizeof url,
             "https://www.omdbapi.com/?apikey=%s&s=%s",key,q);
    int rc=omdb_get(f,ud,url,&body,&len,err,errcap);
    if (rc!=0) {
        if (rc==-2&&err&&strcmp(err,"Movie not found!")==0) return 0;
        return rc;
    }
    const char *vb,*ve,*p,*ob,*oe; int n=0;
    if (!obj_find(body,body+len,"Search",&vb,&ve)||*vb!='[') {
        free(body); return 0;
    }
    p=vb;
    while (n<cap&&arr_next(&p,ve,&ob,&oe)) {
        im_hit_t *h=&out[n]; memset(h,0,sizeof *h);
        if (!get_str(ob,oe,"imdbID",h->id,sizeof h->id)||
            !get_str(ob,oe,"Title",h->title,sizeof h->title)) continue;
        get_str(ob,oe,"Year",h->year,sizeof h->year);
        get_str(ob,oe,"Type",h->type,sizeof h->type);
        n++;
    }
    free(body); return n;
}

static int valid_id(const char *id)
{
    if (id[0]!='t'||id[1]!='t') return 0;
    size_t n=strlen(id+2);
    if (n<7||n>10) return 0;
    for (const char *p=id+2;*p;p++)
        if (!isdigit((unsigned char)*p)) return 0;
    return 1;
}

int im_details(im_fetch_fn f, void *ud, const char *key,
               const char *id, im_info_t *out, char *err, size_t errcap)
{
    char url[256]; char *body; size_t len=0;
    set_err(err,errcap,""); memset(out,0,sizeof *out);
    if (!valid_id(id)) { set_err(err,errcap,"Bad IMDb id"); return -3; }
    snprintf(url,sizeof url,
             "https://www.omdbapi.com/?apikey=%s&i=%s&plot=short",key,id);
    int rc=omdb_get(f,ud,url,&body,&len,err,errcap);
    if (rc!=0) return rc;
    const char *b=body, *e=body+len;
    snprintf(out->id,sizeof out->id,"%s",id);
    get_str(b,e,"Title",     out->title,   sizeof out->title);
    get_str(b,e,"Year",      out->year,    sizeof out->year);
    get_str(b,e,"Rated",     out->rated,   sizeof out->rated);
    get_str(b,e,"Runtime",   out->runtime, sizeof out->runtime);
    get_str(b,e,"Genre",     out->genre,   sizeof out->genre);
    get_str(b,e,"Director",  out->director,sizeof out->director);
    get_str(b,e,"Actors",    out->actors,  sizeof out->actors);
    get_str(b,e,"Plot",      out->plot,    sizeof out->plot);
    get_str(b,e,"imdbRating",out->rating,  sizeof out->rating);
    get_str(b,e,"Poster",    out->poster,  sizeof out->poster);
    free(body);
    if (!out->title[0]) {
        set_err(err,errcap,"Unexpected reply from OMDb"); return -2;
    }
    return 0;
}

int im_sources_for(const char *json, size_t len, const char *id,
                   im_source_t *out, int cap)
{
    const char *end=json+len, *p=json;
    const char *ob,*oe; int n=0;
    skip_ws(&p,end);
    if (p<end&&(unsigned char)*p==0xEF&&end-p>=3) p+=3;
    while (arr_next(&p,end,&ob,&oe)) {
        char cur[16];
        if (!get_str(ob,oe,"imdb_id",cur,sizeof cur)||
            strcasecmp(cur,id)!=0) continue;
        const char *vb,*ve;
        if (!obj_find(ob,oe,"sources",&vb,&ve)||*vb!='[') return 0;
        const char *q=vb, *sb,*se;
        while (n<cap&&arr_next(&q,ve,&sb,&se)) {
            im_source_t *s=&out[n]; memset(s,0,sizeof *s);
            if (!get_str(sb,se,"url",s->url,sizeof s->url)) continue;
            if (!get_str(sb,se,"server",s->server,sizeof s->server))
                snprintf(s->server,sizeof s->server,"Server %d",n+1);
            get_str(sb,se,"quality",s->quality,sizeof s->quality);
            n++;
        }
        return n;
    }
    return 0;
}

int im_remote_sources(im_fetch_fn f, void *ud, const char *api_base,
                      const char *id, im_source_t *out, int cap)
{
    if (!api_base||!api_base[0]||!id||!id[0]) return 0;
    char base[512]; size_t blen=strlen(api_base);
    if (blen>=sizeof base) blen=sizeof base-1;
    memcpy(base,api_base,blen);
    while (blen>0&&(base[blen-1]=='/'||base[blen-1]==' ')) blen--;
    base[blen]='\0';
    char url[640]; snprintf(url,sizeof url,"%s/%s",base,id);
    char *body=NULL; size_t len=0;
    if (f(ud,url,&body,&len)!=0||!body) return -1;
    int n=im_sources_for(body,len,id,out,cap);
    free(body); return n>0?n:0;
}

/* Deliberately no built-in third-party embed-site scraping. Source discovery
 * is delegated to the configured resolver API; direct authorized URLs can be
 * maintained in sources.json. This avoids brittle site-specific assumptions. */
int im_scrape_sources(im_fetch_fn f, void *ud, const char *id,
                      im_source_t *out, int cap)
{
    (void)f; (void)ud; (void)id; (void)out; (void)cap;
    return 0;
}

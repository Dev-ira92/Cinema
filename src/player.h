#ifndef PLAYER_H
#define PLAYER_H

#include <SDL2/SDL.h>
#include "ui.h"

typedef struct player player_t;

player_t *player_open(SDL_Renderer *r, const char *url, const char *title,
                      int disp_w, int disp_h);
void      player_close(player_t *p);
void      player_update(player_t *p);
void      player_draw(player_t *p, ui_t *ui);
int       player_button(player_t *p, int button);

#endif

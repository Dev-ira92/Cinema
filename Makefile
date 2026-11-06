# Cine Hub - PS5 payload (SDL2 + libcurl), run through websrv.
# Recipes use ">" instead of TAB so copy-paste cannot break them.

TARGET := cinehub.elf
SRCDIR := src
BUILD  := build

SRCS := $(SRCDIR)/main.c $(SRCDIR)/movies.c $(SRCDIR)/common.c \
        $(SRCDIR)/ui.c $(SRCDIR)/stb_impl.c $(SRCDIR)/shape.c \
        $(SRCDIR)/text.c $(SRCDIR)/player.c $(SRCDIR)/imdb.c

WARN        := -Wall -Wextra -Wno-unused-parameter
BASE_CFLAGS := $(WARN) -O2 -I$(SRCDIR)

ifdef PS5_PAYLOAD_SDK
include $(PS5_PAYLOAD_SDK)/toolchain/prospero.mk
endif

.RECIPEPREFIX := >

ifdef PS5_PAYLOAD_SDK
# All dependencies are supplied by the pinned PacBrew bundle.
# FFmpeg is the PS5-targeted static package (libav* pkg-config modules).
PS5_PKGS    := sdl2 SDL2_net libarchive libwebp libcurl libxml-2.0 \
               libavformat libavcodec libswscale libswresample libavutil
BASE_CFLAGS += -DCURL_STATICLIB
PS5_CFLAGS  := $(shell $(PKG_CONFIG) --cflags $(PS5_PKGS))
PS5_LIBS    := $(shell $(PKG_CONFIG) --libs $(PS5_PKGS))

# Zvuk: stub biblioteke SceAudioOut iz SDK-a. Dodaje se samo ako ga linker
# stvarno nadje, pa bez njega program ostaje bez zvuka umjesto da ne linka.
AUDIO_LIB   := $(shell printf 'int main(void){return 0;}\n' > /tmp/cine_probe.c && \
                 $(CC) /tmp/cine_probe.c -nodefaultlibs -lc -lkernel_sys \
                 -lSceLibcInternal -lSceAudioOut -o /tmp/cine_probe.elf \
                 >/dev/null 2>&1 && echo -lSceAudioOut)
$(info audio library: $(if $(AUDIO_LIB),$(AUDIO_LIB),NOT FOUND - no sound))

CFLAGS += $(BASE_CFLAGS) $(PS5_CFLAGS)
# Without this the process dies with SIGSYS (see ComicReader SETUP.md).
LDADD  += -nodefaultlibs $(PS5_LIBS) -lz -lSDL2main -lm \
          -lc -lkernel_sys -lSceLibcInternal -lSceNet $(AUDIO_LIB)

OBJS := $(patsubst $(SRCDIR)/%.c,$(BUILD)/%.o,$(SRCS))

all: $(TARGET)

$(BUILD):
> @mkdir -p $(BUILD)

$(BUILD)/%.o: $(SRCDIR)/%.c | $(BUILD)
> $(CC) $(CFLAGS) -c -o $@ $<

$(TARGET): $(OBJS)
> $(CC) -o $@ $(OBJS) $(LDADD)

.PHONY: all
endif

clean:
> rm -rf $(BUILD) $(TARGET) package

.PHONY: clean

# --- Package for home-screen install (fake-pkg folder, not a signed .pkg) ---
# Produces package/<TITLE_ID>/eboot.bin + sce_sys/{param.sfo,icon0.png,pic0.png}.
# Title id / content id are baked by scripts/make_sfo.py; edit there if you
# need a different id (keep it in the PPSA-homebrew range to avoid clashing
# with a real title).
TITLE_ID := PPSA85301
PKGDIR    := package/$(TITLE_ID)

package: $(TARGET)
> @mkdir -p $(PKGDIR)/sce_sys
> cp $(TARGET) $(PKGDIR)/eboot.bin
> python3 scripts/make_sfo.py \
>   --title "CineHub" \
>   --title-id "$(TITLE_ID)" \
>   --content-id "IV0000-$(TITLE_ID)_00-CINEHUBPS5000000" \
>   --out $(PKGDIR)/sce_sys/param.sfo
> cp sce_sys/icon0.png $(PKGDIR)/sce_sys/icon0.png
> cp sce_sys/pic0.png  $(PKGDIR)/sce_sys/pic0.png
> @echo "package ready: $(PKGDIR)/  (eboot.bin + sce_sys/)"

.PHONY: package

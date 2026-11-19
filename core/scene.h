#ifndef ICS_SCENE_H
#define ICS_SCENE_H
#ifdef __cplusplus
extern "C" {
#endif
/* Straight RGBA8 sRGB, top-down, width*4 bytes per row. Caller owns buffer.
   Returns 0 on success, -1 for invalid bounds, -2 for allocation failure. */
int studio_render_rgba(unsigned char *rgba, int width, int height, int frame, int total_frames);
#ifdef __cplusplus
}
#endif
#endif

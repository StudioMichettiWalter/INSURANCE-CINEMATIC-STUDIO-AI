#include "scene.h"
#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

typedef struct { double x, y, z; } Vec;
typedef struct { double x, y, inv_z, u, v; } Vertex;
typedef struct { Vec pos; double u, v; } Corner;
typedef enum { BOARD, GILT, PAPER, BLOCK, INK } Material;
typedef struct {
    int w, h, shadow;
    double focal;
    Vec eye, forward, right, up, light;
    unsigned char *pixels;
    float *depth;
} View;

static Vec vec(double x, double y, double z) { Vec a = {x,y,z}; return a; }
static Vec add(Vec a, Vec b) { return vec(a.x+b.x, a.y+b.y, a.z+b.z); }
static Vec sub(Vec a, Vec b) { return vec(a.x-b.x, a.y-b.y, a.z-b.z); }
static Vec scale(Vec a, double f) { return vec(a.x*f, a.y*f, a.z*f); }
static double dot(Vec a, Vec b) { return a.x*b.x + a.y*b.y + a.z*b.z; }
static Vec cross(Vec a, Vec b) { return vec(a.y*b.z-a.z*b.y, a.z*b.x-a.x*b.z, a.x*b.y-a.y*b.x); }
static Vec unit(Vec a) { return scale(a, 1.0/sqrt(dot(a,a))); }
static double ease(double t) {
    t = fmax(0., fmin(1.,t));
    return t*t*(3.-2.*t);
}

static Vertex project(const View *view, Corner a) {
    Vec p = a.pos;
    if (view->shadow) {
        /* Intersect the directional light ray with the receiver y=0. */
        p = sub(p, scale(view->light, p.y/view->light.y));
    }
    Vec d = sub(p, view->eye);
    double inv = 1./dot(d, view->forward);
    Vertex v = {view->w*.5 + view->focal*dot(d,view->right)*inv,
                view->h*.49 - view->focal*dot(d,view->up)*inv,
                inv, a.u*inv, a.v*inv};
    return v;
}

static double edge(Vertex a, Vertex b, double x, double y) {
    return (b.x-a.x)*(y-a.y)-(b.y-a.y)*(x-a.x);
}

static Material pattern(Material m, double u, double v) {
    if (m == BOARD) {
        if (u>.14 && u<.88 && v>.12 && v<.88 &&
             (u<.152 || u>.868 || v<.13 || v>.87)) return GILT;
        if (u>.27 && u<.74 && v>.33 && v<.35) return GILT;
    }
    if (m == PAPER && u>.16 && u<.84 && v>.16 && v<.82) {
        if (v<.20 && u<.57) return INK;
        if (v>.48 && v<.54) return GILT;
        double band = (v-.25)*14.;
        if (band>0 && band-floor(band)<.14 && u<.80-.06*fmod(floor(band),3.))
            return INK;
    }
    return m;
}

static void triangle(View *view, Corner p, Corner q, Corner r, Material m) {
    static const unsigned char colors[][3] = {
        {21,43,71}, {230,199,119}, {246,241,227}, {206,195,170}, {60,73,87}
    };
    Vertex a=project(view,p), b=project(view,q), c=project(view,r);
    double area=edge(a,b,c.x,c.y);
    if (fabs(area)<1e-8) return;
    int x0=(int)fmax(0.,floor(fmin(a.x,fmin(b.x,c.x))));
    int x1=(int)fmin(view->w-1.,ceil(fmax(a.x,fmax(b.x,c.x))));
    int y0=(int)fmax(0.,floor(fmin(a.y,fmin(b.y,c.y))));
    int y1=(int)fmin(view->h-1.,ceil(fmax(a.y,fmax(b.y,c.y))));
    Vec normal = unit(cross(sub(q.pos,p.pos),sub(r.pos,p.pos)));
    if (dot(normal,sub(view->eye,p.pos))<0.) normal=scale(normal,-1.);
    double light = .67 + .33*fmax(0.,dot(normal,view->light));
    for (int y=y0;y<=y1;++y) {
        for (int x=x0;x<=x1;++x) {
            double s=edge(b,c,x+.5,y+.5)/area;
            double t=edge(c,a,x+.5,y+.5)/area;
            double u=1.-s-t;
            if (s < -1e-9 || t < -1e-9 || u < -1e-9) continue;
            size_t i=(size_t)y*view->w+x;
            unsigned char *dst=view->pixels+4*i;
            if (view->shadow) {
                dst[0]=8; dst[1]=14; dst[2]=23; dst[3]=95;
                continue;
            }
            double depth=s*a.inv_z+t*b.inv_z+u*c.inv_z;
            if (depth<=view->depth[i]) continue;
            view->depth[i]=(float)depth;
            Material mapped=pattern(m,(s*a.u+t*b.u+u*c.u)/depth,(s*a.v+t*b.v+u*c.v)/depth);
            for (int k=0;k<3;++k) dst[k]=(unsigned char)(colors[mapped][k]*light+.5);
            dst[3]=255;
        }
    }
}
static void quad(View *view, Vec a, Vec b, Vec c, Vec d, double u0, double u1, Material m) {
    Corner pa={a,u0,0}, pb={b,u1,0}, pc={c,u1,1}, pd={d,u0,1};
    triangle(view,pa,pb,pc,m);
    triangle(view,pa,pc,pd,m);
}

static void slab(View *view, double x, double width, double half_z, double base, double height, Material m) {
    Vec a=vec(x,base,-half_z), b=vec(x+width,base,-half_z);
    Vec c=vec(x+width,base,half_z), d=vec(x,base,half_z), lift=vec(0,height,0);
    Vec e=add(a,lift), f=add(b,lift), g=add(c,lift), h=add(d,lift);
    quad(view,a,b,f,e,0,1,BLOCK); quad(view,b,c,g,f,0,1,BLOCK);
    quad(view,c,d,h,g,0,1,BLOCK); quad(view,d,a,e,h,0,1,BLOCK);
    quad(view,e,f,g,h,0,1,m);
}
static void book(View *view, double time) {
    const double pi=3.14159265358979323846;
    slab(view,-.05,1.54,1.02,.05,.07,BOARD);
    slab(view,.005,1.43,.975,.12,.095,PAPER);
    double angle=pi*(1.-.012)*ease((time-.06)/.46);
    /* Rigid front board pivots on the spine. */
    Vec a=vec(0,.23,-1.02), d=vec(0,.23,1.02);
    Vec tip=vec(1.49*cos(angle),1.49*sin(angle),0);
    quad(view,a,add(a,tip),add(d,tip),d,0.,1.,BOARD);

    /* Turning sheet uses a swept strip with varying local tangent. Integrating
       unit tangents keeps its length fixed while curvature changes. */
    double turn=ease((time-.31)/.50);
    double x=0., y=.22;
    for (int i=0;i<24;++i) {
        double u0=i/24., u1=(i+1)/24.;
        double bend=pi*.97*turn + .84*sin(pi*turn)*cos(pi*(u0+u1)*.5);
        double nx=x+1.43/24.*cos(bend), ny=y+1.43/24.*sin(bend);
        quad(view,vec(x,y,-.97),vec(nx,ny,-.97),vec(nx,ny,.97),vec(x,y,.97),u0,u1,PAPER);
        x=nx; y=ny;
    }
}

int studio_render_rgba(unsigned char *rgba,int width,int height,int frame,int total_frames) {
    if (!rgba || width<1 || height<1 || width>4096 || height>4096 ||
        (size_t)width*height>8388608u || frame<0 || frame>=total_frames) return -1;
    size_t count=(size_t)width*height;
    float *z=calloc(count,sizeof(*z));
    if (!z) return -2;
    memset(rgba,0,count*4);
    double t=total_frames==1 ? 1. : (double)frame/(total_frames-1);
    View view={0};
    view.w=width; view.h=height; view.depth=z; view.pixels=rgba;
    view.eye=vec(1.9-.45*ease(t),4.9,4.5);
    view.forward=unit(sub(vec(.2,.34,0),view.eye));
    view.right=unit(cross(view.forward,vec(0,1,0)));
    view.up=cross(view.right,view.forward);
    view.focal=fmin(width*1.45,height*1.38);
    view.light=unit(vec(-.65,1.,-.55));
    view.shadow=1; book(&view,t);
    view.shadow=0; book(&view,t);
    free(z);
    return 0;
}

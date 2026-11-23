package dev.battlepedia;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import java.util.Random;

/**
 * Pixel-art chrome for BattlePedia v0.4.
 * Drawn natively at integer GUI pixels: decorative pixels never scale item textures.
 */
final class PediaChrome {
    static final int BACK = 0xFF0C0D24;
    static final int PANEL = 0xEE17142E;
    static final int PANEL2 = 0xF01C1937;
    static final int INK = 0xFFF6F1FF;
    static final int LIGHT = 0xFFC8BADC;
    static final int DIM = 0xFFA49AB9;
    static final int VIOLET = 0xFFB56BFF;
    static final int MAGENTA = 0xFFE077FA;
    static final int GOLD = 0xFFFFC352;
    static final int GREEN = 0xFF62F58D;
    static final int BLUE = 0xFF7DC7FF;
    static final int LINE = 0xFF5F468F;

    private PediaChrome() {}

    static void pixelFrame(DrawContext ctx,int x,int y,int w,int h,int fill,int edge,boolean shine){
        if(w<8 || h<8)return;
        if(shine) {
            ctx.fill(x-2,y+5,x-1,y+h-5,0x3DBD5BFF);
            ctx.fill(x+w+1,y+5,x+w+2,y+h-5,0x3DBD5BFF);
            ctx.fill(x+5,y-2,x+w-5,y-1,0x3DBD5BFF);
            ctx.fill(x+5,y+h+1,x+w-5,y+h+2,0x3DBD5BFF);
        }
        ctx.fill(x+3,y,x+w-3,y+h,fill);
        ctx.fill(x,y+3,x+w,y+h-3,fill);
        // Notched 45-degree corners, like the reference rather than plain rectangles.
        ctx.fill(x+3,y,x+w-3,y+1,edge);
        ctx.fill(x+3,y+h-1,x+w-3,y+h,edge);
        ctx.fill(x,y+3,x+1,y+h-3,edge);
        ctx.fill(x+w-1,y+3,x+w,y+h-3,edge);
        for(int i=1;i<=3;i++){
            ctx.fill(x+i,y+3-i,x+i+1,y+4-i,edge);
            ctx.fill(x+w-i-1,y+3-i,x+w-i,y+4-i,edge);
            ctx.fill(x+i,y+h-4+i,x+i+1,y+h-3+i,edge);
            ctx.fill(x+w-i-1,y+h-4+i,x+w-i,y+h-3+i,edge);
        }
        ctx.fill(x+4,y+2,x+w-4,y+3,0x385C75BC);
        ctx.fill(x+3,y+h-3,x+w-3,y+h-2,0x28232255);
    }

    static void div(DrawContext ctx,int x,int y,int w){
        ctx.fill(x,y,x+w,y+1,0xFF40365F);
        ctx.fill(x,y+1,x+w,y+2,0x354D4170);
    }

    static void softGlow(DrawContext ctx,int cx,int cy,int radius,int color){
        int red=(color>>16)&255,green=(color>>8)&255,blue=color&255;
        for(int i=radius;i>0;i-=5){
            int alpha=Math.max(1,Math.min(35,((radius-i+5)*21)/(radius+1)));
            int rgba=(alpha<<24)|(red<<16)|(green<<8)|blue;
            ctx.fill(cx-i,cy-i,cx+i,cy+i,rgba);
        }
    }

    static void titleStrip(DrawContext ctx,int x,int y,int w,int h){
        pixelFrame(ctx,x,y,w,h,0xF022173C,0xFF7D58A1,false);
        ctx.fill(x+4,y+4,x+w-4,y+5,0x75445391);
        ctx.fill(x+3,y+h-4,x+w-3,y+h-3,0x5A5B3E88);
    }

    static int rarity(String r){
        return switch(r){
            case "Common" -> 0xFFB8B8CE;
            case "Uncommon" -> 0xFF57F57B;
            case "Rare" -> 0xFF80AAFF;
            case "Epic" -> 0xFFE06BFA;
            case "Legendary" -> 0xFFFFBB41;
            case "Mythic" -> 0xFFFF7ECF;
            case "Unique" -> 0xFF7BE8F7;
            case "Special" -> 0xFFFF8F78;
            default -> DIM;
        };
    }

    static void banner(DrawContext ctx, int width, int height){
        int h=Math.max(29,height);
        for(int y=0;y<h;y++) {
            int red=19+(y*8)/h, green=12+(y*5)/h, blue=51+(y*4)/h;
            ctx.fill(0,y,width,y+1,0xFF000000|(red<<16)|(green<<8)|blue);
        }
        // Distant pixel stars, trees and a small glowing portal on the horizon.
        Random rng=new Random(0xB47A15);
        for(int i=0;i<Math.min(95,width/7);i++){
            int x=rng.nextInt(Math.max(1,width-2)),y=rng.nextInt(Math.max(1,h-8));
            int color=i%11==0?0xFFAFB6FF:0x655A69B6;
            ctx.fill(x,y,x+(i%13==0?2:1),y+1,color);
        }
        int moonX=(int)(width*0.88);
        softGlow(ctx,moonX,Math.max(9,h/3),9,0xFF76A8F9);
        ctx.fill(moonX-4,Math.max(3,h/3-4),moonX+4,Math.max(11,h/3+4),0xFFE5E5FF);
        for(int x=0;x<width;x+=8){
            int seed=(x*37+31) & 31;
            int peak=h-11-(seed%10);
            ctx.fill(x,peak,x+8,h,0xFF16102F);
            if(x%40==0)ctx.fill(x+2,peak-7,x+6,peak,0xFF1B1237);
            if(x%56==0)ctx.fill(x+2,peak+3,x+4,peak+5,0x99612CA1);
        }
        int portalX=(int)(width*0.72),portalTop=Math.max(8,h-29);
        softGlow(ctx,portalX,h-15,16,0xFFB839F9);
        ctx.fill(portalX-12,portalTop,portalX+12,h,0xFF261038);
        ctx.fill(portalX-8,portalTop+3,portalX+8,h-3,0xFF8C26B9);
        ctx.fill(portalX-5,portalTop+6,portalX+5,h-4,0xFFC14AE6);
        for(int i=0;i<8;i++){
            int off=(i*13)%11-5;
            ctx.fill(portalX+off,portalTop+5+(i*3)%(h-portalTop-7),portalX+off+1,
                portalTop+6+(i*3)%(h-portalTop-7),0xFFEFBAFF);
        }
        ctx.fill(0,h-2,width,h,0xAA362053);
    }

    static void paw(DrawContext ctx,int x,int y,int color){
        ctx.fill(x+4,y+9,x+16,y+17,color);
        ctx.fill(x+6,y+7,x+14,y+10,color);
        ctx.fill(x+1,y+4,x+5,y+9,color);
        ctx.fill(x+6,y,x+10,y+6,color);
        ctx.fill(x+12,y,x+16,y+6,color);
        ctx.fill(x+17,y+4,x+21,y+9,color);
        ctx.fill(x+7,y+15,x+13,y+19,color);
    }

    static void text(DrawContext ctx,TextRenderer font,String s,int x,int y,int color){
        ctx.drawText(font,s,x,y,color,false);
    }

    static void center(DrawContext ctx,TextRenderer font,String s,int cx,int y,int color){
        text(ctx,font,s,cx-font.getWidth(s)/2,y,color);
    }

    static String crop(TextRenderer font,String s,int max){
        if(s==null||max<8)return "";
        if(font.getWidth(s)<=max)return s;
        return font.trimToWidth(s,Math.max(0,max-font.getWidth("…")))+"…";
    }
}

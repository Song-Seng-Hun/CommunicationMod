package communicationmod.map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.map.*;
import com.megacrit.cardcrawl.screens.DungeonMapScreen;
import communicationmod.protocol.ProtocolSession;
import java.util.*;

/** Mouse-owned map overlay. Ink never becomes navigation input or wire geometry. */
public final class MapDrawing {
    private enum Mode {OFF,PEN,ROUTE,ERASER}
    private static Mode mode=Mode.OFF;
    private static boolean toolbarGesture,frameBlock;
    private static List<float[]> pending;
    private static String message="";
    private static long messageUntil;
    private static final Color INK=new Color(1f,0.72f,0.27f,0.94f),ROUTE=new Color(0.24f,0.89f,0.92f,0.94f);
    private static final Color PANEL=new Color(0.075f,0.09f,0.11f,0.94f),BUTTON=new Color(0.18f,0.20f,0.23f,1f);
    private MapDrawing(){}

    public static boolean visible() {
        return AbstractDungeon.screen==AbstractDungeon.CurrentScreen.MAP&&AbstractDungeon.player!=null
            &&!AbstractDungeon.player.isDead&&!CardCrawlGame.isPopupOpen&&!AbstractDungeon.isFadingOut
            &&AbstractDungeon.fadeColor.a<=0.01f&&!AbstractDungeon.player.viewingRelics
            &&(AbstractDungeon.topPanel==null||AbstractDungeon.topPanel.potionUi==null||AbstractDungeon.topPanel.potionUi.isHidden);
    }
    public static boolean blocksNavigation(){return visible()&&(mode!=Mode.OFF||toolbarGesture||frameBlock);}
    public static void close(){mode=Mode.OFF;toolbarGesture=false;frameBlock=false;pending=null;}
    public static void beforeUpdate(DungeonMapScreen screen) {
        frameBlock=false;
        try {
            MapPlanner.sync();
            if(!visible()){close();return;}
            float x=InputHelper.mX,y=InputHelper.mY;
            int button=buttonAt(x,y);
            boolean pressed=InputHelper.justClickedLeft,released=InputHelper.justReleasedClickLeft,down=InputHelper.isMouseDown;
            if(screen.clicked||communicationmod.patches.DungeonMapPatch.doBossHover||communicationmod.patches.MapRoomNodeHoverPatch.doHover) {
                close();return; // Never interrupt a navigation action already accepted by the game.
            }
            if(pressed&&button>=0) {
                finishStroke();toolbarGesture=true;frameBlock=true;
                if(button<3){Mode selected=Mode.values()[button+1];mode=mode==selected?Mode.OFF:selected;}
                else if(button==3)MapPlanner.INK.undo();else MapPlanner.INK.clear();
            }
            if(mode!=Mode.OFF||toolbarGesture)frameBlock=true;
            if(!toolbarGesture&&canvas(x,y)) {
                if(mode==Mode.PEN) {
                    if(pressed)pending=new ArrayList<>();
                    if(pending!=null&&(down||pressed||released))sample(x,y);
                } else if(mode==Mode.ROUTE&&pressed)MapPlanner.humanNode(MapPlanner.nearest(x,y));
                else if(mode==Mode.ERASER&&pressed) {
                    float[] p=MapAnnotations.toMap(x,y,Settings.WIDTH,Settings.scale,DungeonMapScreen.offsetY);
                    MapPlanner.INK.erase(p[0],p[1],18f);
                }
            }
            if(released||(!down&&!pressed)){finishStroke();toolbarGesture=false;}
            if(blocksNavigation())screen.clicked=false;
        } catch(IllegalArgumentException invalid) {
            pending=null;say(korean()?"연결된 노드만 선택할 수 있습니다. (또는 그리기 한도 도달)":"Choose connected nodes. Ink/route limits may be reached.");
        } catch(RuntimeException|LinkageError unavailable) {
            close();say(korean()?"지도 메모를 사용할 수 없습니다.":"Map annotation unavailable.");
        }
    }
    private static void sample(float x,float y) {
        if(pending.size()>=MapAnnotations.MAX_STROKE_POINTS||pending.size()+MapPlanner.INK.pointCount()>=MapAnnotations.MAX_POINTS)return;
        float[] p=MapAnnotations.toMap(x,y,Settings.WIDTH,Settings.scale,DungeonMapScreen.offsetY);
        if(!pending.isEmpty()){float[] last=pending.get(pending.size()-1);float dx=p[0]-last[0],dy=p[1]-last[1];if(dx*dx+dy*dy<4f)return;}
        pending.add(p);
    }
    private static void finishStroke() {if(pending==null)return;List<float[]> done=pending;pending=null;if(!done.isEmpty())MapPlanner.INK.commitStroke(done);}
    public static Map<String,Object> observation(){return MapPlanner.observation();}
    public static List<ProtocolSession.Action> actions(){return MapPlanner.actions();}
    public static void noteNode(MapRoomNode node){MapPlanner.noteNode(node);}
    public static void noteBoss(DungeonMap map){MapPlanner.noteBoss(map);}

    public static void render(SpriteBatch sb) {
        if(!visible()||ImageMaster.WHITE_SQUARE_IMG==null)return;
        Color previous=sb.getColor().cpy();
        try {
            MapPlanner.sync();float s=Settings.scale,scroll=DungeonMapScreen.offsetY,cx=Settings.WIDTH/2f;
            sb.setColor(INK);
            for(List<MapAnnotations.Point> stroke:MapPlanner.INK.strokes()) {
                MapAnnotations.Point last=stroke.get(0);
                for(MapAnnotations.Point p:stroke){line(sb,last.x*s+cx,last.y*s+scroll,p.x*s+cx,p.y*s+scroll,3*s,true);last=p;}
            }
            if(pending!=null&&!pending.isEmpty()) {
                float[] last=pending.get(0);
                for(float[] p:pending){line(sb,last[0]*s+cx,last[1]*s+scroll,p[0]*s+cx,p[1]*s+scroll,3*s,true);last=p;}
            }
            sb.setColor(ROUTE);float[] last=null;
            for(String node:MapPlanner.INK.route()) {
                float[] p=MapPlanner.position(node);
                if(p!=null&&last!=null){line(sb,last[0],last[1],p[0],p[1],5*s,true);arrow(sb,last,p,s);}
                if(p!=null&&canvas(p[0],p[1])) {
                    line(sb,p[0]-8*s,p[1],p[0],p[1]+8*s,3*s,true);line(sb,p[0],p[1]+8*s,p[0]+8*s,p[1],3*s,true);
                    line(sb,p[0]+8*s,p[1],p[0],p[1]-8*s,3*s,true);line(sb,p[0],p[1]-8*s,p[0]-8*s,p[1],3*s,true);
                }
                last=p;
            }
            toolbar(sb,s);
        } finally {sb.setColor(previous);}
    }
    private static void toolbar(SpriteBatch sb,float s) {
        float x=24*s,y=toolbarY(),width=82*s,gap=5*s;
        sb.setColor(PANEL);rect(sb,x-8*s,y-66*s,5*(width+gap)+11*s,118*s);
        String[] labels=korean()?new String[]{"펜","경로","지우개","되돌림","초기화"}:new String[]{"Pen","Route","Eraser","Undo","Clear"};
        int hovered=buttonAt(InputHelper.mX,InputHelper.mY);
        for(int i=0;i<5;i++) {
            float left=x+i*(width+gap);boolean active=i<3&&mode==Mode.values()[i+1];
            sb.setColor(active?ROUTE:hovered==i?BUTTON.cpy().mul(1.35f):BUTTON);rect(sb,left,y,width,42*s);
            if(i==0){sb.setColor(active?PANEL:INK);feather(sb,left+15*s,y+10*s,s);}
            FontHelper.renderFontCentered(sb,FontHelper.tipBodyFont,labels[i],left+width/2+(i==0?10*s:0),y+24*s,active?PANEL:Color.WHITE);
        }
        String hint=System.nanoTime()<messageUntil?message:korean()
            ?(mode==Mode.OFF?"펜: 손그림 | 경로: 노드 연결":"편집 중 | 휠 스크롤 가능")
            :(mode==Mode.OFF?"Pen: ink | Route: connected nodes":"Editing | Mouse wheel scrolls");
        FontHelper.renderFontLeftTopAligned(sb,FontHelper.tipBodyFont,hint,x,y-10*s,Color.WHITE);
        FontHelper.renderFontLeftTopAligned(sb,FontHelper.tipBodyFont,korean()?"도구 재선택: 종료 · 계획은 이동하지 않음":"Click active tool to finish. Plans never move you.",x,y-34*s,Color.WHITE);
    }
    private static void feather(SpriteBatch sb,float x,float y,float s) {
        line(sb,x,y,x+18*s,y+24*s,2*s,false);
        for(int i=0;i<5;i++){float t=i*3*s;line(sb,x+4*s+t,y+7*s+t,x+2*s+t,y+18*s+t,2*s,false);}
    }
    private static void arrow(SpriteBatch sb,float[] a,float[] b,float s) {
        float dx=b[0]-a[0],dy=b[1]-a[1],len=(float)Math.sqrt(dx*dx+dy*dy);if(len<2)return;
        dx/=len;dy/=len;float x=a[0]+(b[0]-a[0])*0.65f,y=a[1]+(b[1]-a[1])*0.65f;
        line(sb,x,y,x-12*s*dx+6*s*dy,y-12*s*dy-6*s*dx,3*s,true);
        line(sb,x,y,x-12*s*dx-6*s*dy,y-12*s*dy+6*s*dx,3*s,true);
    }
    private static void rect(SpriteBatch sb,float x,float y,float w,float h){sb.draw(ImageMaster.WHITE_SQUARE_IMG,x,y,w,h);}
    private static void line(SpriteBatch sb,float x,float y,float toX,float toY,float thickness,boolean clip) {
        // Clip to the map canvas, keeping strokes away from the top panel and toolbar.
        float dx=toX-x,dy=toY-y,t0=0,t1=1;
        if(clip) {
            float[] p={-dx,dx,-dy,dy},q={x,Settings.WIDTH-x,y-40*Settings.scale,toolbarY()-72*Settings.scale-y};
            for(int i=0;i<4;i++) {
                if(p[i]==0){if(q[i]<0)return;continue;}
                float t=q[i]/p[i];if(p[i]<0)t0=Math.max(t0,t);else t1=Math.min(t1,t);if(t0>t1)return;
            }
        }
        float endX=x+dx*t1,endY=y+dy*t1;x+=dx*t0;y+=dy*t0;dx=endX-x;dy=endY-y;
        float length=(float)Math.sqrt(dx*dx+dy*dy);
        Texture pixel=ImageMaster.WHITE_SQUARE_IMG;
        sb.draw(pixel,x,y-thickness/2,0,thickness/2,Math.max(thickness,length),thickness,1,1,
            (float)Math.toDegrees(Math.atan2(dy,dx)),0,0,pixel.getWidth(),pixel.getHeight(),false,false);
    }
    private static float toolbarY(){return Settings.HEIGHT-140*Settings.scale;}
    private static boolean canvas(float x,float y){return x>=0&&x<=Settings.WIDTH&&y>=40*Settings.scale&&y<toolbarY()-72*Settings.scale;}
    private static int buttonAt(float x,float y) {
        float s=Settings.scale;if(y<toolbarY()||y>toolbarY()+42*s||x<24*s)return -1;
        float slot=(x-24*s)/(87*s);int index=(int)slot;return index<5&&(slot-index)*87<=82?index:-1;
    }
    private static boolean korean(){return Settings.language==Settings.GameLanguage.KOR;}
    private static void say(String text){message=text;messageUntil=System.nanoTime()+3_000_000_000L;}
}

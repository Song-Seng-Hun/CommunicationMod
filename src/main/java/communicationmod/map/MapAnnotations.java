package communicationmod.map;

import java.util.*;
import java.util.function.*;

/** Bounded, game-thread-confined annotations. No gameplay, rendering, IO or hidden state. */
public final class MapAnnotations {
    public static final int MAX_ROUTE=64, MAX_STROKES=128, MAX_POINTS=8192, MAX_STROKE_POINTS=2048;
    private static final int MAX_UNDO=16;
    public static final class Point {
        public final float x,y;
        private Point(float x,float y){this.x=x;this.y=y;}
    }
    private static final class Snapshot {
        final List<List<Point>> strokes;
        final List<String> route;
        final String author;
        Snapshot(List<List<Point>> strokes,List<String> route,String author){this.strokes=strokes;this.route=route;this.author=author;}
    }
    private String mapId="",author="none";
    private long revision;
    private List<List<Point>> strokes=Collections.emptyList();
    private List<String> route=Collections.emptyList();
    private final Deque<Snapshot> history=new ArrayDeque<>();

    public void reset(String id) {
        if(id==null)throw new IllegalArgumentException("Map ID required");
        if(mapId.equals(id))return;
        mapId=id;strokes=Collections.emptyList();route=Collections.emptyList();history.clear();author="none";revision++;
    }
    public String mapId(){return mapId;}
    public long revision(){return revision;}
    public String routeAuthor(){return author;}
    public List<String> route(){return route;}
    public List<List<Point>> strokes(){return strokes;}
    public int strokeCount(){return strokes.size();}
    public boolean canUndo(){return !history.isEmpty();}
    public int pointCount(){int count=0;for(List<Point> s:strokes)count+=s.size();return count;}

    public void setRoute(List<String> candidate,String by,long expected,Predicate<String> known,BiPredicate<String,String> connected) {
        if(!("human".equals(by)||"mcp".equals(by)))throw new IllegalArgumentException("Invalid route author");
        List<String> copy=validateRoute(candidate,expected,known,connected);
        if(route.equals(copy))return;
        checkpoint();route=copy;author=by;revision++;
    }
    public List<String> validateRoute(List<String> candidate,long expected,Predicate<String> known,BiPredicate<String,String> connected) {
        if(expected!=revision)throw new IllegalArgumentException("Map plan revision changed");
        if(candidate==null || candidate.size()>MAX_ROUTE)throw new IllegalArgumentException("Invalid route");
        List<String> copy=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(String node:candidate) {
            if(node==null || node.length()>32 || !known.test(node) || !seen.add(node))throw new IllegalArgumentException("Unknown or duplicate map node");
            if(!copy.isEmpty() && !connected.test(copy.get(copy.size()-1),node))throw new IllegalArgumentException("Nodes are not joined by a map edge");
            copy.add(node);
        }
        return Collections.unmodifiableList(copy);
    }
    public String next(String current) {
        int index=route.indexOf(current);
        return index<0 || index+1>=route.size()?null:route.get(index+1);
    }
    public void commitStroke(List<float[]> input) {
        if(input==null || input.isEmpty() || input.size()>MAX_STROKE_POINTS
                || strokes.size()>=MAX_STROKES || input.size()+pointCount()>MAX_POINTS)throw new IllegalArgumentException("Ink limit reached");
        List<Point> points=new ArrayList<>();
        for(float[] p:input) {
            if(p==null || p.length!=2 || !finite(p[0]) || !finite(p[1]))throw new IllegalArgumentException("Invalid ink point");
            points.add(new Point(p[0],p[1]));
        }
        checkpoint();List<List<Point>> copy=new ArrayList<>(strokes);copy.add(Collections.unmodifiableList(points));
        strokes=Collections.unmodifiableList(copy);revision++;
    }
    public boolean erase(float x,float y,float radius) {
        if(!finite(x)||!finite(y)||!finite(radius)||radius<=0)throw new IllegalArgumentException("Invalid eraser");
        List<List<Point>> kept=new ArrayList<>();
        for(List<Point> stroke:strokes) {
            boolean hit=false;Point previous=stroke.get(0);
            for(Point p:stroke) {if(distanceSquared(x,y,previous,p)<=radius*radius){hit=true;break;}previous=p;}
            if(!hit)kept.add(stroke);
        }
        if(kept.size()==strokes.size())return false;
        checkpoint();strokes=Collections.unmodifiableList(kept);revision++;return true;
    }
    public void undo() {
        if(history.isEmpty())return;
        Snapshot previous=history.removeLast();strokes=previous.strokes;route=previous.route;author=previous.author;revision++;
    }
    public void clear() {
        if(strokes.isEmpty()&&route.isEmpty())return;
        checkpoint();strokes=Collections.emptyList();route=Collections.emptyList();author="none";revision++;
    }
    private void checkpoint() {
        if(history.size()==MAX_UNDO)history.removeFirst();
        history.addLast(new Snapshot(strokes,route,author));
    }
    private static boolean finite(float n){return !Float.isNaN(n)&&!Float.isInfinite(n)&&Math.abs(n)<1000000f;}
    private static float distanceSquared(float x,float y,Point a,Point b) {
        float dx=b.x-a.x,dy=b.y-a.y,length=dx*dx+dy*dy;
        float t=length==0?0:Math.max(0,Math.min(1,((x-a.x)*dx+(y-a.y)*dy)/length));
        float px=x-(a.x+t*dx),py=y-(a.y+t*dy);return px*px+py*py;
    }
    public static float[] toMap(float x,float y,float width,float scale,float scroll) {
        if(!finite(scale)||scale<=0)throw new IllegalArgumentException("Invalid display scale");
        return new float[]{(x-width/2)/scale,(y-scroll)/scale};
    }
    public static float[] toScreen(float x,float y,float width,float scale,float scroll) {
        if(!finite(scale)||scale<=0)throw new IllegalArgumentException("Invalid display scale");
        return new float[]{x*scale+width/2,y*scale+scroll};
    }
}

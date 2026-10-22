package cn.g.space;

/** One timer per drag, independent of how often pointer location events arrive. */
public final class DragAutoScroll implements Runnable {
 public interface Host {
  void post(Runnable task,int delayMs);void remove(Runnable task);
  boolean valid();int height();int edge();int maxStep();void scrollBy(int delta);
 }
 private final Host host;private float y;private boolean running;
 public DragAutoScroll(Host host){this.host=host;}
 public void move(float pointerY){
  y=pointerY;
  // Updating the pointer must never postpone a pending frame.
  if(!running){running=true;host.post(this,32);}
 }
 public void stop(){running=false;host.remove(this);}
 public void run(){
  if(!running)return;if(!host.valid()){stop();return;}
  int step=GridDropTarget.scroll(y,host.height(),host.edge(),host.maxStep());
  if(step!=0)host.scrollBy(step);
  if(running)host.post(this,32);
 }
}

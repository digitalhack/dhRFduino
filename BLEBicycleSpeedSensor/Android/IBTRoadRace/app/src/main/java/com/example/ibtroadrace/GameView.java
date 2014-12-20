package com.example.ibtroadrace;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class GameView extends SurfaceView implements SurfaceHolder.Callback {
    private String TAG = "GameView";

    private Game game;
    private GameThread gameThread;
    private Context mContext;

    public GameView(Context context) {
        super(context);

        gameViewInit(context);
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);

        gameViewInit(context);
    }

    public GameView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        gameViewInit(context);
    }

    private void gameViewInit(Context context) {
        Log.v(TAG, "gameViewInit");
        mContext = context;
        // We can now safely start the ibtroadrace loop.
        // Focus must be on GamePanel so that events can be handled.
        this.setFocusable(true);
        // For intercepting events on the surface.
        this.getHolder().addCallback(this);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated");
        startGame();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    private void startGame(){
        game = new Game(mContext, getWidth(), getHeight(), getResources());
        game.start();

        gameThread = new GameThread(this.getHolder(), game);

        gameThread.running = true;
        gameThread.start();
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "surfaceDestroyed");
        gameThread.running = false;
        game.stop();

        // Shut down the ibtroadrace loop thread cleanly.
        boolean retry = true;
        while(retry) {
            try {
                gameThread.join();
                retry = false;
            } catch (InterruptedException e) {}
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // This is for single-touch. For multi-touch use MotionEventCompat.getActionMasked(event);
        int action = event.getAction();

        if(action == MotionEvent.ACTION_DOWN){
            game.touchEvent_actionDown(event);
        }

        if(action == MotionEvent.ACTION_MOVE) {
            game.touchEvent_actionMove(event);
        }

        if(action == MotionEvent.ACTION_UP){
            game.touchEvent_actionUp(event);
        }

        return true;
    }
}

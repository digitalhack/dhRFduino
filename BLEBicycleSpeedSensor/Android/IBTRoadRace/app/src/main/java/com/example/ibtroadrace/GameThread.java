package com.example.ibtroadrace;

        import android.graphics.Canvas;
        import android.util.Log;
        import android.view.SurfaceHolder;

public class GameThread extends Thread {
    private String TAG = "GameThread";

    // How many times per second the ibtroadrace should be updated, drawn?
    private final static int MAX_FPS = 30;
    // Maximum number of frames to be skipped
    private final static int MAX_FRAME_SKIPS = 5;
    // The frame period
    private final static int FRAME_PERIOD = 1000 / MAX_FPS;

    // Surface holder that can access the physical surface.
    private SurfaceHolder surfaceHolder;

    private Game game;

    // Elapsed ibtroadrace time in milliseconds.
    private long gameTime;

    // Holds the state of the ibtroadrace loop.
    public boolean running;

    public GameThread(SurfaceHolder surfaceHolder, Game game) {
        super();

        Log.v(TAG, "Constructor");

        this.surfaceHolder = surfaceHolder;
        this.game = game;

        this.gameTime = 0;
    }

    @Override
    public void run() {
        Canvas canvas;

        long beginTime;		// the time when the cycle begun
        long timeDiff;		// the time it took for the cycle to execute
        int sleepTime;		// ms to sleep (<0 if we're behind)
        int framesSkipped;	// number of frames being skipped

        sleepTime = 0;

        while(running){
            canvas = null;

            try {
                // Try locking the canvas for exclusive pixel editing in the surface.
                canvas = this.surfaceHolder.lockCanvas();
                synchronized(surfaceHolder) {
                    beginTime = System.currentTimeMillis();
                    framesSkipped = 0;	// Resetting the frames skipped.


                    this.game.update(this.gameTime);
                    this.game.draw(canvas);


                    // Calculate how long did the cycle take.
                    timeDiff = System.currentTimeMillis() - beginTime;
                    // Calculate sleep time.
                    sleepTime = (int)(FRAME_PERIOD - timeDiff);

                    if (sleepTime > 0) {
                        try {
                            // Send the thread to sleep for a short period.
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    while (sleepTime < 0 && framesSkipped < MAX_FRAME_SKIPS) {
                        // We need to catch up, so we update without drawing the ibtroadrace to the screen.
                        this.game.update(this.gameTime);
                        // Add FRAME_PERIOD to check while condition again.
                        sleepTime += FRAME_PERIOD;
                        framesSkipped++;
                    }

                    this.gameTime += System.currentTimeMillis() - beginTime;
                }
            } catch(Exception e) {
                e.printStackTrace();
            } finally {
                // In case of an exception the surface is not left in an inconsistent state.
                if (canvas != null) {
                    surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }
}
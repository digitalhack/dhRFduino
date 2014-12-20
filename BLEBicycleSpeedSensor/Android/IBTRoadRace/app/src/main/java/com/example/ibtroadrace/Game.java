package com.example.ibtroadrace;

/*
20141118

Increased road width to 500 from 300.
Moved racer back.
Decreased speed factor from 2 to 1.5
Made heading from -2 to 2 straight.
Implemented time delay for new hazards after crash.  Set initially to 100 * roadRacer.speed

Added buttons
Change LCD font
Added heading to speedometer

20141119

Fixed starts and stops so now they start and stop the application correctly.
Put the speed factor back to 2.

20141123

Added Reset, Hazard Off, Timer logic
Added Timer and Score to surface view.

---Future---

Add button to remove hazards for normal riding.
Count down timer

*/

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import java.util.Random;

public class Game  {
    private String TAG = "Game";

    private class spriteClass {
        public Bitmap image;
        public int width;
        public int height;
        public int center;
        public int middle;
        public float speed;
        public float posX;
        public float posY;
        public boolean stopped;
        public boolean visible;
        public int points;
    }

    public static int screenWidth;
    public static int screenHeight;
    public static float screenDensity;
    public static Context context;

    private int gameTimeSec;

    Bitmap myCanvasBitmap = null;
    Canvas myCanvas = null;
    Matrix identityMatrix;

    float roadWidth;
    float roadLeft;
    float roadRight;
    float roadCenter;
    float ssWidth;
    int dashLength;

    float screenCenter;

    float startOffset;

    int crashPhase;

    int dashStart;

    spriteClass roadRacer;
    spriteClass crashedRacer;
    spriteClass[] roadHazard;

    int maxHazard = 1;
    int hcnt;

    float nextScore;

    int crashNot = 1;
    int crashJust = 2;
    int crashRecover = 3;
    int crashOver = 4;

    // Bike Data
    int cnt, oldCnt, cntAtReset;
    int mpr, oldMpr;
    long rideTime;
    float speed;
    float miles;
    int headingStraight;
    int tHeading, oldTHeading;
    int score;

    int crashStart;
    int skipHazards;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    HeadingSensor heading;
    Speedometer speedometer;
    boolean gameSetupComplete = false;
    boolean simulateSpeedometer = false;
    long lastCntMillis = 0;

    private double wheelfactor1;
    private double wheelfactor2;

    boolean resetGame = false;
    boolean hazardsOff = false;
    boolean saveHazardsOff = false;
    boolean scoreOff = false;
    boolean initCountDown = false;
    boolean preCountDown = false;
    boolean countDown = false;
    boolean postCountDown = false;
    boolean doneCountDown = false;
    long countDownEnd = 0;
    int countDownTimer = 0;
    boolean scoreDone = false;

    int countDownScore = 0;

    ToneGenerator beep;

    Paint textPaint;
    Paint smallTextPaint;

    int cntdwn = 0;

    public Game(Context context, int screenWidth, int screenHeight, Resources resources){
        Log.v(TAG, "Constructor");

        Game.screenWidth = screenWidth;
        Game.screenHeight = screenHeight;
        Game.screenDensity = resources.getDisplayMetrics().density;
        Game.context = context;

        this.init(resources);
    }

    private void init(Resources resources){
        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(150);
        textPaint.setTypeface(MainActivity.tfLCD);

        smallTextPaint = new Paint();
        smallTextPaint.setColor(Color.BLACK);
        smallTextPaint.setTextSize(60);
        smallTextPaint.setTypeface(MainActivity.tfLCD);

        myCanvasBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
        myCanvas = new Canvas();
        myCanvas.setBitmap(myCanvasBitmap);

        identityMatrix = new Matrix();

        roadRacer = new spriteClass();
        roadRacer.image = BitmapFactory.decodeResource(resources, R.drawable.rider);

        roadRacer.width = roadRacer.image.getWidth();
        roadRacer.height = roadRacer.image.getHeight();
        roadRacer.center = roadRacer.width / 2;
        roadRacer.middle = roadRacer.height / 2;

        crashedRacer = new spriteClass();
        crashedRacer.image = BitmapFactory.decodeResource(resources, R.drawable.crash);

        crashedRacer.width = crashedRacer.image.getWidth();
        crashedRacer.height = crashedRacer.image.getHeight();
        crashedRacer.center = crashedRacer.width / 2;
        crashedRacer.middle = crashedRacer.height / 2;

        roadHazard = new spriteClass[1];

        for (hcnt=0; hcnt<maxHazard; hcnt++) {
            roadHazard[hcnt] = new spriteClass();
            roadHazard[hcnt].image = BitmapFactory.decodeResource(resources, R.drawable.oil);
            roadHazard[hcnt].width = roadHazard[hcnt].image.getWidth();
            roadHazard[hcnt].height = roadHazard[hcnt].image.getHeight();
            roadHazard[hcnt].center = roadHazard[hcnt].width / 2;
            roadHazard[hcnt].middle = roadHazard[hcnt].height / 2;
            roadHazard[hcnt].visible = false;
            roadHazard[hcnt].posX = 0;
            roadHazard[hcnt].posY = 0;
            roadHazard[hcnt].points = -5;
        }

        roadWidth = 500;
        ssWidth = 20;
        dashLength = 200;

        roadCenter = screenCenter = screenWidth / 2f;

        roadLeft = screenCenter - roadWidth / 2f;
        roadRight = screenCenter + roadWidth / 2f;

        MainActivity.button1.setText("Reset");
        MainActivity.button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetGame = true;
            }
        });

        MainActivity.button2.setText("Haz: Off");
        hazardsOff = false;
        MainActivity.button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hazardsOff) {
                    MainActivity.button2.setText("Haz: Off");
                    setHazards(true);
                } else {
                    MainActivity.button2.setText("Haz: On");
                    setHazards(false);
                }
            }
        });

        MainActivity.button3.setText("");
        MainActivity.button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "button3 pressed");
            }
        });

        MainActivity.button4.setText("");
        MainActivity.button4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "button4 pressed");
            }
        });

        MainActivity.button5.setText("Timer");
        MainActivity.button5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initCountDown = true;
                Log.v(TAG, "button5 pressed");
            }
        });

        beep = new ToneGenerator(AudioManager.STREAM_ALARM, 100);

        if (heading == null) heading = new HeadingSensor(context);
        if (speedometer == null) speedometer = new Speedometer(context);
    }

    public void start() {
        Log.v(TAG, "start");
        if (heading != null) heading.start();
        if (speedometer != null) speedometer.start();
    }

    public void stop () {
        Log.v(TAG, "stop");
        if (heading != null) heading.stop();
        if (speedometer != null) speedometer.stop();
    }

    private void setup() {
        Log.v(TAG, "setup");

        SharedPreferences mySharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);

        String my_wheelcircumference =
                mySharedPreferences.getString("wheelcircumference_setting", "2163");

        wheelfactor1 = 2.2369356 * Integer.parseInt(my_wheelcircumference);
        wheelfactor2 = 0.000000621371 * Integer.parseInt(my_wheelcircumference);

        String my_countdowntimer =
                mySharedPreferences.getString("countDownTimer", "30");
        countDownTimer = Integer.parseInt(my_countdowntimer);

        Log.v(TAG, String.format("Wheel Factor 1: %6.3f Wheel Factor 2: %6.3f Cnt Dwn Timer: %3d", wheelfactor1, wheelfactor2, countDownTimer));

        dashStart = 0;

        startOffset = 0;
        crashPhase = crashNot;

        roadRacer.stopped = false;
        roadRacer.posY = ((float)screenHeight / 2f) + 350f;
        roadRacer.posX = roadCenter;

        rideTime = 0;
        oldCnt = -1;
        oldTHeading = 0;
        miles = 0;
        score = 0;
        setScoring(true);

        headingStraight = heading.getHeading();
        if (!simulateSpeedometer) {
            cntAtReset = speedometer.getCnt();
            speedometer.resetMprTotal();
        } else {
            cnt = 0;
            cntAtReset = cnt;
        }

        resetGame = false;
        gameSetupComplete = true;
        countDown = false;
    }

    public void update(long gameTime) {
        this.gameTimeSec = (int) (gameTime / 1000);

        if (resetGame) {
            gameSetupComplete = false;
        }

        if (!gameSetupComplete) {
            if (!speedometer.getSetupComplete()) {
                if (gameTimeSec < 5) {
                    return;
                }
                simulateSpeedometer = true;
            }
            if (heading.getHeading() == 999) {
                return;
            }
            setup();
        }

        if (!simulateSpeedometer) {
            mpr = speedometer.getMpr();
            cnt = speedometer.getCnt() - cntAtReset;
        } else {
            mpr = 800;
            if (lastCntMillis + mpr < System.currentTimeMillis()) {
                cnt++;
                lastCntMillis = System.currentTimeMillis();
            }
        }

        if (mpr == -1) {
            speed = 0;
        } else {
            speed = (float) wheelfactor1 / mpr;
        }

        if (mpr > 0 && oldCnt != cnt) {
            rideTime = mpr + rideTime;
        }

        miles = (float) (cnt * wheelfactor2);

        cntdwn = 0;

        // Begin Countdown Section

        if (initCountDown) {
            saveHazardsOff = hazardsOff;
            setHazards(false);
            countDownEnd = System.currentTimeMillis() + 5000;
            initCountDown = false;
            preCountDown = true;
        } else if (preCountDown) {
            cntdwn = (int) ((countDownEnd - System.currentTimeMillis()) / 1000) + 1;
            if (System.currentTimeMillis() > countDownEnd) {
                preCountDown = false;
                countDown = true;
                countDownEnd = System.currentTimeMillis() + ((countDownTimer) * 1000);
                setHazards(true);
                beep.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
            }
        } else if (countDown) {
            cntdwn = (int) ((countDownEnd - System.currentTimeMillis()) / 1000) + 1;
            if (System.currentTimeMillis() > countDownEnd) {
                countDown = false;
                postCountDown = true;
                countDownScore = score;
                setHazards(false);
                countDownEnd = System.currentTimeMillis() + 5000;
                beep.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
                scoreDone = false;
                showTheScore(context);
            }
        } else if (postCountDown) {
            if (scoreDone) {
                postCountDown = false;
                setHazards(!saveHazardsOff);
            }
        }

        // End Countdown Section

        if (mpr != oldMpr || cnt != oldCnt || tHeading != oldTHeading) {
            speedometer.updateSpeedometer(speed, miles, rideTime, score, tHeading);
            oldMpr = mpr;
            oldCnt = cnt;
            oldTHeading = tHeading;
        }

        if (!roadRacer.stopped) {
            roadRacer.speed = (int) (speed * 2);
            dashStart = (int) startOffset % dashLength;
            startOffset += roadRacer.speed;

            tHeading = heading.getHeading() - headingStraight;
            if (tHeading < 0) {
                tHeading = tHeading + 360;
            }
            if (tHeading > 180) {
                tHeading = tHeading - 360;
            }

            if (tHeading < -1) {
                tHeading += 1;
            } else if (tHeading > 1) {
                tHeading += -1;
            } else {
                tHeading = 0;
            }

            roadRacer.posX = ((tHeading / 10f) * roadRacer.speed) + roadRacer.posX;

            if (skipHazards > 0 || hazardsOff) {
                if (!hazardsOff) {
                    skipHazards -= roadRacer.speed;
                }
            } else {
                for (hcnt = 0; hcnt < maxHazard; hcnt++) {
                    if (!roadHazard[hcnt].visible) {
                        roadHazard[hcnt].posY = 0;
                        Random r = new Random();
                        roadHazard[hcnt].posX = (r.nextFloat() * (roadRight - roadHazard[hcnt].center -
                                roadLeft)) + roadLeft + roadHazard[hcnt].center;
                        roadHazard[hcnt].visible = true;
                    } else {
                        roadHazard[hcnt].posY += roadRacer.speed;
                        if (roadHazard[hcnt].posY > screenHeight + (roadHazard[hcnt].height / 2)) {
                            roadHazard[hcnt].posY = 0;
                            roadHazard[hcnt].posX = 0;
                            roadHazard[hcnt].visible = false;
                        }
                    }
                    if ((roadHazard[hcnt].posX + roadHazard[hcnt].center < roadRacer.posX - 5) ||
                            (roadHazard[hcnt].posX - roadHazard[hcnt].center > roadRacer.posX + 5) ||
                            (roadHazard[hcnt].posY + roadHazard[hcnt].middle < roadRacer.posY -
                                    roadRacer.middle) ||
                            (roadHazard[hcnt].posY - roadHazard[hcnt].middle > roadRacer.posY +
                                    roadRacer.middle)) {
                    } else {
                        crashPhase = crashJust;
                        roadRacer.stopped = true;
                        if (!scoreOff) score += roadHazard[hcnt].points;
                        roadHazard[hcnt].posY = 0;
                        roadHazard[hcnt].posX = 0;
                        roadHazard[hcnt].visible = false;
                    }
                }
            }

            if ((roadRacer.posX < roadLeft + 2) || (roadRacer.posX > roadRight + 2)) {
                crashPhase = crashJust;
                roadRacer.stopped = true;
                if (!scoreOff) {
                    nextScore = miles + 100f;
                    score += -10;
                }
            }
        } else {
            if (crashPhase == crashOver) {
                roadRacer.stopped = false;
                roadRacer.posX = roadCenter;
                crashPhase = crashNot;
                if (!hazardsOff) {
                    for (hcnt = 0; hcnt < maxHazard; hcnt++) {
                        roadHazard[hcnt].posY = 0;
                        roadHazard[hcnt].posX = 0;
                        roadHazard[hcnt].visible = false;
                    }
                    skipHazards = (int) (roadRacer.speed * 100f);
                }
                if (!scoreOff) nextScore = miles + .01f;
            }
        }

        if (crashPhase == crashNot) {
            if (miles >= nextScore) {
                if (!scoreOff) {
                    score += 5;
                    nextScore = miles + .01f;
                }
            }
        }

        statusText("Game is running: " + gameTimeSec);
    }

    public void draw(Canvas canvas) {
        if (! gameSetupComplete) {
            return;
        }
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth(3);
        paint.setAntiAlias(true);
        paint.setColor(Color.argb(255, 23, 173, 3));
        myCanvas.drawRect(0, 0, screenWidth, screenHeight, paint);

        paint.setColor(Color.argb(255, 140, 140, 140));
        myCanvas.drawRect(roadLeft, 0, roadLeft + roadWidth, screenHeight, paint);

        paint.setStrokeWidth(ssWidth);
        paint.setColor(Color.BLACK);
        myCanvas.drawLine(roadLeft - ssWidth / 2, 0, roadLeft - ssWidth / 2, screenHeight, paint);
        myCanvas.drawLine(roadRight + ssWidth / 2, 0, roadRight + ssWidth / 2, screenHeight, paint);

        paint.setColor(Color.argb(255, 255, 255, 0));
        for (int i = dashStart - dashLength; i < screenHeight; i = i + dashLength) {
            myCanvas.drawLine(roadCenter, i, roadCenter, i + dashLength / 2, paint);
        }

        for (hcnt = 0; hcnt < maxHazard; hcnt++) {
            if (roadHazard[hcnt].visible)
                myCanvas.drawBitmap(roadHazard[hcnt].image,
                        roadHazard[hcnt].posX - roadHazard[hcnt].center,
                        roadHazard[hcnt].posY - roadHazard[hcnt].middle, null);
        }
        if (crashPhase == crashNot) {
            myCanvas.drawBitmap(roadRacer.image, roadRacer.posX - roadRacer.center,
                    roadRacer.posY - roadRacer.middle, null);
        } else {
            myCanvas.drawBitmap(crashedRacer.image, roadRacer.posX - crashedRacer.center,
                    roadRacer.posY - crashedRacer.middle, null);
            if (crashPhase == crashJust) {
                //crashedRacer.sound.play();
                crashStart = gameTimeSec;
                crashPhase = crashRecover;

            } else {
                //if (!crashedRacer.sound.getIsPlaying()) crashPhase = crashOver;
                if (crashStart + 3 < gameTimeSec) crashPhase = crashOver;
            }
        }

        if (countDown || preCountDown) {
            myCanvas.drawText("Score", 10 + 60, 60, smallTextPaint);
            myCanvas.drawText(String.format("%3d", score), 10, 200, textPaint);

            myCanvas.drawText("Timer", screenWidth - 300 + 60, 60, smallTextPaint);
            myCanvas.drawText(String.format("%3d", cntdwn), screenWidth - 300, 200, textPaint);
            //Log.v(TAG, String.format("screenWidth: %d screenHeight %d", screenWidth, screenHeight));
        }

        if (canvas != null) {
            canvas.drawBitmap(myCanvasBitmap, identityMatrix, null);
        }
    }

    public static void statusText (final String msg) {
        Handler handler = new Handler (Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                MainActivity.tvStatus.setText(msg);
            }
        });
    }

    public void touchEvent_actionDown(MotionEvent event){

    }

    public void touchEvent_actionMove(MotionEvent event){

    }

    public void touchEvent_actionUp(MotionEvent event){

    }

    private void setScoring(boolean scoreFlag) {
        if (scoreFlag) {
            scoreOff = false;
            nextScore = miles + .01f;
            score = 0;
        } else {
            scoreOff = true;
            score = 0;
        }
    }

    private void setHazards(boolean hazardFlag) {
        if (hazardFlag) {
            hazardsOff = false;
            setScoring(true);
        } else {
            hazardsOff = true;
            for (hcnt=0; hcnt<maxHazard; hcnt++) {
                roadHazard[hcnt].visible = false;
            }
            setScoring(false);
        }
    }

    private void showTheScore(final Context context) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                AlertDialog.Builder showScoreDialogBuilder = new AlertDialog.Builder(context);
                showScoreDialogBuilder.setTitle("Your Score");
                TextView tvScore = new TextView(context);
                tvScore.setText(String.format("\n\n%d\n\n\n", countDownScore));
                tvScore.setGravity(Gravity.CENTER);
                tvScore.setTextSize(100);
                tvScore.setTypeface(MainActivity.tfLCD);

                showScoreDialogBuilder.setView(tvScore);

                // set positive button: Yes message
                showScoreDialogBuilder.setPositiveButton("OK",new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        scoreDone = true;
                    }
                });

                AlertDialog showScore = showScoreDialogBuilder.create();

                // show alert
                showScore.show();
            }
        });
    }
}
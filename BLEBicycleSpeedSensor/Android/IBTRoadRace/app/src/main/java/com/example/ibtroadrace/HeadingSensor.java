package com.example.ibtroadrace;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class HeadingSensor implements SensorEventListener {
    private String TAG = "HeadingSensor";
    private SensorManager mSensorManager;

    private float[] mOrientation = new float[9];

    private volatile float mHeading = 0;
    private volatile int mHeadingSmooth = 0;
    private int initCnt = 0;

    private float mPitch;

    private Sensor mRotationVectorSensor;
    private final float[] mRotationMatrix = new float[16];

    private volatile int old = 0;
    private int MAXINITCNT = 30;

    public HeadingSensor(Context context) {
        // Get an instance of the SensorManager
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        // find the rotation-vector sensor
        mRotationVectorSensor = mSensorManager.getDefaultSensor(
                Sensor.TYPE_ROTATION_VECTOR);

        // initialize the rotation matrix to identity
        mRotationMatrix[ 0] = 1;
        mRotationMatrix[ 4] = 1;
        mRotationMatrix[ 8] = 1;
        mRotationMatrix[12] = 1;
    }

    public void start() {
        // enable our sensor when the activity is resumed, ask for
        // 30 ms updates.
        Log.v(TAG, "start");
        mSensorManager.registerListener(this, mRotationVectorSensor, 30000);
    }

    public void stop() {
        // make sure to turn our sensor off when the activity is paused
        Log.v(TAG, "stop");
        mSensorManager.unregisterListener(this);
    }

    public void onSensorChanged(SensorEvent event) {
        SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
        SensorManager.remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_X,
                SensorManager.AXIS_Z, mRotationMatrix);
        SensorManager.getOrientation(mRotationMatrix, mOrientation);

        mHeading = (float) Math.toDegrees(mOrientation[0]);
        mPitch = (float) Math.toDegrees(mOrientation[1]);

        mHeading = mod(mHeading, 360.0f);

        if (initCnt < MAXINITCNT) {
            if (mHeadingSmooth == 0) { mHeadingSmooth = Math.round(mHeading); }
            initCnt++;
        }

        mHeadingSmooth = (Math.round(mHeading) + (mHeadingSmooth))/2;
/*
        if (old != mHeadingSmooth) {
            Log.v(TAG, String.format("%3d", mHeadingSmooth));
            old = mHeadingSmooth;
        }
*/
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private float mod(float a, float b) {
        return (a % b + b) % b;
    }

    public int getHeading() {
        if (initCnt < MAXINITCNT) {
            return 999;
        } else {
            return mHeadingSmooth;
        }
    }
}
package com.example.yh.trailharvester2;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Environment;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.w3c.dom.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.regex.Matcher;


public class MainActivity extends AppCompatActivity implements SensorEventListener
{

    private TextView xText, yText, zText, moving, north, status, phoneAngle;
    private EditText filenameByUser;
    private Sensor myAccelerometer, myOrientation, myLinAccelerometer;
    private SensorManager SM;
    private Button startBtn, stopBtn, saveBtn, drawBtn;
    private ProgressDialog myProgress;
    private Canvas canvas;
    private long lastUpdateTime = 0 , currentTime;
    boolean btnFlag = false;
    boolean timeFlag = false;
    //For file writing
    String fileName;
    FileWriter fWriter;
    File file;
    //Array matrix for calculation orientation
    private float[] mGravity = new float[3];
    private float[] mGeomagnetic = new float[3];
    private float[] mAcceleration = new float[3];
    float[] Inc = new float[9];
    float[] Rot = new float[9];
    float[] orientation = new float[3];
    float azimuth, pitch;
    boolean computeFlag;


    //interval of the harvest in ms
    int intervalTime = 20;
    //LPF coefficient, the greater the smoother
    final float alpha = 0.97f;
    final float alpha2 = 0.8f;
    //Threshold for acceleration to be used by peakDetector
    final float threshold =0.24f;
    //Declaring ArrayList for storing all sensor data
    List<DataPoint> data;
    List<MotionVector> motionVector;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //Setup accelerometer Sensor
        SM = (SensorManager)getSystemService(SENSOR_SERVICE);
        myAccelerometer = SM.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        SM.registerListener(this, myAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);

        //Setup linear accelerometer sensor without Gravity
        myLinAccelerometer = SM.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        SM.registerListener(this, myLinAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);

        //Setup orientation sensor
        myOrientation = SM.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        SM.registerListener(this, myOrientation, SensorManager.SENSOR_DELAY_FASTEST);

        //Setup text
        xText = (TextView)findViewById(R.id.display1);
        yText = (TextView)findViewById(R.id.display2);
        zText = (TextView)findViewById(R.id.display3);
        moving = (TextView)findViewById(R.id.moving);
        north = (TextView)findViewById(R.id.display7);
        phoneAngle = (TextView)findViewById(R.id.display8);
        status = (TextView)findViewById(R.id.status);

        //Setup Edit Text
        filenameByUser = (EditText)findViewById(R.id.filenameField);

        //Configure button
        startBtn = (Button)findViewById(R.id.startBtn);
        stopBtn = (Button)findViewById(R.id.stopBtn);
        saveBtn = (Button)findViewById(R.id.saveBtn);
        drawBtn = (Button)findViewById(R.id.drawBtn);

        //Progress dialog for peakDetector
        myProgress = new ProgressDialog(this);

        //setting action of start button
        startBtn.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v)
            {

                btnFlag = true;
                status.setText("Status: Recording Accelerometer Data");
                status.setTextColor(Color.GREEN);
                //create path that will save to the phone storage
                fileName = filenameByUser.getText().toString();
                File saveDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Accelerometer");
                file = new File(saveDirectory, fileName + ".csv");
                data = new ArrayList<>();
                motionVector = new ArrayList<>();

            }
        });

        //setting action of stop button
        stopBtn.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                btnFlag = false;
                status.setText("Status: Recording Stopped");
                status.setTextColor(Color.RED);
            }
        });

        //save the array from buffer to csv of phone internal memory
        saveBtn.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                status.setText("Status: Saving To Csv File");
                try
                {
                    myProgress.setMessage("Peak Detection in Progress...");
                    myProgress.show();
                    //perform peakDetect
                    peakDetector(data);
                    //instantiate a Motion vector list
                    trapezoidalSum(data, motionVector);
                    Intent myIntent = new Intent(MainActivity.this, DrawVector.class);
                    //myIntent.putParcelableArrayListExtra("motionVector", motionVector);
                    startActivity(myIntent);

                    fWriter = new FileWriter(file, true);

                    for(int j = 0; j < data.size(); j++ )
                    {
                        fWriter.write(data.get(j).time + ", " +
                                data.get(j).yAcceleration + ", " +
                                data.get(j).threshold + ", " +
                                -data.get(j).threshold + ", " +
                                data.get(j).status + "\n ");
                    }
                    fWriter.flush();
                    fWriter.close();
                    //empty List to prepare for nex application
                    data.clear();
                    //data = new ArrayList<>(); // this ways fails to renew the array because the size is not reset.
                    // It still crash for the second time save without restart the apps

                }catch(java.io.IOException e)
                {
                    e.printStackTrace();
                }
                status.setText("Status: Saving Completed");
                status.setTextColor(Color.BLUE);
                toast("Trail Harvested !");
            }
        }
        );

        drawBtn.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                Intent myIntent = new Intent(MainActivity.this, DrawVector.class);
                startActivity(myIntent);
            }
        });

    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        //retrieve and update sensor data
        synchronized (this)
        {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            {
                //LPF and save to array
                mGravity[0] = alpha*mGravity[0] + (1-alpha)*event.values[0];
                mGravity[1] = alpha*mGravity[1] + (1-alpha)*event.values[1];
                mGravity[2] = alpha*mGravity[2] + (1-alpha)*event.values[2];

            }else if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION)
            {
                mAcceleration[0] = alpha2*mAcceleration[0] + (1-alpha2)*event.values[0];
                mAcceleration[1] = alpha2*mAcceleration[1] + (1-alpha2)*event.values[1];
                mAcceleration[2] = event.values[2];
                xText.setText("X: " + mAcceleration[0]);
                yText.setText("Y: " + mAcceleration[1]);
                zText.setText("Z: " + mAcceleration[2]);
                //check the walking state
                if(mAcceleration[1] > 0.6)
                {
                    moving.setTextColor(Color.GREEN);
                }else
                {
                    moving.setTextColor(Color.BLACK);
                }

                //LPF

                //take a copy of linear accelerometer value to mAcceleration array
                //System.arraycopy(event.values, 0, mAcceleration, 0, 3);
            }
            else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            {
                //LPF and save to array
                mGeomagnetic[0] = alpha*mGeomagnetic[0] + (1-alpha)*event.values[0];
                mGeomagnetic[1] = alpha*mGeomagnetic[1] + (1-alpha)*event.values[1];
                mGeomagnetic[2] = alpha*mGeomagnetic[2] + (1-alpha)*event.values[2];
            }
            computeFlag = SM.getRotationMatrix(Rot, Inc, mGravity, mGeomagnetic);
            if (computeFlag)
            {
                SM.getOrientation(Rot, orientation);
                azimuth = (float)Math.toDegrees(orientation[0]);
                pitch = (float)Math.toDegrees(orientation[1]);
                north.setText("Azimuth(deg from North): " + azimuth);
                //check phone is perpendicular to ground
                if(Math.abs(pitch) < 1.3)
                {
                    phoneAngle.setTextColor(Color.GREEN);
                }else
                {
                    phoneAngle.setTextColor(Color.BLACK);
                }
            }
        }

        currentTime = System.currentTimeMillis();

        //setting time interval receiving data
        if ((currentTime - lastUpdateTime) > intervalTime)
        {
            timeFlag = true;
            lastUpdateTime = currentTime;
        }
        if (btnFlag && timeFlag)
        {
            //constructor for DataPoint Object with new arguments
            DataPoint tempPoint = new DataPoint(currentTime, mAcceleration[1], azimuth, threshold);
            data.add(tempPoint);
            //reset time flag
            timeFlag = false;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
        // not in use
    }

    // Toast function
    public void toast(String s)
    {
        Toast.makeText(getBaseContext(), s, Toast.LENGTH_SHORT).show();
    }

    //Declare class obj for storing time, y-acceleration and azimuth
    public class DataPoint
    {
        private long time;
        private float yAcceleration, direction, threshold;
        private int status = 0;

        private DataPoint(long t, float y, float d, float th)
        {
            time = t;
            yAcceleration =y;
            direction = d;
            threshold = th;
        }

        private void setHigh()
        {
            status = 1;
        }
    }

    public class MotionVector
    {
        private double distance, direction, x, y;

        private MotionVector(float dist, float dir)
        {
            //convert float to double
            this.distance = Double.parseDouble(Float.toString(dist));
            this.direction = Double.parseDouble(Float.toString(dir));
            //change the polar to cartesian
            this.x = distance * Math.sin(direction);
            this.y = distance * Math.cos(direction);
        }
    }

    //take average of 5 points and set high for exceeding threshold
    public void peakDetector(List<DataPoint> e)
    {
        int startCount, lastCount;
        int totalCount = e.size();
        float sum = 0, average = 0;
        DataPoint tempData;

        if(totalCount > 6)
        {
            startCount = 2;
            lastCount = totalCount - 3;
            //average and check a set of 5 points
            for (int i = startCount ; i <= lastCount ; i ++)
            {
                //this is direct test without using 5 points avg
                if(Math.abs(e.get(i).yAcceleration) > threshold)
                {
                    e.get(i).setHigh();
                }

                //this is using 5 points average
                /*for (int j = i-2 ; j <= i+2 ; j++)
                {
                    tempData = data.get(j);
                    sum += tempData.yAcceleration;
                }
                //check if the absolute avg is greater than threshold
                if(Math.abs(sum/5) > threshold)
                {
                    tempData = data.get(i);
                    tempData.setHigh();
                    data.set(i, tempData);
                    sum = 0;
                }*/
            }
        }else
        {
            toast("Insufficient Data Count !");
        }

        myProgress.cancel();

    }

    public void trapezoidalSum(List<DataPoint> e, List<MotionVector> e2)
    {
        long timeInterval;
        float avgAcceleration, initV=0, finV=0, distanceAccumulated=0, tempD1=0, tempD2, sumAzimuth=0;
        int pulseChecker, count=0;
        boolean flag=false;

        //iterate through all points
        for(int i = 0 ; i < e.size()-1 ; i++)
        {
            pulseChecker = e.get(i+1).status - e.get(i).status;
            if(e.get(i).status == 1 && e.get(i+1).status == 1)
            {
                timeInterval = (e.get(i+1).time - e.get(i).time)/1000; //millisec to sec
                avgAcceleration = (e.get(i+1).yAcceleration + e.get(i).yAcceleration)/2;
                finV = initV + avgAcceleration*timeInterval; //latch
                distanceAccumulated += initV*timeInterval + 0.5f*avgAcceleration*timeInterval*timeInterval; //latch
                initV = finV; //swap final and initial velocity
            }
            switch (pulseChecker)
            {
                case 1 :
                    tempD1 = distanceAccumulated;
                    flag = true;
                    break;
                case -1 :
                    tempD2 = distanceAccumulated;
                    flag = false;
                    //create a motion vector
                    MotionVector MV = new MotionVector(tempD2-tempD1,sumAzimuth/count);
                    e2.add(MV);
                    //reset the accumulating param
                    count = 0;
                    sumAzimuth = 0;
                    break;
                case 0 :
                    break;
            }
            while(flag)//start accumulate azimuth for average purpose
            {
                sumAzimuth += e.get(i).direction;
                count++;
            }

        }
    }



}



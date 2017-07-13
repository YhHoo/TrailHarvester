package com.example.yh.trailharvester2;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;

public class DrawVector extends AppCompatActivity
{

    Canvas c;
    Paint paintForCentre, paintForLines;
    Button drawBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_draw_vector);

        drawBtn = (Button)findViewById(R.id.drawBtn);
        final ImageView imgCircle = (ImageView)findViewById(R.id.imageView);

        drawBtn.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {

                //Set paint for starting point
                paintForCentre = new Paint();
                paintForCentre.setColor(Color.BLUE);
                paintForCentre.setStyle(Paint.Style.FILL_AND_STROKE);
                //set paint for lines
                paintForLines = new Paint();
                paintForLines.setColor(Color.RED);
                paintForLines.setStyle(Paint.Style.STROKE);

                //create Bitmap
                Bitmap b = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888);

                //Canvas
                Canvas canvas = new Canvas(b);
                //draw a small circle at centre point
                canvas.drawCircle(b.getWidth()/2, b.getHeight()/2, 5, paintForCentre);
                canvas.drawLine(b.getWidth()/2,b.getHeight()/2, 200, 200, paintForLines);
                canvas.drawLine(200,200,150,170,paintForLines);
                canvas.drawLine(150,170,300,120,paintForLines);

                //output the circle
                imgCircle.setImageBitmap(b);


            }
        });



    }

}

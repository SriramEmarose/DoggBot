package com.rex.doggybot;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class MainActivity extends CameraActivity implements CvCameraViewListener2 {
    private static final String TAG = "DoggyBot";

    private CameraBridgeViewBase mOpenCvCameraView;

    private boolean isRedObjectDetected = false;

    Mat gFrameRGB = new Mat();
    Mat gFrameHSV = new Mat();
    Mat segmentedFrame = new Mat();

    Point centroidToFollow = new Point(0,0);

    private int currRobotState = 0;

    Scalar lb_red = new Scalar(160, 50, 50);
    Scalar ub_red = new Scalar(180, 255, 255);

    private static final String ROBOT_IP = "192.168.0.104";
    private static final int ROBOT_PORT = 80;

    private static final int MOVE_ROBOT_FORWARD       = 2;
    private static final int MOVE_ROBOT_REVERSE       = 1;
    private static final int MOVE_ROBOT_RIGHT         = 3;
    private static final int MOVE_ROBOT_LEFT          = 4;
    private static final int MOVE_ROBOT_SLIGHT_RIGHT  = 5;
    private static final int MOVE_ROBOT_SLIGHT_LEFT   = 6;
    private static final int STOP_ROBOT               = 7;
    private static final int CMD_ACK                  = 8;
    private static final int INCORRECT_CMD_ACK        = 9;

    // Comms
    InetAddress serverAddr;
    Socket socket = null;
    PrintWriter cmdOut;


    static {
        OpenCVLoader.initDebug();
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.cam_surface_view);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        mOpenCvCameraView.setCvCameraViewListener(this);

        moveRobotToObject();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
        CloseRobotComms();
    }

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame)
    {
        getRedObjectCentroid(inputFrame.rgba());

        Mat vis = inputFrame.rgba().clone();
        Imgproc.circle(vis, centroidToFollow, 9, new Scalar(0,255,0),-1);
        String coords = String.valueOf(centroidToFollow.x) + " , " + String.valueOf(centroidToFollow.y);
        Imgproc.putText(vis, coords, new Point(100,100), Imgproc.FONT_HERSHEY_SCRIPT_SIMPLEX, 1.0,
                new Scalar(0, 255, 0), 1, Imgproc.LINE_AA, false);

        moveRobotToObject();
        return vis;
    }

    public void getRedObjectCentroid(Mat frame)
    {
        Imgproc.cvtColor(frame, gFrameRGB, Imgproc.COLOR_RGBA2BGR);
        Imgproc.cvtColor(gFrameRGB, gFrameHSV, Imgproc.COLOR_BGR2HSV);

        Core.inRange(gFrameHSV, lb_red, ub_red, segmentedFrame);

        final Mat se =Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(11,11));
        Imgproc.erode(segmentedFrame, segmentedFrame, se);
        Imgproc.dilate(segmentedFrame, segmentedFrame, se);

        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(segmentedFrame, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

        if(contours.size() == 0)
        {
            centroidToFollow.x = 0;
            centroidToFollow.y = 0;
            isRedObjectDetected = false;
        }

        for (MatOfPoint contour : contours) {
            MatOfPoint2f areaPoints = new MatOfPoint2f(contour.toArray());
            RotatedRect boundingRect = Imgproc.minAreaRect(areaPoints);

            double bwArea = boundingRect.size.area();

            if (bwArea > 1000 && bwArea < 300000) {
                Point rotated_rect_points[] = new Point[4];
                boundingRect.points(rotated_rect_points);
                Rect roi = Imgproc.boundingRect(new MatOfPoint(rotated_rect_points));

                centroidToFollow.x = roi.x + (roi.width/2);
                centroidToFollow.y = roi.y + (roi.height/2);

                isRedObjectDetected = true;
            }
            else
            {
                centroidToFollow.x = 0;
                centroidToFollow.y = 0;
                isRedObjectDetected = false;
            }
        }
    }

    public void moveRobotToObject()
    {
        try
        {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try{
                        while(true)
                        {
                            if(isRedObjectDetected == true)
                            {
                                System.out.println("RedObjectDetected");
                                int w = gFrameRGB.cols();
                                int h = gFrameRGB.rows();

                                int ROBOT_CENTROID_X = w/2;
                                int ROBOT_CENTROID_Y = h/2;

                                if(centroidToFollow.x == 0 || centroidToFollow.y ==  0)
                                    SendCmdToRobot(String.valueOf(STOP_ROBOT));

                                else if(centroidToFollow.x < ROBOT_CENTROID_X)
                                {
                                     SendCmdToRobot(String.valueOf(MOVE_ROBOT_FORWARD));
                                     Thread.sleep(500);
                                }
                                else if(centroidToFollow.y < ROBOT_CENTROID_Y)
                                {
                                    SendCmdToRobot(String.valueOf(MOVE_ROBOT_LEFT));
                                    Thread.sleep(500);
                                }
                                else if(centroidToFollow.y > ROBOT_CENTROID_Y)
                                {
                                    SendCmdToRobot(String.valueOf(MOVE_ROBOT_RIGHT));
                                    Thread.sleep(500);
                                }
                            }
                            else
                            {
                                System.out.println("Writing to STOP_ROBOT");
                                SendCmdToRobot(String.valueOf(STOP_ROBOT));
                                //break;
                            }
                            Thread.sleep(500);
                        }
                    }
                    catch(Exception ex){
                        // tvHeading.setText(ex.toString());
                        System.out.println(ex.toString());
                    }
                }
            });
            thread.start();

        }
        catch(Exception e)
        {
            System.out.println(e.toString());
        }
    }

    public void ConnectToRobot()
    {
        try {
            serverAddr = InetAddress.getByName(ROBOT_IP);
            socket = new Socket(serverAddr, ROBOT_PORT);
            cmdOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
        }
        catch (Exception e)
        {
            System.out.println(e.toString());
        }
    }

    public void CloseRobotComms()
    {
        try {
            cmdOut.flush();
            cmdOut.close();
            socket.close();
        }
        catch (Exception e)
        {
            System.out.println(e.toString());
        }
    }

    public void SendCmdToRobot(String cmd)
    {
        int nextState = Integer.parseInt(cmd);

        if(currRobotState != nextState)
        {
            currRobotState = nextState;

            if(socket == null)
            {
                ConnectToRobot();
            }

            System.out.println("Writing to Robot" + nextState);

            try {
                cmdOut.println(cmd);
            }
            catch (Exception e)
            {
                System.out.println(e.toString());
            }
        }
    }
}

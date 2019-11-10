package cultoftheunicorn.marvel;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.cultoftheunicorn.marvel.R;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

public class Recognize extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static DatagramSocket socket = null;
    private static final int SendPort = 22222;
    private static final int MSG_SEND_DATA = 1001;
    private static int MSG_SELECT = 1000;
    private static final int SEND_ALARM = 1002;
    private static final int SEND_HELLO = 1003;

    private static final String    TAG                 = "OCVSample::Activity";
    private static final Scalar FACE_RECT_COLOR     = new Scalar(0, 255, 0, 255);
    public static final int        JAVA_DETECTOR       = 0;
    public static final int        NATIVE_DETECTOR     = 1;

    public static boolean flag=true;

    public static final int SEARCHING= 1;
    public static final int IDLE= 2;

    private static final int frontCam =1;
    private static final int backCam =2;


    private int faceState=IDLE;


    private Mat                    mRgba;
    private Mat                    mGray;
    private File mCascadeFile;
    private CascadeClassifier mJavaDetector;

    private int                    mDetectorType       = JAVA_DETECTOR;
    private String[]               mDetectorName;

    private float                  mRelativeFaceSize   = 0.2f;
    private int                    mAbsoluteFaceSize   = 0;
    private int mLikely=999;

    String mPath="";

    private Tutorial3View   mOpenCvCameraView;

    private ImageView Iv;
    Bitmap mBitmap;
    Handler mHandler;

    PersonRecognizer fr;
    ToggleButton scan;

    Set<String> uniqueNames = new HashSet<String>();

    // max number of people to detect in a session
    String[] uniqueNamesArray = new String[10];

    static final long MAXIMG = 10;

    Labels labelsFile;
    static {
        OpenCVLoader.initDebug();
        System.loadLibrary("opencv_java");
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    fr=new PersonRecognizer(mPath);
                    String s = getResources().getString(R.string.Straininig);
                    //Toast.makeText(getApplicationContext(),s, Toast.LENGTH_LONG).show();
                    fr.load();

                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                        mOpenCvCameraView.enableView();

                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;


            }
        }
    };

    public Recognize() {
        mDetectorName = new String[2];
        mDetectorName[JAVA_DETECTOR] = "Java";
        mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";

        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognize);
        Button submit = (Button) findViewById(R.id.submit);

        scan = (ToggleButton) findViewById(R.id.scan);
        final TextView results = (TextView) findViewById(R.id.results);

        mOpenCvCameraView = (Tutorial3View) findViewById(R.id.tutorial3_activity_java_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);

        //mPath=getFilesDir()+"/facerecogOCV/";
        mPath = Environment.getExternalStorageDirectory()+"/facerecogOCV/";

        Log.e("Path", mPath);

        labelsFile= new Labels(mPath);

        //输入IP地址以及端口号
        final EditText IP_add=(EditText)findViewById(R.id.IP_add);

        final ImageButton mCotrolIb = (ImageButton) findViewById(R.id.imageButton2);
        mCotrolIb.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
               if(flag == false) {
                   mCotrolIb.setVisibility(View.INVISIBLE);
                   flag = true;
               }
               else{
                   mCotrolIb.setVisibility(View.GONE);
                   flag = false;
               }
            }
        });


        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                MSG_SELECT = SEND_HELLO;
                mainHandler.sendEmptyMessage(MSG_SEND_DATA);
                results.setText("message had send");
                if(flag == false) {
                    mCotrolIb.setVisibility(View.VISIBLE);
                    flag = true;
                }
                else{
                    mCotrolIb.setVisibility(View.INVISIBLE);
                    flag = false;
                }
            }


            Handler mainHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    switch (msg.what) {
                        case MSG_SEND_DATA:
                            sendUDPMessage(System.currentTimeMillis() + "");
                            break;
                        default:
                            break;
                    }
                }
            };
            private InetAddress mAddress;
            private DatagramSocket mSocket = null;

            private int mSendPort = 22222;
            private byte[] mSendBuf;
            public void sendUDPMessage(final String msg) {
                final String mBroadCastIp = IP_add.getText().toString();
                // 初始化socket
                try {
                    mSocket = new DatagramSocket();
                } catch (SocketException e) {
                    e.printStackTrace();
                }
                try {
                    mAddress = InetAddress.getByName(mBroadCastIp);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }

                ThreadPoolManager.getInstance().startTaskThread(new Runnable() {
                    @Override
                    public void run() {
                        String sendData = "data_init";
                        if (MSG_SELECT == SEND_ALARM)
                        {
                            sendData = "target is Liu De hua";
                        }
                        else
                        {
                            sendData = "hello";
                        }
                        byte mSendBuf[] = sendData.getBytes();
                        DatagramPacket recvPacket1 = new DatagramPacket(mSendBuf, mSendBuf.length, mAddress, mSendPort);
                        try {
                            mSocket.send(recvPacket1);
                            mSocket.close();
                            Log.e(TAG, "sendUDPMessage msg：" + msg);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }, "");
            }

            private  void sendMessage()
            {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            String sendData = "data_init";
                            if (MSG_SELECT == SEND_ALARM)
                            {
                                sendData = "target is Liu De hua";
                            }
                            else
                            {
                                sendData = "hello";
                            }
                            byte data[] = sendData.getBytes();
                            Log.d(TAG,"run:发送消息");
                            DatagramPacket packet = new DatagramPacket(data,data.length,mAddress,SendPort);
                            socket.send(packet);
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        });



        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                /*
                    display a newline separated list of individual names
                 */
                String tempName = msg.obj.toString();
                if (!(tempName.equals("Unknown"))) {
                    tempName = capitalize(tempName);
                    String uniqueNames = tempName;
                    StringBuilder strBuilder = new StringBuilder();
                    String textToDisplay = tempName;
                    String target = "Boss";
                    if (textToDisplay.equals(target))
                    {
//                        textToDisplay = textToDisplay.concat("danger");
                        results.setText("target is coming");
//                        results.setText(textToDisplay);
                        MSG_SELECT = SEND_ALARM;
                        mainHandler.sendEmptyMessage(MSG_SEND_DATA);
                    }
                    else
                    {
                        results.setText(textToDisplay);
                    }
                }
                else{
                    results.setText("Not target");
                }
            }
            Handler mainHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    switch (msg.what) {
                        case MSG_SEND_DATA:
                            sendUDPMessage(System.currentTimeMillis() + "");
                            break;
                        default:
                            break;
                    }
                }
            };
            private InetAddress mAddress;
            private DatagramSocket mSocket = null;

            private int mSendPort = 22222;
            private byte[] mSendBuf;
            public void sendUDPMessage(final String msg) {
                final String mBroadCastIp = IP_add.getText().toString();
                // 初始化socket
                try {
                    mSocket = new DatagramSocket();
                } catch (SocketException e) {
                    e.printStackTrace();
                }
                try {
                    mAddress = InetAddress.getByName(mBroadCastIp);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }

                ThreadPoolManager.getInstance().startTaskThread(new Runnable() {
                    @Override
                    public void run() {
                        String sendData = "data_init";
                        if (MSG_SELECT == SEND_ALARM)
                        {
                            sendData = "danger";
                        }
                        else
                        {
                            sendData = "hello";
                        }
                        byte mSendBuf[] = sendData.getBytes();
                        DatagramPacket recvPacket1 = new DatagramPacket(mSendBuf, mSendBuf.length, mAddress, mSendPort);
                        try {
                            mSocket.send(recvPacket1);
                            mSocket.close();
                            Log.e(TAG, "sendUDPMessage msg：" + msg);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }, "");
            }

            private  void sendMessage()
            {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            String sendData = "data_init";
                            if (MSG_SELECT == SEND_ALARM)
                            {
                                sendData = "danger";
                            }
                            else
                            {
                                sendData = "hello";
                            }
                            byte data[] = sendData.getBytes();
                            Log.d(TAG,"run:发送消息");
                            DatagramPacket packet = new DatagramPacket(data,data.length,mAddress,SendPort);
                            socket.send(packet);
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        };

        scan.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b) {
                    if(!fr.canPredict()) {
                        scan.setChecked(false);
                        Toast.makeText(getApplicationContext(), getResources().getString(R.string.SCanntoPredic), Toast.LENGTH_LONG).show();
                        return;
                    }
                    faceState = SEARCHING;
                }
                else {
                    faceState = IDLE;
                }
            }
        });

        boolean success=(new File(mPath)).mkdirs();
        if (!success)
        {
            Log.e("Error","Error creating directory");
        }

        Button trainingButton = (Button) findViewById(R.id.training);

        trainingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Recognize.this, NameActivity.class));
            }
        });

    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }

        MatOfRect faces = new MatOfRect();

        if (mDetectorType == JAVA_DETECTOR) {
            if (mJavaDetector != null)
                mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        }
        else if (mDetectorType == NATIVE_DETECTOR) {
            /*if (mNativeDetector != null)
                mNativeDetector.detect(mGray, faces);*/
        }
        else {
            Log.e(TAG, "Detection method is not selected!");
        }

        Rect[] facesArray = faces.toArray();

        if ((facesArray.length>0) && (faceState==SEARCHING))
        {
            Mat m=new Mat();
            m=mGray.submat(facesArray[0]);
            mBitmap = Bitmap.createBitmap(m.width(),m.height(), Bitmap.Config.ARGB_8888);

            Utils.matToBitmap(m, mBitmap);
            Message msg = new Message();
            String textTochange = "IMG";
            msg.obj = textTochange;
            //mHandler.sendMessage(msg);

            textTochange = fr.predict(m);
            mLikely=fr.getProb();
            msg = new Message();
            msg.obj = textTochange;
            mHandler.sendMessage(msg);
        }
        for (int i = 0; i < facesArray.length; i++)
            Core.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);

        return mRgba;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

//    because capitalize is the new black
    private String capitalize(final String line) {
        return Character.toUpperCase(line.charAt(0)) + line.substring(1);
    }
}

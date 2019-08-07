package il.ac.huji.cs.cv.cvdropcounter;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;


public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final int CAMERA_PERMISSIONS_REQ = 100;

    private JavaCameraView cameraView;
    private TextView viewModeTv;
    private static final int VIEW_MODE_RGBA   = 0;
    private static final int VIEW_MODE_GRAY   = 1;
    private static final int VIEW_MODE_CANNY  = 2;
    private static final int VIEW_MODE_FEATURES = 3;
    private int mViewMode = VIEW_MODE_FEATURES;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        viewModeTv = findViewById(R.id.mode_tv);
        changeMode(VIEW_MODE_FEATURES);

        cameraView = findViewById(R.id.cameraView);
        cameraView.setVisibility(SurfaceView.VISIBLE);
        cameraView.setCvCameraViewListener(this);

        viewModeTv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                changeMode((mViewMode + 1) % (VIEW_MODE_FEATURES + 1));
                return true;
            }
        });
    }

    private void changeMode(int i) {
        mViewMode = i;
        String modeName = "";
        switch (i) {
            case VIEW_MODE_RGBA:
                modeName = "RGBA";
                break;
            case VIEW_MODE_GRAY:
                modeName = "GRAY";
                break;
            case VIEW_MODE_CANNY:
                modeName = "CANNY";
                break;
            case VIEW_MODE_FEATURES:
                modeName = "FEATURES";
                break;
        }
        viewModeTv.setText(modeName);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, CAMERA_PERMISSIONS_REQ);
        } else {
            initCVLoader();
        }
    }

    private void initCVLoader() {
        if (OpenCVLoader.initDebug()) {
            //cameraView.setMaxFrameSize(1280, 720);
            cameraView.setVisibility(View.VISIBLE);
            cameraView.setEnabled(true);
            cameraView.enableView();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Can't load")
                    .setCancelable(false)
                    .setPositiveButton("Okay", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .create().show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CAMERA_PERMISSIONS_REQ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initCVLoader();
            } else {
                finish();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraView != null) {
            cameraView.disableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraView != null) {
            cameraView.disableView();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat rgba = inputFrame.rgba();
        //Mat gray = inputFrame.gray();
        Imgproc.cvtColor(rgba, rgba, Imgproc.COLOR_RGBA2GRAY);
        Core.flip(rgba, rgba, 1);
        return rgba;
    }

}

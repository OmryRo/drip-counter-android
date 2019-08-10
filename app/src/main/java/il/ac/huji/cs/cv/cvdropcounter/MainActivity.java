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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final int CAMERA_PERMISSIONS_REQ = 100;

    private BetterCameraView cameraView;
    private TextView viewModeTv;
    private EditText cannyLowTv;
    private EditText cannyHighTv;
    private CheckBox areaFilterCb;
    private Spinner modeSelector;

    private static final int VIEW_MODE_FEATURES   = 0;
    private static final int VIEW_MODE_CANNY   = 1;
    private static final int VIEW_MODE_NUM = 2;

    private static final int DEBUG_MODE_ALL = 0;
    private static final int DEBUG_MODE_ONLY_CHAMBER = 1;
    private static final int DEBUG_MODE_ONLY_DROPS = 2;

    private int selectedMode = DEBUG_MODE_ONLY_DROPS;
    private int mViewMode = 0;

    ChamberDetector chamberDetector;
    DropDetection dropDetection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        chamberDetector = new ChamberDetector();
        dropDetection = new DropDetection();

        setControllers();

        cameraView = findViewById(R.id.cameraView);
        cameraView.setVisibility(SurfaceView.VISIBLE);
        cameraView.setCvCameraViewListener(this);
        cameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraView.setFocus();
            }
        });

    }

    private void setControllers() {
        cannyLowTv = findViewById(R.id.cannylow);
        cannyLowTv.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && s.length() > 0) {
                    try {
                        chamberDetector.setCannyLow(Integer.valueOf(s.toString()));
                    } catch (NumberFormatException e) {}
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        cannyLowTv.setText(String.valueOf(ChamberDetector.DEFAULT_CANNY_LOW));

        cannyHighTv = findViewById(R.id.cannyhigh);
        cannyHighTv.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && s.length() > 0) {
                    try {
                        chamberDetector.setCannyHigh(Integer.valueOf(s.toString()));
                    } catch (NumberFormatException e) {}
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        cannyHighTv.setText(String.valueOf(ChamberDetector.DEFAULT_CANNY_HIGH));

        viewModeTv = findViewById(R.id.mode_tv);
        changeMode(VIEW_MODE_FEATURES);
        viewModeTv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                changeMode((mViewMode + 1) % (VIEW_MODE_NUM));
                return true;
            }
        });

        areaFilterCb = findViewById(R.id.filterarea);
        areaFilterCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                chamberDetector.setFilterArea(isChecked);
            }
        });
        areaFilterCb.setChecked(ChamberDetector.DEFAULT_FILTER_AREA);

        modeSelector = findViewById(R.id.modespinner);
        ArrayList<String> modeList = new ArrayList<>();
        modeList.add("ALL");
        modeList.add("CHAMBER");
        modeList.add("DROPS");
        modeSelector.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, modeList));
        modeSelector.setSelection(selectedMode);
        modeSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedMode = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedMode = DEBUG_MODE_ALL;
            }
        });
    }

    private void changeMode(int i) {
        mViewMode = i;
        String modeName = "";
        switch (i) {
            case VIEW_MODE_FEATURES:
                modeName = "FEATURES";
                break;
            case VIEW_MODE_CANNY:
                modeName = "CANNY";
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
        chamberDetector.onCameraViewStarted(width, height);
    }

    @Override
    public void onCameraViewStopped() {
        chamberDetector.onCameraViewStopped();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        Mat gray = inputFrame.gray();
        Mat dst = inputFrame.rgba();

        try {

            if (selectedMode != DEBUG_MODE_ONLY_DROPS) {
                chamberDetector.detectDropChamber(dst, gray);
            }

            if (selectedMode != DEBUG_MODE_ONLY_CHAMBER) {
                dropDetection.detectDrops(dst, gray);
            }

        } catch (Exception e) {
            Log.e("bla", "onCameraFrame: ", e);
        }

        switch (mViewMode) {
            case VIEW_MODE_CANNY:
                Mat canny = chamberDetector.getCanny();

                return canny == null ? dst : canny;
            default:
                return dst;
        }
    }


}

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

    private static final int MAX_TIMES_TILL_RESET = 10;
    private static final int MAX_DISTANCE_TO_IGNORE = 500;

    private int cannyLow = 15;
    private int cannyHigh = 25;
    private boolean filterArea = true;
    private int selectedMode = DEBUG_MODE_ONLY_CHAMBER;

    private int mViewMode = 0;
    private Mat dsIMG, usIMG, bwIMG, cIMG, hovIMG;
    private MatOfPoint2f approxCurve;
    private Scalar[] scalarsG;
    private Scalar[] scalarsB;

    private Point centerFirst;
    private Point centerSecond;
    private int timesTillReset;

    private Point[] dropChamberArea;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
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
        cannyLowTv.setText(String.valueOf(cannyLow));
        cannyLowTv.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && s.length() > 0) {
                    try {
                        cannyLow = Integer.valueOf(s.toString());
                    } catch (NumberFormatException e) {}
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        cannyHighTv = findViewById(R.id.cannyhigh);
        cannyHighTv.setText(String.valueOf(cannyHigh));
        cannyHighTv.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && s.length() > 0) {
                    try {
                        cannyHigh = Integer.valueOf(s.toString());
                    } catch (NumberFormatException e) {}
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

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
        areaFilterCb.setChecked(filterArea);
        areaFilterCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                filterArea = isChecked;
            }
        });

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

        dsIMG = new Mat();
        usIMG = new Mat();
        bwIMG = new Mat();
        cIMG = new Mat();
        hovIMG = new Mat();
        approxCurve = new MatOfPoint2f();
        scalarsG = new Scalar[] {
                new Scalar(0,255,0,255),
                new Scalar(0,235,0,235),
                new Scalar(0,215,0,215),
                new Scalar(0,195,0,195),
                new Scalar(0,175,0,175),
                new Scalar(0,155,0,155),
                new Scalar(0,135,0,135),
                new Scalar(0,115,0,115),
                new Scalar(0,95,0,95),
                new Scalar(0,75,0,75),
                new Scalar(0,55,0,55)
        };
        scalarsB = new Scalar[] {
                new Scalar(0,0,255,255),
                new Scalar(0,0,235,235),
                new Scalar(0,0,215,215),
                new Scalar(0,0,195,195),
                new Scalar(0,0,175,175),
                new Scalar(0,0,155,155),
                new Scalar(0,0,135,135),
                new Scalar(0,0,115,115),
                new Scalar(0,0,95,95),
                new Scalar(0,0,75,75),
                new Scalar(0,0,55,55)
        };

        centerFirst = new Point(0,0);
        centerSecond = new Point(0,0);
        timesTillReset = 0;
    }

    @Override
    public void onCameraViewStopped() {
        dsIMG = null;
        usIMG = null;
        bwIMG = null;
        cIMG = null;
        hovIMG = null;
        approxCurve = null;
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        Mat gray = inputFrame.gray();
        Mat dst = inputFrame.rgba();

        try {

            if (selectedMode != DEBUG_MODE_ONLY_DROPS) {
                detectDropChamber(dst, gray);
            }

            if (selectedMode != DEBUG_MODE_ONLY_CHAMBER) {
                detectDrops(dst, gray);
            }

        } catch (Exception e) {
            Log.e("bla", "onCameraFrame: ", e);
        }

        switch (mViewMode) {
            case VIEW_MODE_CANNY:
                return cIMG == null ? dst : cIMG;
            default:
                return dst;
        }
    }

    private Mat detectDropChamber(Mat dst, Mat gray) {
        Imgproc.pyrDown(gray, dsIMG, new Size(gray.cols() / 2, gray.rows() / 2));
        Imgproc.pyrUp(dsIMG, usIMG, gray.size());
        Imgproc.Canny(usIMG, bwIMG, cannyLow, cannyHigh);
        Imgproc.dilate(bwIMG, bwIMG, new Mat(), new Point(-1, 1), 1);

        cIMG = bwIMG.clone();

        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(cIMG, contours, hovIMG, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        List<Object[]> found = new ArrayList<>();

        for (MatOfPoint cnt : contours) {

            MatOfPoint2f curve = new MatOfPoint2f(cnt.toArray());
            Imgproc.approxPolyDP(curve, approxCurve, 0.02 * Imgproc.arcLength(curve, true), true);
            int numberVertices = (int) approxCurve.total();

            // we don't have what to do with lines and tingles
            if (numberVertices <= 3) {
                continue;
            }

            double contourArea = Math.abs(Imgproc.contourArea(cnt));

            boolean inSize = contourArea > 6000 && contourArea < 20000;

            if (!inSize && filterArea) {
                continue;
            }

            Point[] points = approxCurve.toArray();
            Object[] results = new Object[] {cnt, curve, numberVertices, contourArea, points};
            found.add(results);
        }

        ArrayList<Point> centers = new ArrayList<>();
        for (Object[] result : found) {
            MatOfPoint cnt = (MatOfPoint) result[0];
            MatOfPoint2f curve = (MatOfPoint2f) result[1];
            int numberVertices = (int) result[2];
            double contourArea = (double) result[3];
            Point[] points = (Point[]) result[4];

            double centerX = 0;
            double centerY = 0;
            for (Point point : points) {
                centerX += point.x;
                centerY += point.y;
            }
            Point center = new Point(centerX / numberVertices, centerY / numberVertices);
            centers.add(center);

            for (int j = 0; j < numberVertices; j++) {
                Point pt0 = points[j];
                Point pt1 = points[(j + 1) % numberVertices];
                Imgproc.line(dst, pt0, pt1, (true ? scalarsG : scalarsB)[0], 3);
            }


            Mat overlay = new Mat();
            dst.copyTo(overlay);
            Imgproc.fillConvexPoly(overlay, cnt, (true ? scalarsG : scalarsB)[0]);
            Imgproc.circle(overlay, center, 10, scalarsB[0], Imgproc.FILLED);
            Core.addWeighted(overlay, 0.4, dst, 0.6, 0, dst);

            setLabel(dst, String.format("%.2f,%d", contourArea, numberVertices), cnt, 3, (true ? scalarsG : scalarsB)[0]);
        }

        boolean isCloseToCenter = false;
        if (centers.size() == 2) {

            Point point0 = centers.get(0);
            Point point1 = centers.get(1);

            Imgproc.line(dst, point0, point1, scalarsG[0], 6);

            if (timesTillReset <= 0) {
                centerFirst = point0;
                centerSecond = point1;
                isCloseToCenter = true;

            } else {

                double point0toFirst = distance2(point0, centerFirst);
                double point0toSecond = distance2(point0, centerSecond);
                double point1toFirst = distance2(point1, centerFirst);
                double point1toSecond = distance2(point1, centerSecond);

                double maxDist = MAX_DISTANCE_TO_IGNORE * MAX_DISTANCE_TO_IGNORE;
                if (point0toFirst < point1toFirst && point0toFirst <= maxDist && point1toSecond <= maxDist) {
                    centerFirst.x = (centerFirst.x + point0.x) / 2;
                    centerFirst.y = (centerFirst.y + point0.y) / 2;
                    centerSecond.x = (centerSecond.x + point1.x) / 2;
                    centerSecond.y = (centerSecond.y + point1.y) / 2;
                    isCloseToCenter = true;

                } else if (point0toFirst >= point1toFirst && point1toFirst <= maxDist && point0toSecond <= maxDist) {
                    centerFirst.x = (centerFirst.x + point1.x) / 2;
                    centerFirst.y = (centerFirst.y + point1.y) / 2;
                    centerSecond.x = (centerSecond.x + point0.x) / 2;
                    centerSecond.y = (centerSecond.y + point0.y) / 2;
                    isCloseToCenter = true;

                }

            }
        }

        if (isCloseToCenter) {
            timesTillReset = MAX_TIMES_TILL_RESET;
        } else {
            timesTillReset--;

        }

        if (centerFirst.dot(centerSecond) != 0) {
            Imgproc.line(dst, centerFirst, centerSecond, scalarsB[0], 6);

            if (timesTillReset > 0) {

                double top = Math.min(centerFirst.y, centerSecond.y);
                double bottom = Math.max(centerFirst.y, centerSecond.y);
                double left = Math.min(centerFirst.x, centerSecond.x);
                double right = Math.max(centerFirst.x, centerSecond.x);

                double paddingY = (bottom - top) * 0.3;
                double paddingX = (right - left) * 1.5;

                Point topLeft = new Point(left - paddingX, top - paddingY);
                Point bottomRight = new Point(right + paddingX, bottom + paddingY);
                Scalar white = new Scalar(255, 255, 255, 255);

                Imgproc.rectangle(dst, topLeft, bottomRight, white, 5);

                dropChamberArea = new Point[] {topLeft, bottomRight};
            }
        }

        return dst;
    }

    private Mat detectDrops(Mat dst, Mat gray) {

        if (selectedMode != DEBUG_MODE_ONLY_DROPS) {

            if (dropChamberArea == null) {
                return dst;
            }

            Point topLeft = dropChamberArea[0];
            Point bottomRight = dropChamberArea[1];

        }

        Mat circles = new Mat();

        Imgproc.pyrDown(gray, dsIMG, new Size(gray.cols() / 2, gray.rows() / 2));
        Imgproc.pyrUp(dsIMG, usIMG, gray.size());
        Imgproc.HoughCircles(usIMG, circles, Imgproc.CV_HOUGH_GRADIENT, 2, usIMG.height() / 4, 500, 50, 0, 0);

        if (circles.cols() > 0) {
            for (int x=0; x < Math.min(circles.cols(), 5); x++ ) {
                double circleVec[] = circles.get(0, x);

                if (circleVec == null) {
                    break;
                }

                Point center = new Point((int) circleVec[0], (int) circleVec[1]);
                int radius = (int) circleVec[2];

                Imgproc.circle(dst, center, 3, new Scalar(255, 0, 0), 5);
                Imgproc.circle(dst, center, radius, new Scalar(0, 255, 0), 2);
            }
        }

        circles.release();
        return dst;
    }

    private static double distance2(Point pt1, Point pt2) {
        double dx = pt1.x - pt2.x;
        double dy = pt1.y - pt2.y;
        return dx * dx + dy * dy;
    }

    private static double angle(Point pt1, Point pt2, Point pt0) {
        double dx1 = pt1.x - pt0.x;
        double dy1 = pt1.x - pt0.y;
        double dx2 = pt2.x - pt0.x;
        double dy2 = pt2.y - pt0.y;
        return (dx1 * dx2 + dy1 * dy2) / Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10);
    }

    private static void setLabel(Mat im, String label, MatOfPoint contour, double scale, Scalar color) {
        int fontface = Imgproc.FONT_HERSHEY_SIMPLEX;
        int thickness = 3;
        int[] baseline = new int[1];
        Size text = Imgproc.getTextSize(label, fontface, scale, thickness, baseline);
        Rect r = Imgproc.boundingRect(contour);
        Point pt = new Point(r.x + ((r.width - text.width) / 2), r.y + ((r.height + text.height) / 2));
        Imgproc.putText(im, label, pt, fontface, scale, color, thickness);
    }

}

package il.ac.huji.cs.cv.cvdropcounter;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

class ChamberDetector {

    public static final int DEFAULT_CANNY_LOW = 15;
    public static final int DEFAULT_CANNY_HIGH = 25;
    public static final boolean DEFAULT_FILTER_AREA = true;

    private static final int MAX_TIMES_TILL_RESET = 10;
    private static final int MAX_DISTANCE_TO_IGNORE = 500;

    private Mat dsIMG, usIMG, bwIMG, cIMG, hovIMG;
    private MatOfPoint2f approxCurve;
    private Scalar[] scalarsG;
    private Scalar[] scalarsB;
    private Point[] dropChamberArea;

    private Point centerFirst;
    private Point centerSecond;
    private int timesTillReset;

    private int cannyLow = DEFAULT_CANNY_LOW;
    private int cannyHigh = DEFAULT_CANNY_HIGH;
    private boolean filterArea = DEFAULT_FILTER_AREA;

    ChamberDetector() {}

    public void setCannyLow(int cannyLow) {
        this.cannyLow = cannyLow;
    }

    public void setCannyHigh(int cannyHigh) {
        this.cannyHigh = cannyHigh;
    }

    public void setFilterArea(boolean filterArea) {
        this.filterArea = filterArea;
    }

    public Mat getCanny() {
        return cIMG;
    }

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

    public void onCameraViewStopped() {
        dsIMG = null;
        usIMG = null;
        bwIMG = null;
        cIMG = null;
        hovIMG = null;
        approxCurve = null;
    }

    public Mat detectDropChamber(Mat dst, Mat gray) {
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

            Utils.setLabel(dst, String.format("%.2f,%d", contourArea, numberVertices), cnt, 3, (true ? scalarsG : scalarsB)[0]);
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

                double point0toFirst = Utils.distance2(point0, centerFirst);
                double point0toSecond = Utils.distance2(point0, centerSecond);
                double point1toFirst = Utils.distance2(point1, centerFirst);
                double point1toSecond = Utils.distance2(point1, centerSecond);

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
}

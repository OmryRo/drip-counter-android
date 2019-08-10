package il.ac.huji.cs.cv.cvdropcounter;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class DropDetection {


    public Mat detectDrops(Mat dst, Mat gray) {

//        if (selectedMode != DEBUG_MODE_ONLY_DROPS) {
//
//            if (dropChamberArea == null) {
//                return dst;
//            }
//
//            Point topLeft = dropChamberArea[0];
//            Point bottomRight = dropChamberArea[1];
//
//        }
//
//        Mat circles = new Mat();
//
//        Imgproc.pyrDown(gray, dsIMG, new Size(gray.cols() / 2, gray.rows() / 2));
//        Imgproc.pyrUp(dsIMG, usIMG, gray.size());
//        Imgproc.HoughCircles(usIMG, circles, Imgproc.CV_HOUGH_GRADIENT, 2, usIMG.height() / 4, 500, 50, 0, 0);
//
//        if (circles.cols() > 0) {
//            for (int x=0; x < Math.min(circles.cols(), 5); x++ ) {
//                double circleVec[] = circles.get(0, x);
//
//                if (circleVec == null) {
//                    break;
//                }
//
//                Point center = new Point((int) circleVec[0], (int) circleVec[1]);
//                int radius = (int) circleVec[2];
//
//                Imgproc.circle(dst, center, 3, new Scalar(255, 0, 0), 5);
//                Imgproc.circle(dst, center, radius, new Scalar(0, 255, 0), 2);
//            }
//        }
//
//        circles.release();

        List<Mat> lhsv = new ArrayList<Mat>(3);
        Mat circles = new Mat();
        Mat array255 = new Mat(gray.rows(), gray.cols(), CvType.CV_8UC1);

        array255.setTo(new Scalar(255));
        Scalar hsv_min = new Scalar(0, 50, 50, 0);
        Scalar hsv_max = new Scalar(6, 255, 255, 0);
        Scalar hsv_min2 = new Scalar(175, 50, 50, 0);
        Scalar hsv_max2 = new Scalar(179, 255, 255, 0);
        Mat mHSV = new Mat();
        Imgproc.cvtColor(dst, mHSV, Imgproc.COLOR_RGB2HSV,4);

        Mat mThresholded = new Mat();
        Mat mThresholded2 = new Mat();

        Core.inRange(mHSV, hsv_min, hsv_max, mThresholded);
        Core.inRange(mHSV, hsv_min2, hsv_max2, mThresholded2);
        Core.bitwise_or(mThresholded, mThresholded2, mThresholded);

        Core.split(mHSV, lhsv);
        Mat S = lhsv.get(1);
        Mat V = lhsv.get(2);
        Core.subtract(array255, S, S);
        Core.subtract(array255, V, V);
        S.convertTo(S, CvType.CV_32F);
        V.convertTo(V, CvType.CV_32F);

        Mat distance = new Mat();

        Core.magnitude(S, V, distance);
        Core.inRange(distance,new Scalar(0.0), new Scalar(200.0), mThresholded2);
        Core.bitwise_and(mThresholded, mThresholded2, mThresholded);

        Imgproc.GaussianBlur(mThresholded, mThresholded, new Size(9,9),0,0);
        Imgproc.HoughCircles(mThresholded, circles, Imgproc.CV_HOUGH_GRADIENT, 2, mThresholded.height()/4, 500, 50, 0, 0);
        int rows = circles.rows();
        int elemSize = (int)circles.elemSize();
        float[] data2 = new float[rows * elemSize/4];
        if (data2.length>0){
            circles.get(0, 0, data2);
            for(int i=0; i<data2.length; i=i+3) {
                Point center= new Point(data2[i], data2[i+1]);
                Imgproc.ellipse(dst, center, new Size((double)data2[i+2], (double)data2[i+2]), 0, 0, 360, new Scalar( 255, 0, 255 ), 4, 8, 0 );
            }
        }

        return dst;
    }

}

package il.ac.huji.cs.cv.cvdropcounter;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.util.AttributeSet;

import org.opencv.android.JavaCameraView;

import java.util.ArrayList;

public class BetterCameraView extends JavaCameraView {

    public BetterCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setFocus() {
        mCamera.cancelAutoFocus();

        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);

        ArrayList<Camera.Area> areas = new ArrayList<>();
        areas.add(new Camera.Area(new Rect(-20,-20,20,20), 1000));
        parameters.setFocusAreas(areas);

        mCamera.setParameters(parameters);

        mCamera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {

            }
        });
    }

}

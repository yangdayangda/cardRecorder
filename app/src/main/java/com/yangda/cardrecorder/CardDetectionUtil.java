package com.yangda.cardrecorder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class CardDetectionUtil {
    /**
     * 模板匹配函数，threshold为阈值，0-1，越大表示要求越高
     * @param bgBitmap
     * @param templeteBitmap
     * @param threshold
     * @return
     */
    public static int matchImgNum(Bitmap bgBitmap, Bitmap templeteBitmap, double threshold) {
        // 将 Bitmap 转换为 OpenCV 的 Mat 对象
        Mat bgMat = new Mat();
        Mat templeteMat = new Mat();
        Utils.bitmapToMat(bgBitmap, bgMat);
        Utils.bitmapToMat(templeteBitmap, templeteMat);

        //使用图像金字塔进行下采样，加快查找时间
        Imgproc.pyrDown(bgMat, bgMat, new Size(bgMat.cols() / 2, bgMat.rows() / 2));
        Imgproc.pyrDown(templeteMat, templeteMat, new Size(templeteMat.cols() / 2, templeteMat.rows() / 2));

        // 执行模板匹配
        Mat result = new Mat();
        Imgproc.matchTemplate(bgMat, templeteMat, result, Imgproc.TM_CCOEFF_NORMED);

        // 设置阈值，找到匹配得分高于阈值的区域
        List<Rect> detectedRectangles = new ArrayList<>();
        for (int row = 0; row < result.rows(); row++) {
            for (int col = 0; col < result.cols(); col++) {
                double score = result.get(row, col)[0];
                if (score >= threshold) {
                    // 使用阈值确定匹配的区域
                    Point matchLoc = new Point(col, row);
                    Rect matchRect = new Rect(matchLoc, new Point(matchLoc.x + templeteMat.cols(), matchLoc.y + templeteMat.rows()));
                    // 检查是否与已检测的区域重叠
                    boolean isOverlapping = false;
                    for (Rect existingRect : detectedRectangles) {
                        if (isRectOverlapping(matchRect, existingRect)) {
                            isOverlapping = true;
                            break;
                        }
                    }
                    // 如果没有重叠，将匹配的区域添加到列表中
                    if (!isOverlapping) {
                        detectedRectangles.add(matchRect);
                        // 在原图上标记匹配区域，可选,调试时配合下面查看标记的位置
//                        Imgproc.rectangle(bgMat, matchRect.tl(), matchRect.br(), new Scalar(0, 0, 0), 3);
                    }
                }
            }
        }
//        // 将 Mat 转换为 Bitmap
//        Bitmap resultBitmap = Bitmap.createBitmap(bgMat.cols(), bgMat.rows(), Bitmap.Config.ARGB_8888);
//        Utils.matToBitmap(bgMat, resultBitmap);
        // 释放 Mat 对象
        bgMat.release();
        templeteMat.release();
        result.release();
        // 返回匹配得分高于阈值的区域数量
        return detectedRectangles.size();
    }

    private static boolean isRectOverlapping(Rect rect1, Rect rect2) {
        return (rect1.x < rect2.x + rect2.width &&
                rect1.x + rect1.width > rect2.x &&
                rect1.y < rect2.y + rect2.height &&
                rect1.y + rect1.height > rect2.y);
    }

    public static int[] findMatches(Context context, Bitmap inputBitmap, double threshold, boolean handCard, float ratio) {
        int[] result = new int[15];
        float scale = handCard ? 80f : 60f;
        for (int i = 1; i <= 15; i++) {
            String imageName = "point"+ i ; // 替换成你需要查找的图片名称
            int resourceId = context.getResources().getIdentifier(imageName, "drawable", context.getPackageName());
            Bitmap smallBitmap = BitmapFactory.decodeResource(context.getResources(), resourceId);
            smallBitmap = ImageUtils.setImgSize(smallBitmap, scale * ratio);
            int num = matchImgNum(inputBitmap, smallBitmap, threshold);
            if (i == 15){
                //由于出牌区的王和手牌区的王不一样，所以需要重新识别一次
                result[14] += num;
                break;
            }
            result[i] = num;
        }
        return result;
    }
}
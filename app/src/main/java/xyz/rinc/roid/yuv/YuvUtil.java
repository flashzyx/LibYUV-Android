package xyz.rinc.roid.yuv;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class YuvUtil {

    static {
        System.loadLibrary("YuvUtil");
    }

    public enum FilterMode {
        None,      // Point sample; Fastest.
        Linear,    // Filter horizontally only.
        Bilinear,  // Faster than box, but lower quality scaling down.
        Box        // Highest quality.
    }
    
    public enum YuvFormat {
        NV21,
        I420
    }
    
    public enum RotateMode {
        Clockwise90,
        Clockwise180,
        Clockwise270
    }

    public static boolean imageToYuvBytes(Image image, YuvFormat yuvFormat, byte[] yuv_bytes) {
        try {
            Rect crop = image.getCropRect();
            int width = crop.width();
            int height = crop.height();
            Image.Plane[] planes = image.getPlanes();
            byte[] row_bytes = new byte[planes[0].getRowStride()];
            int channelOffset = 0;
            int outputStride = 1;
            ByteBuffer buffer;
            for (int i = 0; i < planes.length; i++) {
                switch (i) {
                    case 0: {
                        channelOffset = 0;
                        outputStride = 1;
                        break;
                    }
                    case 1: {
                        if (yuvFormat == YuvFormat.I420) {
                            channelOffset = width * height;
                            outputStride = 1;
                        } else {
                            channelOffset = width * height + 1;
                            outputStride = 2;
                        }
                        break;
                    }
                    case 2: {
                        if (yuvFormat == YuvFormat.I420) {
                            channelOffset = width * height * 5 / 4;
                            outputStride = 1;
                        } else {
                            channelOffset = width * height;
                            outputStride = 2;
                        }
                        break;
                    }
                }
                buffer = planes[i].getBuffer();
                int rowStride = planes[i].getRowStride();
                int pixelStride = planes[i].getPixelStride();
                int shift = (i == 0) ? 0 : 1;
                int w = width >> shift;
                int h = height >> shift;
                buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
                for (int row = 0; row < h; row++) {
                    int length;
                    if (pixelStride == 1 && outputStride == 1) {
                        length = w;
                        buffer.get(yuv_bytes, channelOffset, length);
                        channelOffset += length;
                    } else {
                        length = (w - 1) * pixelStride + 1;
                        buffer.get(row_bytes, 0, length);
                        for (int col = 0; col < w; col++) {
                            yuv_bytes[channelOffset] = row_bytes[col * pixelStride];
                            channelOffset += outputStride;
                        }
                    }
                    if (row < h - 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                }
                buffer.clear();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    @Deprecated
    public static byte[] yuv2Jpeg(byte[] data, int format, final Size size, Rect cropRect, int quality) {
        if (data == null || data.length <=0 || size == null || size.getWidth() == 0 || size.getHeight() == 0 || quality <= 0 || quality > 100) return null;
        byte[] bytes = null;

        byte[] nv21_bytes = null;
        if (format == ImageFormat.YUV_420_888) {
            nv21_bytes = new byte[data.length];
            YuvUtil.I420ToNV21(data, size, nv21_bytes);
            System.arraycopy(nv21_bytes, 0, data, 0, data.length);
            format = ImageFormat.NV21;
        }

        if (cropRect == null) cropRect = new Rect(0, 0, size.getWidth(), size.getHeight());
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
        try {
            if (new YuvImage(data, format, size.getWidth(), size.getHeight(), null).compressToJpeg(cropRect, quality, baos)) {
                bytes = baos.toByteArray();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (baos != null) {
            try {
                baos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bytes;
    }
    
    /*
    public static byte[] I420Compress(byte[] yuvBytes, Size size, Rect rect, Bitmap.Config config, Bitmap.CompressFormat format, int quality) {
        Mat matYUV = new Mat(size.getHeight() * 3 / 2, size.getWidth(), CvType.CV_8UC1);
        matYUV.put(0, 0, yuvBytes);

        Mat matRGB = new Mat();
        Imgproc.cvtColor(matYUV, matRGB, Imgproc.COLOR_YUV2RGB_IYUV);

        Mat matRGBCropped = new Mat(matRGB, new org.opencv.core.Rect(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top));

        Bitmap bmp = Bitmap.createBitmap(matRGBCropped.cols(), matRGBCropped.rows(), config);;
        Utils.matToBitmap(matRGBCropped, bmp);

        byte[] jpegBytes = null;
        if (bmp != null && !bmp.isRecycled()) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                bmp.compress(format, quality, baos);
                jpegBytes = baos.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
            }
            bmp.recycle();
        }

        return jpegBytes;
    }*/

    public static int bitmapToI420(Bitmap bitmap, byte[] dst_bytes) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getConfig() != Bitmap.Config.ARGB_8888) return Integer.MIN_VALUE;
        if (dst_bytes == null) dst_bytes = new byte[bitmap.getWidth() * bitmap.getHeight() * 3 / 2];
        int ret = nativeBitmapToI420(bitmap, dst_bytes);
        bitmap.recycle();
        return ret;
    }

    public static int I420ToNV21(byte[] i420_bytes, Size size, byte[] dst_bytes) {
        if (i420_bytes == null || i420_bytes.length <= 0
                || size == null || size.getWidth() <= 0 || size.getHeight() <= 0) return Integer.MIN_VALUE;
        if (dst_bytes == null) dst_bytes = new byte[size.getWidth() * size.getHeight() * 3 / 2];
        return nativeI420ToNV21(i420_bytes, dst_bytes, size.getWidth(), size.getHeight());
    }

    public static int NV21ToI420(byte[] nv21_bytes, Size size, byte[] dst_bytes) {
        if (nv21_bytes == null || nv21_bytes.length <= 0
                || size == null || size.getWidth() <= 0 || size.getHeight() <= 0) return Integer.MIN_VALUE;
        if (dst_bytes == null) dst_bytes = new byte[size.getWidth() * size.getHeight() * 3 / 2];
        return nativeNV21ToI420(nv21_bytes, dst_bytes, size.getWidth(), size.getHeight());
    }

    public static int rotateI420(byte[] src_bytes, Size size, RotateMode mode, byte[] dst_bytes) {
        if (src_bytes == null || src_bytes.length <= 0
                || size == null || size.getWidth() <= 0 || size.getHeight() <= 0) return Integer.MIN_VALUE;
        if (mode == RotateMode.None) return 0;
        if (dst_bytes == null) dst_bytes = new byte[size.getWidth() * size.getHeight() * 3 / 2];
        int degree = 0;
        switch (mode) {
            case Clockwise270: degree += 90;
            case Clockwise180: degree += 90;
            case Clockwise90: degree += 90;
        }
        return nativeRotateI420(src_bytes,size.getWidth(), size.getHeight(), dst_bytes, degree);
    }

    public static int mirrorI420(byte[] src_bytes, Size size, byte[] dst_bytes) {
        if (src_bytes == null || src_bytes.length <= 0
                || size == null || size.getWidth() <= 0 || size.getHeight() <= 0) return Integer.MIN_VALUE;
        if (dst_bytes == null) dst_bytes = new byte[size.getWidth() * size.getHeight() * 3 / 2];
        return nativeMirrorI420(src_bytes, size.getWidth(), size.getHeight(), dst_bytes);
    }

    public static int scaleI420(byte[] src_bytes, Size src_size, Size dst_size, FilterMode filter_mode, byte[] dst_bytes) {
        if (src_bytes == null || src_bytes.length <= 0
                || src_size == null || src_size.getWidth() <= 0 || src_size.getHeight() <= 0
                || dst_size == null || dst_size.getWidth() <= 0 || dst_size.getHeight() <= 0) return Integer.MIN_VALUE;
        if (dst_bytes == null) dst_bytes = new byte[Math.max(src_size.getWidth() * src_size.getHeight(), dst_size.getWidth() * dst_size.getHeight()) * 3 / 2];
        int mode = 0;
        switch (filter_mode) {
            case Box: ++mode;
            case Bilinear: ++mode;
            case Linear: ++mode;
            case None:
        }
        return nativeScaleI420(src_bytes, src_size.getWidth(), src_size.getHeight(), dst_bytes, dst_size.getWidth(), dst_size.getHeight(), mode);
    }
    
    private static native int nativeBitmapToI420(Bitmap bitmap, byte[] i420_bytes);
    private static native int nativeI420ToNV21(byte[] i420_bytes, byte[] nv21_bytes, int width, int height);
    private static native int nativeNV21ToI420(byte[] nv21_bytes, byte[] i420_bytes, int width, int height);
    private static native int nativeRotateI420(byte[] src_bytes, int width, int height, byte[] dst_bytes, int degree);
    private static native int nativeMirrorI420(byte[] src_bytes, int width, int height, byte[] dst_bytes);
    private static native int nativeScaleI420(byte[] src_bytes, int src_width, int src_height, byte[] dst_bytes, int dst_width, int dst_height, int filter_mode);
}

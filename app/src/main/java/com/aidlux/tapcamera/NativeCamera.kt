package com.aidlux.tapcamera

import android.Manifest
import android.content.Context
import android.content.Context.CAMERA_SERVICE
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Range
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.Transformations.map
import java.nio.ByteBuffer
import java.util.*


class NativeCamera(
    val mContext: Context,val  type:Int,
    val imageW: Int,
    val imageH: Int,val surface: Surface
) {
    var mCameraHandler: Handler?
    var mainHandler: Handler?
    var mCameraDevice: CameraDevice? = null
    var frontCameraId = ""
    var backCameraId = ""
    var mCameraId = ""
    var frontCameraCharacteristics: CameraCharacteristics? = null
    var backCameraCharacteristics: CameraCharacteristics? = null
    var mCaptureSession: CameraConstrainedHighSpeedCaptureSession? = null
    var mPreviewRequestBuilder: CaptureRequest.Builder? = null
    var mSessionCallback: CameraCaptureSession.StateCallback? = null
    lateinit var  fpsRanges: Array< Range<Int>>
    lateinit var  highfpsRanges: Array< Range<Int>>
    open val info =StringBuffer()
    open val openInfo =StringBuffer()



    private val ORIENTATIONS: SparseIntArray = SparseIntArray()

    init {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
        initInfo()
        val handlerThread = HandlerThread("CameraThread");
        handlerThread.start();
        mCameraHandler = Handler(handlerThread.getLooper());
        mainHandler = Handler(Looper.getMainLooper());




        mSessionCallback =
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    // The camera is already closed
                    if (mCameraDevice == null) {
                        return
                    }

                    mCaptureSession = session as CameraConstrainedHighSpeedCaptureSession
                    captureImageRequests=mCaptureSession?.createHighSpeedRequestList(mPreviewRequestBuilder?.build()!!)
                    mPreviewRequestBuilder?.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )

                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    throw Exception(session.toString())
                }

                override fun onClosed(session: CameraCaptureSession) {
                    if (mCaptureSession != null && mCaptureSession!!.equals(session)) {
                        mCaptureSession = null
                    }
                }
            }

    }


    fun initInfo() {
        val mCameraManager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val cameraIdList = mCameraManager.cameraIdList
        Log.d("native-cameraIdList",cameraIdList.size.toString())
        info.appendln("native-cameraIdList:"+cameraIdList.size.toString())
        cameraIdList.forEach { cameraId ->
            Log.d("native-cameraId",cameraId.toString())
            info.appendln("native-cameraId:"+cameraId.toString())
            val cameraCharacteristics: CameraCharacteristics =
                mCameraManager.getCameraCharacteristics(cameraId)
            val orientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)

            if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_FRONT&&frontCameraId.isEmpty()) {
                frontCameraId = cameraId
                frontCameraCharacteristics = cameraCharacteristics
                Log.d("native-orientation_front", orientation.toString())
            } else if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK&&backCameraId.isEmpty()) {
                backCameraId = cameraId
                backCameraCharacteristics = cameraCharacteristics
                Log.d("native-orientation_back", orientation.toString())
            }
            Log.d("native-BackCameraId", backCameraId.toString())
            info.appendln("native-BackCameraId:"+backCameraId.toString())
            Log.d("native-FrontCameraId", frontCameraId.toString())
            info.appendln("native-FrontCameraId:"+frontCameraId.toString())
            val mCameraSensorOrientation =
                cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            info.appendln("native-CameraSensorOrientation:"+ mCameraSensorOrientation.toString())
            Log.d("native-CameraSensorOrientation", mCameraSensorOrientation.toString())
            val configurationMap = cameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )
            fpsRanges =
                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)!!;

            Log.d(
                "native-fps", Arrays.toString(fpsRanges)
            );
            info.appendln("native-fps:" + Arrays.toString(fpsRanges))
            info.appendln("native-high-fps:"+Arrays.toString(configurationMap?.getHighSpeedVideoFpsRanges()))

            Log.d("native-high-fps", Arrays.toString(configurationMap?.getHighSpeedVideoFpsRanges()))
            val savePicSize: Array<Size> = configurationMap!!.getOutputSizes(ImageFormat.JPEG)
            if (cameraId.equals(0.toString())){
                highfpsRanges= configurationMap?.getHighSpeedVideoFpsRanges()!!
                val highsavePicSize: Array<Size> = configurationMap!!.getHighSpeedVideoSizesFor(highfpsRanges[1])
                info.appendln("native-highsavePicSize:"+Arrays.toString(highsavePicSize))
                Log.d("native-highsavePicSize",Arrays.toString(highsavePicSize))
            }

            val previewSize: Array<Size> = configurationMap.getOutputSizes(
                SurfaceTexture::class.java
            )

            info.appendln("native-savePicSize:"+Arrays.toString(savePicSize))
            Log.d("native-savePicSize",Arrays.toString(savePicSize))

            info.appendln("native-previewSize:"+Arrays.toString(previewSize))
            Log.d("native-previewSize",Arrays.toString(previewSize))
            val formats = configurationMap.getOutputFormats();
            info.appendln("native-formats"+Arrays.toString(formats))
            Log.d("native-formats",Arrays.toString(formats))



        }



    }


    @Throws(Exception::class)
    fun openCamera(cameraId: String) {
        mCameraId = cameraId
        val manager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager


        if (mContext.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            throw Exception("缺少camera权限")
            return
        }
        manager.openCamera(mCameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                mCameraDevice = camera
                startCaptureSession()
            }

            override fun onDisconnected(camera: CameraDevice) {
                mCameraDevice?.close();
                mCameraDevice = null;
            }

            override fun onError(camera: CameraDevice, error: Int) {
                release()
                throw Exception("打开摄像头失败")


            }

        }, mainHandler)

    }




    private fun startCaptureSession() {
        if (!isCameraOpened() ) {
            return
        }

        try {
            if (type==1){
                mPreviewRequestBuilder =
                    mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            }else{
                mPreviewRequestBuilder =
                    mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            }

            mPreviewRequestBuilder?.addTarget(surface)
            mPreviewRequestBuilder?.set(CaptureRequest.JPEG_ORIENTATION, 90)
            mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,highfpsRanges[0])
//            mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,fpsRanges[2])
            val value = mPreviewRequestBuilder?.get(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE)

            Log.d("native-open-fps",  value.toString());
            openInfo.appendln("native-open-fps:"+value)

            mCameraDevice?.createConstrainedHighSpeedCaptureSession(
                Arrays.asList(surface),
                mSessionCallback!!, mCameraHandler
            )
        } catch (e: CameraAccessException) {
            throw RuntimeException("Failed to start camera session")
        }
    }

    fun isCameraOpened(): Boolean {
        return mCameraDevice != null
    }





    /**
     *
     * 根據提供的參數值返回與指定寬高相等或最接近的尺寸
     *
     * @param targetWidth   目標寬度
     * @param targetHeight  目標高度
     * @param maxWidth      最大寬度(即TextureView的寬度)
     * @param maxHeight     最大高度(即TextureView的高度)
     * @param sizeList      支持的Size列表
     *
     * @return  返回與指定寬高相等或最接近的尺寸
     *
     */
    private fun getBestSize(
        targetWidth: Int,
        targetHeight: Int,
        maxWidth: Int,
        maxHeight: Int,
        sizeList: List<Size>
    ): Size {
        val bigEnough = ArrayList<Size>()     //比指定寬高大的Size列表
        val notBigEnough = ArrayList<Size>()  //比指定寬高小的Size列表

        for (size in sizeList) {

            //寬<=最大寬度  &&  高<=最大高度  &&  寬高比 == 目標值寬高比
            if (size.width <= maxWidth && size.height <= maxHeight
                && size.width == size.height * targetWidth / targetHeight
            ) {

                if (size.width >= targetWidth && size.height >= targetHeight)
                    bigEnough.add(size)
                else
                    notBigEnough.add(size)
            }
            Log.d(
                "native-size",
                "系統支持的尺寸: ${size.width} * ${size.height} ,  比例 ：${size.width.toFloat() / size.height}"
            )
        }

        Log.d(
            "native-max-size",
            "最大尺寸 ：$maxWidth * $maxHeight, 比例 ：${targetWidth.toFloat() / targetHeight}"
        )
        Log.d(
            "native-target-size",
            "目標尺寸 ：$targetWidth * $targetHeight, 比例 ：${targetWidth.toFloat() / targetHeight}"
        )

        //選擇bigEnough中最小的值  或 notBigEnough中最大的值
        return when {
            bigEnough.size > 0 -> Collections.min(bigEnough, CompareSizesByArea())
            notBigEnough.size > 0 -> Collections.max(notBigEnough, CompareSizesByArea())
            else -> sizeList[0]
        }
    }


    private class CompareSizesByArea : Comparator<Size> {
        override fun compare(size1: Size, size2: Size): Int {
            return java.lang.Long.signum(size1.width.toLong() * size1.height - size2.width.toLong() * size2.height)
        }
    }


    fun release() {
        if (mCaptureSession != null) {
            mCaptureSession?.close()
            mCaptureSession = null
        }
        if (mCameraDevice != null) {
            mCameraDevice?.close()
            mCameraDevice = null
        }



        mCameraHandler = null;
        mainHandler = null;

    }
    var captureImageRequests :List<CaptureRequest>?=null
    fun takePhoto(){
        mCaptureSession?.captureBurst(   captureImageRequests!!,
            null, mCameraHandler)?:toast("请打开相机")
    }

    fun startPreview(){
        // When the session is ready, we start displaying the preview.

        try {
            mCaptureSession?.setRepeatingBurst(
                captureImageRequests!!,
                null, mCameraHandler
            )?:toast("请打开相机")
        } catch (e: CameraAccessException) {

            throw Exception("Failed to start camera preview because it couldn't access camera")
        } catch (e: IllegalStateException) {
            throw Exception("Failed to start camera preview.")
        }
    }

    fun getPreviewOrientation(mContext: Context, camera2Id: String?): Int {
        val mCameraManager = mContext.getSystemService(CAMERA_SERVICE) as CameraManager
        var characteristics: CameraCharacteristics? = null
        try {
            if (mCameraManager != null) {
                characteristics = mCameraManager.getCameraCharacteristics(camera2Id!!)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        var result = 0
        if (characteristics != null) {
            val mCameraOrientation = characteristics[CameraCharacteristics.SENSOR_ORIENTATION]
            val wm =
                mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (wm != null) {
                val rotation: Int = wm.getDefaultDisplay().getRotation()
                if (characteristics[CameraCharacteristics.LENS_FACING] === CameraMetadata.LENS_FACING_FRONT) {
                    result = (mCameraOrientation!! + ORIENTATIONS.get(rotation)) % 360
                    result = (360 - result) % 360
                } else {
                    result = (mCameraOrientation!! - ORIENTATIONS.get(rotation) + 360) % 360
                }
            }
        }
        return result
    }

    private fun toast(s: String) {
        Toast.makeText(mContext, s, Toast.LENGTH_SHORT).show()
    }

}
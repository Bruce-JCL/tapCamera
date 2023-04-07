package com.aidlux.tapcamera

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.media.ImageReader
import android.media.MediaCodecList
import android.os.*
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.io.File
import java.nio.ByteBuffer


class MainActivity : AppCompatActivity() {
    lateinit var stop:Button
    lateinit var nativeCamera: NativeCamera
    lateinit var pre:Button
    lateinit var infoTv:TextView
    lateinit var save:Button
    var imageHandler: Handler?=null
    lateinit var surfacetv:TextureView
    var time1:Long=0
    val TAG="codec"
    var mImageReader: ImageReader? = null
    var imageW=1920
    var imageH=1080
    val fpsUtil=FpsUtil("image")
    val captureBitmap = Bitmap.createBitmap(imageW, imageH, Bitmap.Config.ARGB_8888)
    var buffer = ByteBuffer.allocate(captureBitmap.getByteCount())
    val file=File("/sdcard/Documents/test/img.bmp")
    val textureViewLi = object : TextureView.SurfaceTextureListener{
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            start()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {

        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false

        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            val time_1=System.currentTimeMillis()
            surfacetv.getBitmap(captureBitmap)
            buffer.clear()
            captureBitmap.copyPixelsToBuffer(buffer)
            val time_2=System.currentTimeMillis()

            Log.d("image_time1",(time_2-time_1).toString())
//            Log.d("image_time2",(time_3-time_2).toString())
            fpsUtil.timing()
        }


    }


    lateinit var onImageAvailableListener: ImageReader.OnImageAvailableListener
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        prepareImageReader()


        surfacetv=findViewById(R.id.surfacetv)
        save=findViewById(R.id.save)
        surfacetv.surfaceTextureListener=textureViewLi
        stop=findViewById(R.id.close_button)
        infoTv=findViewById(R.id.info)

        pre=findViewById(R.id.pre)
        save.setOnClickListener {
            val time0=System.currentTimeMillis()
            file.writeBytes(buffer.array())
            Log.d("image----time--file",(System.currentTimeMillis()-time0).toString())
        }
        pre.setOnClickListener {
            time1=System.currentTimeMillis()
            nativeCamera.startPreview()
        }
        stop.setOnClickListener {
            restart()
        }

//        codec()




    }

    fun start(){
        XXPermissions.with(this)
            // 申请单个权限
            .permission(Permission.CAMERA)
            // 申请多个权限
            .permission(Permission.Group.STORAGE)
            .request(object : OnPermissionCallback {

                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                    if (!allGranted) {
                        toast("获取部分权限成功，但部分权限未正常授予")
                        return
                    }
                    toast("获取相机和存储权限成功")
                    if (!file.exists()){
                        file.createNewFile()
                    }
                    doCamera()
                }

                override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                    if (doNotAskAgain) {
                        toast("被永久拒绝授权，请手动授予相机和存储权限")
                        // 如果是被永久拒绝就跳转到应用权限系统设置页面
                        XXPermissions.startPermissionActivity(this@MainActivity, permissions)
                    } else {
                        toast("获取相机和存储权限失败")
                    }
                }
            })
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }


    fun doCamera() {
        nativeCamera = NativeCamera(getApplicationContext(), 0,1920, 1080, Surface(surfacetv.surfaceTexture) )
        infoTv.setText(nativeCamera.info)

        nativeCamera.openCamera(0.toString())
        SystemClock.sleep(1000)
        Log.d("openInfo",nativeCamera.openInfo.toString())
    }

    override fun onDestroy() {
        toast("restart")
        nativeCamera.release()
        if (mImageReader != null) {
            mImageReader?.close()
            mImageReader = null
        }
        imageHandler = null;
        Log.d("native-destroy","restart")

        super.onDestroy()
    }


    fun restart(){

        val intent = baseContext.packageManager
            .getLaunchIntentForPackage(baseContext.packageName)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    /**
     * 准备Imagereader
     */
    private fun prepareImageReader() {
        val imageThread = HandlerThread("image reader")
        imageThread.start()
        imageHandler = Handler(imageThread.looper)
        onImageAvailableListener = object : ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(reader: ImageReader) {
                val time5=System.currentTimeMillis()
                val image = reader.acquireLatestImage()
//                if (image == null) {
//                    return
//                }
//                val y_size = image.planes[0].getBuffer().remaining()
//                val u_size = image.planes[1].getBuffer().remaining()
//                val v_size = image.planes[2].getBuffer().remaining()
//                var index = 0
//                if (yuv != null) {
//
//                    for (plane in image.planes) {
//                        val size = plane.getBuffer().remaining()
//                        val pixelStride = plane.pixelStride
//                        for (i in 0 until size step pixelStride) {
//                            if (plane.buffer==null){
//                                return
//                            }
//                            val byte = plane.buffer.get(i)
//                            yuv!![index] = byte
//                            index++
//                        }
//
//                    }
//                    Log.d("native-yuv-time-pre",(System.currentTimeMillis()-time5).toString())
//
////                    Log.d("image_index", index.toString())
//                    inputCallBack.yuv(yuv!!, imageW, imageH)
//                }
//                image.close()
                fpsUtil.timing()
                val byteBuffer: ByteBuffer = image.getPlanes().get(0).getBuffer()
                val byteArray = ByteArray(byteBuffer.remaining())
                byteBuffer.get(byteArray)
                Log.d("native-yuv-time-pre",(System.currentTimeMillis()-time5).toString())
                Log.d("native-yuv-time",(System.currentTimeMillis()-time1).toString())

                Log.d("native-yuv", byteArray.size.toString())
                time1=System.currentTimeMillis()
                image.close()


            }
        }
        if (mImageReader != null) {
            mImageReader?.close()
        }
        mImageReader = ImageReader.newInstance(imageW, imageH, ImageFormat.PRIVATE, 1)
        mImageReader?.setOnImageAvailableListener(onImageAvailableListener, imageHandler)

    }


    fun codec(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val supportCodes = list.codecInfos
            Log.i(TAG, "解码器列表：")
            for (codec in supportCodes) {
                if (!codec.isEncoder) {
                    val name = codec.name
                    if (name.startsWith("OMX.google")) {
                        Log.i(TAG, "软解->$name")
                    }
                }
            }
            for (codec in supportCodes) {
                if (!codec.isEncoder) {
                    val name = codec.name
                    if (!name.startsWith("OMX.google")) {
                        Log.i(TAG, "硬解->$name")
                    }
                }
            }
            Log.i(TAG, "编码器列表：")
            for (codec in supportCodes) {
                if (codec.isEncoder) {
                    val name = codec.name
                    if (name.startsWith("OMX.google")) {
                        Log.i(TAG, "软编->$name")

                    }
                }
            }
            for (codec in supportCodes) {
                if (codec.isEncoder) {
                    val name = codec.name
                    if (!name.startsWith("OMX.google")) {
                        Log.i(TAG, "硬编->$name")
                    }
                }
            }
        }

    }
}
package com.camerapbo

import android.content.Context
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.util.AttributeSet
import java.nio.IntBuffer
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.view.SurfaceHolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import android.widget.Toast
import android.view.Surface
import java.util.*


/**
 * Created by apple on 2018/5/13.
 */
class DrawGLSurfaceView : GLSurfaceView ,GLSurfaceView.Renderer{

    private var mPboIds:IntBuffer = IntBuffer.allocate(2)
    private var mContext:Context? = null
    private var mPboSize:Int = 0
    private var mPboIndex:Int = 0
    private var mPboNewIndex:Int = 1
    private var mFrameBuffers:IntBuffer = IntBuffer.allocate(1)
    private var mFrameBufferTextures:IntBuffer = IntBuffer.allocate(1)

    private var viewWidth:Int = 0
    private var viewHeight:Int = 0
    private var mProgram: Int = 0

    private var mGlAPos: Int = 0
    private var mGlUTex: Int = 0
    private var mGlUTransMatrix: Int = 0
    private var mGlUCameraMatrix: Int = 0

    private var mSurfaceTextureId:Int = 0

    private var vertexBuffer:FloatBuffer? = null
    private val mFrameWidth = 480
    private val mFrameHeight = 640

    private val vert_shader = "attribute vec4 position;\n" +
            "varying vec4 textureCoordinate;\n" +
            "\n" +
            "uniform mat4 textureTransform;\n" +
            "uniform mat4 cameraTransform;\n" +
            "\n" +
            "void main() {\n" +
            "    textureCoordinate =  ( textureTransform *cameraTransform* position);\n" +
            "    gl_Position = position;\n" +
            "}\n" +
            "\n"

    private val fragment_shader = "#extension GL_OES_EGL_image_external : require\n" +
            "\n" +
            "varying highp vec4 textureCoordinate;\n" +
            "\n" +
            "uniform samplerExternalOES inputImageTexture;\n" +
            "\n" +
            "void main(){\n" +
            "    gl_FragColor = texture2D(inputImageTexture, textureCoordinate.xy);\n" +
            "}\n" +
            "\n"

    private val TAG = "DrawGLSurfaceView"
    constructor(context: Context) : super(context) {
        mContext = context
        setEGLConfigChooser(8,8,8,8,8,0)
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context,attributeSet) {
    }

    var squareCoords = floatArrayOf(-1.0f, 1.0f,0.0f,1.0f,
            -1.0f, -1.0f,0.0f,1.0f,
            1.0f, -1.0f,0.0f,1.0f,
            1.0f, 1.0f,0.0f,1.0f)

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,mFrameBuffers[0])
        GLES20.glViewport(0,0,mFrameWidth,mFrameHeight)

        draw()
        readFromPbo()

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0)
        GLES20.glViewport(0,0,viewWidth,viewHeight)

        draw()
    }

    companion object {

        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
    external fun stringFromJNI(): String
    external fun readPixel(pbosize:Int)
    private var mInitRecord:Boolean = true
    private fun readFromPbo() {
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboIds.get(mPboIndex))
        GLES30.glReadPixels(0,0,mRowStride/4,mFrameHeight,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,0)

        if (mInitRecord) {
            unbindPixelBuffer()
            mInitRecord = false
            return
        }

        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboIds.get(mPboNewIndex));
        //var bytebuffer:ByteBuffer = GLES30.glMapBufferRange(GLES30.GL_PIXEL_PACK_BUFFER,0,mPboSize,GLES30.GL_MAP_READ_BIT) as ByteBuffer
        readPixel(mPboSize);

        GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER)
        unbindPixelBuffer()

//        android.util.Log.e(TAG, "[0] = ${bytebuffer[0]}")
//        android.util.Log.e(TAG, "[1] = ${bytebuffer[1]}")
//        android.util.Log.e(TAG, "[2] = ${bytebuffer[2]}")
    }

    private fun unbindPixelBuffer() {
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);

        mPboIndex = (mPboIndex + 1) % 2;
        mPboNewIndex = (mPboNewIndex + 1) % 2;
    }

    private fun draw() {
        GLES20.glClearColor(1.0f,0.0f,0.0f,1.0f)
        GLES20.glClearDepthf(1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(mProgram)
        val bb = ByteBuffer.allocateDirect(squareCoords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer!!.put(squareCoords)
        vertexBuffer!!.position(0)

        GLES20.glEnableVertexAttribArray(mGlAPos)
        GLES20.glVertexAttribPointer(mGlAPos,4,GLES20.GL_FLOAT,false,0,vertexBuffer)

        val transform = floatArrayOf(
                0.5f,0.0f,0.0f,0.5f,
                0.0f,0.5f,0.0f,0.5f,
                0.0f,0.0f,0.0f,0.0f,
                0.0f,0.0f,0.0f,1.0f
        )

        mSurfaceTexture!!.updateTexImage()

        var floatArray = FloatArray(16)
        mSurfaceTexture!!.getTransformMatrix(floatArray)


        GLES20.glUniformMatrix4fv(mGlUTransMatrix,1,false,transform,0)
        GLES20.glUniformMatrix4fv(mGlUCameraMatrix,1,false,floatArray,0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,mSurfaceTextureId)
        GLES20.glUniform1i(mGlUTex,0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN,0,4)

        GLES20.glDisableVertexAttribArray(mGlAPos);
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        initFbo(mFrameWidth,mFrameHeight)
        viewWidth = width
        viewHeight = height
        GLES20.glViewport(0,0,width,height)
        genTextureId(width,height)
        mSurfaceTexture = SurfaceTexture(mSurfaceTextureId)
        mSurfaceTexture!!.setDefaultBufferSize(width,height)
        mSurface = Surface(mSurfaceTexture!!)
        mSurfaceTexture!!.setOnFrameAvailableListener(mFrameAvailableListener)

        initCameraAndPreview()
    }

    private val mFrameAvailableListener = object : SurfaceTexture.OnFrameAvailableListener {
        override fun onFrameAvailable(p0: SurfaceTexture?) {
            requestRender()
        }

    }

    override fun onSurfaceCreated(gl: GL10?, eglConfig: EGLConfig?) {
        var version = GLES30.glGetString(GLES30.GL_VERSION)
        android.util.Log.e(TAG,"version = ${version}")
        initPbo()
        compileShader()
    }
    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        super.surfaceDestroyed(holder)
        GLES20.glDeleteProgram(mProgram)
        GLES20.glDeleteFramebuffers(1,mFrameBuffers)
        GLES20.glDeleteTextures(1,mFrameBufferTextures)
        GLES30.glDeleteBuffers(2,mPboIds)

        mCameraDevice!!.close()
        mSurfaceTexture!!.release()
        var intArray = IntArray(1)
        intArray[0] = mSurfaceTextureId
        GLES20.glDeleteTextures(1,intArray,0)
    }

    private fun genTextureId(width:Int ,height: Int) {
        var textureid:IntBuffer = IntBuffer.allocate(1)
        GLES20.glGenTextures(1,textureid)
        mSurfaceTextureId = textureid.get()

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES ,mSurfaceTextureId)

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexImage2D(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,0,GLES20.GL_RGBA,width,height,0,
                GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null)

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,0)
    }
    private fun loadShader(type: Int, shaderCode: String): Int {
        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        val shader = GLES20.glCreateShader(type)

        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val log = GLES20.glGetShaderInfoLog(shader)
        android.util.Log.d("DirectDraw", log)
        return shader
    }

    private fun compileShader() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vert_shader)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragment_shader)

        mProgram = GLES20.glCreateProgram()             // create empty OpenGL ES Program
        GLES20.glAttachShader(mProgram, vertexShader)   // add the vertex shader to program
        GLES20.glAttachShader(mProgram, fragmentShader) // add the fragment shader to program
        GLES20.glLinkProgram(mProgram)                  // creates OpenGL ES program executables

        mGlAPos = GLES20.glGetAttribLocation(mProgram,"position")
        mGlUTex = GLES20.glGetUniformLocation(mProgram,"inputImageTexture")
        mGlUTransMatrix = GLES20.glGetUniformLocation(mProgram,"textureTransform")
        mGlUCameraMatrix = GLES20.glGetUniformLocation(mProgram,"cameraTransform")

        GLES20.glUseProgram(mProgram)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
    }

    private fun initFbo(width: Int,height: Int) {
        GLES20.glGenFramebuffers(1,mFrameBuffers)
        GLES20.glGenTextures(1,mFrameBufferTextures)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,mFrameBufferTextures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,width,height,0,
                GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,mFrameBuffers[0])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,mFrameBufferTextures[0],0)

        var status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)

        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            android.util.Log.e(TAG,"framebuffer error ${status}")
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0)
    }

    private var mRowStride:Int = 0
    private fun initPbo():Int {
        val align = 4 //4字节对齐
        mRowStride = mFrameWidth * 4 + (align - 1) and (align - 1).inv()


        mPboSize = mRowStride * mFrameHeight
        GLES30.glGenBuffers(2,mPboIds)
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER,mPboIds.get(0))

        GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER,mPboSize,null,GLES30.GL_STATIC_READ)

        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER,mPboIds.get(1))
        GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER,mPboSize,null,GLES30.GL_STATIC_READ)

        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER,0)
        return 0
    }
    override fun onPause() {
        super.onPause()
    }
    public fun permissionOpenCamera() {
        if (ActivityCompat.checkSelfPermission(mContext!!,android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

            mCameraManager!!.openCamera(mCameraId!!, deviceStateCallback, mHandle)
        }
    }
    private var mHandle:Handler? = null
    private var mCameraId = ""
    private var mCameraManager:CameraManager? = null
    private var mCameraDevice:CameraDevice? = null
    private fun initCameraAndPreview() {
        var handlerThread = HandlerThread("My Camera2");
        handlerThread.start();
        mHandle = Handler(handlerThread.looper)

        try {
            mCameraId = "" + CameraCharacteristics.LENS_FACING_FRONT
            mCameraManager = mContext!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            if (ActivityCompat.checkSelfPermission(mContext!!,android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.e(TAG,"permission denid")

                ActivityCompat.requestPermissions(mContext as Activity, arrayOf(android.Manifest.permission.CAMERA),12)

                return
            }
            mCameraManager!!.openCamera(mCameraId!!,deviceStateCallback,mHandle)

        } catch (e:CameraAccessException) {
            e.printStackTrace()
        }
    }

    private val deviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            mCameraDevice = camera
            try {
                takePreview()
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }

        }

        override fun onDisconnected(camera: CameraDevice) {
            if (mCameraDevice != null) {
                mCameraDevice!!.close()
                mCameraDevice = null
            }
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Toast.makeText(mContext, "打开摄像头失败", Toast.LENGTH_SHORT).show()
        }
    }

    private var mPreviewBuilder: CaptureRequest.Builder? = null
    private var mSurfaceTexture:SurfaceTexture? = null
    private var mSurface:Surface? = null
    private fun takePreview()  {
        android.util.Log.e(TAG,"takePreview")
        mPreviewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        mPreviewBuilder!!.addTarget(mSurface!!)
        mCameraDevice!!.createCaptureSession(Arrays.asList(mSurface),mSessionPreviewStateCallback,mHandle)
    }

    private val mSessionPreviewStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession?) {
            Toast.makeText(mContext, "配置失败", Toast.LENGTH_SHORT).show();
        }

        override fun onConfigured(session: CameraCaptureSession?) {
            try {
                android.util.Log.e(TAG,"onConfigured")

                mPreviewBuilder!!.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                //打开闪光灯
                mPreviewBuilder!!.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                session!!.setRepeatingRequest(mPreviewBuilder!!.build(), null, mHandle);

            }catch ( e: CameraAccessException) {
                e.printStackTrace()
            }
        }

    }
}
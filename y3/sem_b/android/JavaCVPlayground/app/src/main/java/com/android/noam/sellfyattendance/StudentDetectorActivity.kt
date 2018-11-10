package com.android.noam.sellfyattendance

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.View
import com.android.noam.sellfyattendance.comparators.CompareSizesByArea
import com.android.noam.sellfyattendance.datasets.ClassObj
import com.android.noam.sellfyattendance.datasets.StudentSet
import com.android.noam.sellfyattendance.face.operations.BmpOperations
import com.android.noam.sellfyattendance.face.operations.FaceDetector
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.livinglifetechway.quickpermissions_kotlin.runWithPermissions
import kotlinx.android.synthetic.main.activity_student_detector.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList

class StudentDetectorActivity : AppCompatActivity(), OnModelReadyListener, OnSuccessListener<Bitmap>, OnFailureListener {

    private lateinit var textureView: AutoFitTextureView

    companion object {
        private const val TAG = "StudentDetectorActivity"
        const val ARRIVED_STUDENTS_LIST = "arrived_student_list"
        private val shouldThrottle = AtomicBoolean(false)
        private val ORIENTATIONS = SparseIntArray()
        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        /**
         * Given `choices` of `Size`s supported by a camera, choose the smallest one that
         * is at least as large as the respective texture view size, and that is at most as large as
         * the respective max size, and whose aspect ratio matches with the specified value. If such
         * size doesn't exist, choose the largest one that is at most as large as the respective max
         * size, and whose aspect ratio matches with the specified value.
         *
         * @param choices           The list of sizes that the camera supports for the intended
         *                          output class
         * @param textureViewWidth  The width of the texture view relative to sensor coordinate
         * @param textureViewHeight The height of the texture view relative to sensor coordinate
         * @param maxWidth          The maximum width that can be chosen
         * @param maxHeight         The maximum height that can be chosen
         * @param aspectRatio       The aspect ratio
         * @return The optimal `Size`, or an arbitrary one if none were big enough
         */
        @JvmStatic private fun chooseOptimalSize(
                choices: Array<Size>,
                textureViewWidth: Int,
                textureViewHeight: Int,
                maxWidth: Int,
                maxHeight: Int,
                aspectRatio: Size
        ): Size {

            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough = ArrayList<Size>()
            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough = ArrayList<Size>()
            val w = aspectRatio.width
            val h = aspectRatio.height
            for (option in choices) {
                if (option.width <= maxWidth && option.height <= maxHeight &&
                        option.height == option.width * h / w) {
                    if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            if (bigEnough.size > 0) {
                return Collections.min(bigEnough, CompareSizesByArea())
            } else if (notBigEnough.size > 0) {
                return Collections.max(notBigEnough, CompareSizesByArea())
            } else {
                Log.e(TAG, "Couldn't find any suitable preview size")
                return choices[0]
            }
        }

    }

    private lateinit var cameraID: String
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private lateinit var captureRequest: CaptureRequest
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var imageDimension: Size
    private lateinit var imageReader: ImageReader
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null
    private var rotationValue = 0   //
    private val arrivedStudents = ArrayList<StudentSet>()
    private lateinit var classObj: ClassObj
    private lateinit var fisherFaces: FisherFaces
    private val modelReady = AtomicBoolean(false)
    private var sensorOrientation = 0


    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private val MAX_PREVIEW_WIDTH = 1920

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private val MAX_PREVIEW_HEIGHT = 1080

    /**
     * The [android.util.Size] of camera preview.
     */
    private lateinit var previewSize: Size


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_detector)

        btn_sign_me.setOnClickListener {
            predicted_student_name.text = ""
            predicted_student_id.text = ""
            captureStillPicture(it)
        }

        textureView = texture_view!!



        classObj = intent.extras.getSerializable(SelectClassActivity.CLASS_OBJ_TAG) as ClassObj


        fisherFaces = FisherFaces(classObj.studentList, this)


        doAsync {
            fisherFaces.readAllStudentsFaces()
            fisherFaces.trainModel()
        }
    }

    override fun onModelReady() {
        modelReady.set(true)
    }



    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            return false
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            openCamera(width, height)
        }

    }

    private val stateCallBack = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice?) {
            Log.d(TAG, "onOpened")
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice?) {
            cameraDevice!!.close()
        }

        override fun onError(camera: CameraDevice?, error: Int) {
            Log.e(TAG, "Error $error occurred")
            cameraDevice!!.close()
            cameraDevice = null
        }
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("Camera Background")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }


    private fun openCamera(width: Int, height: Int) = runWithPermissions(android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    {
        setUpCameraOutputs(width, height)
        configureTransform(width, height)

    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale = Math.max(
                    viewHeight.toFloat() / previewSize.height,
                    viewWidth.toFloat() / previewSize.width)
            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    @SuppressLint("MissingPermission")
    private fun setUpCameraOutputs(width: Int, height: Int) {

        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        Log.d(TAG, "openCamera start")
        try {
            cameraID = manager.cameraIdList.single {
                val characteristics = manager.getCameraCharacteristics(it)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_FRONT
            }
            val frontCamCharacteristics = manager.getCameraCharacteristics(cameraID)
            val map = frontCamCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!

            // For still image captures, we use the largest available size.
            val largestDim = Collections.max(
                    Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
                    CompareSizesByArea())
            imageReader = ImageReader.newInstance(largestDim.width, largestDim.height,
                    ImageFormat.JPEG, /*maxImages*/ 4).apply {
                setOnImageAvailableListener(onImageAvailableListener, mBackgroundHandler)
            }

            val displayRotation = windowManager.defaultDisplay.rotation
            sensorOrientation = frontCamCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            val swappedDimensions = areDimensionsSwapped(displayRotation)

            val displaySize = Point()
            windowManager.defaultDisplay.getSize(displaySize)
            val rotatedPreviewWidth = if (swappedDimensions) height else width
            val rotatedPreviewHeight = if (swappedDimensions) width else height
            var maxPreviewWidth = if (swappedDimensions) displaySize.y else displaySize.x
            var maxPreviewHeight = if (swappedDimensions) displaySize.x else displaySize.y

            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) maxPreviewWidth = MAX_PREVIEW_WIDTH
            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) maxPreviewHeight = MAX_PREVIEW_HEIGHT

            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
                    rotatedPreviewWidth, rotatedPreviewHeight,maxPreviewWidth, maxPreviewHeight,
                    largestDim)

            // We fit the aspect ratio of TextureView to the size of preview we picked.
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                textureView.setAspectRatio(previewSize.width, previewSize.height)
            } else {
                textureView.setAspectRatio(previewSize.height, previewSize.width)
            }

            imageDimension = map.getOutputSizes(SurfaceTexture::class.java)[0]
            manager.openCamera(cameraID, stateCallBack, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        Log.d(TAG, "openCamera end")
    }


    private fun areDimensionsSwapped(displayRotation: Int): Boolean {
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    swappedDimensions = true
                }
            }
            else -> {
                Log.e(TAG, "Display rotation is invalid: $displayRotation")
            }
        }
        return swappedDimensions
    }

    fun finishSigning(view: View) {
        val arrivedStudentsIntent = Intent(this, ArrivedStudentsActivity::class.java)
        arrivedStudentsIntent.putExtra(ARRIVED_STUDENTS_LIST, arrivedStudents)
        startActivity(arrivedStudentsIntent)

    }

    fun fixLastAttendance(view: View) {
        arrivedStudents.removeAt(arrivedStudents.lastIndex)
        btn_fix.visibility = View.GONE
    }

    private fun createCameraPreview() {
        try {

            val texture = textureView.surfaceTexture

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW
            )
            captureRequestBuilder.addTarget(surface)


            cameraDevice!!.createCaptureSession(Arrays.asList(surface, imageReader.surface),
                    object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession?) {
                    Log.e(TAG, "onConfigureFailed: failed here.")
                }

                override fun onConfigured(session: CameraCaptureSession?) {
                    if (cameraDevice == null)
                        return
                    cameraCaptureSession = session
                    updatePreview()
                }
            }, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * This a callback object for the [ImageReader]. "onImageAvailable" will be called when a
     * still mediaImage is ready to be saved.
     */
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener {
        Log.d(TAG, "onImageAvailableListener start")
        Log.d(TAG, "image add")
        mBackgroundHandler?.post(
                FaceDetector(it.acquireNextImage(), rotationValue,
                        this, this))
        Log.d(TAG, "onImageAvailableListener end")
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * [.captureCallback] from both [.lockFocus].
     */
    @Suppress("UNUSED_PARAMETER")
    private fun captureStillPicture(view: View) {
        if (shouldThrottle.get()) {
            toast("Still working on previous picture.")
            return
        }
        shouldThrottle.set(true)
        Log.d(TAG, "captureStillPicture start")
        try {
            if (cameraDevice == null) return
            val rotation = windowManager.defaultDisplay.rotation

            // This is the CaptureRequest.Builder that we use to take a picture.
            val captureBuilder = cameraDevice?.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE)?.apply {
                addTarget(imageReader.surface)

                // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
                // We have to take that into account and rotate JPEG properly.
                // For devices with orientation of 90, we return our mapping from ORIENTATIONS.
                // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
                rotationValue = (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360
                set(CaptureRequest.JPEG_ORIENTATION, rotationValue)

                // Use the same AE and AF modes as the preview.
                set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }

            val captureCallback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession,
                                                request: CaptureRequest,
                                                result: TotalCaptureResult) {
                    cameraCaptureSession?.setRepeatingRequest(captureRequest, null,
                            mBackgroundHandler)
                    shouldThrottle.set(false)
                }
            }
            cameraCaptureSession?.apply {
                stopRepeating()
                abortCaptures()
                capture(captureBuilder?.build(), captureCallback, null)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
        Log.d(TAG, "captureStillPicture end")
    }

    private fun updatePreview() {
        if (cameraDevice == null)
            Log.e(TAG, "updatePreview: cameraDevice is null")
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        try {
            captureRequest = captureRequestBuilder.build()
            cameraCaptureSession?.setRepeatingRequest(captureRequest, null,
                    mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "OnResume")
        startBackgroundThread()
        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        stopBackgroundThread()
        super.onPause()
    }

    override fun onFailure(p0: Exception) {
        toast(p0.message.toString())
    }

    override fun onSuccess(bmp: Bitmap) {
        if (!modelReady.get()) {
            toast("Recognition model not ready yet!")
            return
        }

        val tmpImgFile = BmpOperations.writeBmpToTmpFile(bmp, this)
        val studentId = fisherFaces.predictImage(tmpImgFile.absolutePath)
        if (studentId == -1) {
            onFailure(java.lang.Exception("I'm not sure, please try again."))
            return
        }
        val student = classObj.studentList.single { it.id == studentId }
        predicted_student_id.text = student.id.toString()
        predicted_student_name.text = student.name
        if (!arrivedStudents.contains(student)) {
            arrivedStudents.add(student)
            if (btn_fix.visibility == View.GONE) {
                btn_fix.visibility = View.VISIBLE
                doAsync {
                    sleep(4000)
                    btn_fix.visibility = View.GONE
                }
            }
        } else {
            toast("${student.name} is Already Registered :)")
            btn_fix.visibility = View.GONE
        }
    }
}

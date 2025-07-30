package com.example.mpoxfriend

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var selectImageButton: Button
    private lateinit var takePhotoButton: Button
    private lateinit var predictButton: Button
    private lateinit var imageView: ImageView
    private lateinit var resultTextView: TextView
    private var selectedImageUri: Uri? = null
    private var tempPhotoFile: File? = null

    private val apiClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiPredictUrl: String = BuildConfig.BASE_URL + "api/predict/"

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                imageView.setImageURI(selectedImageUri)
                resultTextView.text = "Gambar dipilih dari galeri. Klik Prediksi."
                tempPhotoFile = null
            } ?: run {
                resultTextView.text = "Gagal memilih gambar dari galeri."
            }
        }
    }

    private fun showPermissionRationaleDialog(permission: String, message: String, onPositive: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Izin Diperlukan")
            .setMessage(message)
            .setPositiveButton("Berikan Izin") { dialog, which ->
                onPositive()
            }
            .setNegativeButton("Batal") { dialog, which ->
                dialog.dismiss()
                Toast.makeText(this, "Aplikasi tidak dapat berfungsi tanpa izin tersebut.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success) {
            tempPhotoFile?.let { file ->
                selectedImageUri = Uri.fromFile(file)
                imageView.setImageURI(selectedImageUri)
                resultTextView.text = "Foto diambil dari kamera. Klik Prediksi."
            }
        } else {
            tempPhotoFile?.delete()
            tempPhotoFile = null
            selectedImageUri = null
            Toast.makeText(this, "Pengambilan foto dibatalkan atau gagal.", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openGallery()
            } else {
                showPermissionDeniedDialog(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                dispatchTakePictureIntent()
            } else {
                showPermissionDeniedDialog(Manifest.permission.CAMERA)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectImageButton = findViewById(R.id.select_image_button)
        takePhotoButton = findViewById(R.id.take_photo_button)
        predictButton = findViewById(R.id.predict_button)
        imageView = findViewById(R.id.image_view)
        resultTextView = findViewById(R.id.result_text_view)

        selectImageButton.setOnClickListener {
            checkAndRequestStoragePermission()
        }

        takePhotoButton.setOnClickListener {
            checkAndRequestCameraPermission()
        }

        predictButton.setOnClickListener {
            selectedImageUri?.let { uri ->
                uploadImageToApi(uri)
            } ?: run {
                resultTextView.text = "Mohon pilih atau ambil gambar terlebih dahulu!"
            }
        }
    }

    // Memeriksa dan meminta izin penyimpanan
    private fun checkAndRequestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            openGallery()
        } else {
            if (shouldShowRequestPermissionRationale(permission)) {
                showPermissionRationaleDialog(permission, "Untuk dapat memilih gambar dari galeri, aplikasi memerlukan akses ke penyimpanan media Anda.") {
                    requestStoragePermissionLauncher.launch(permission)
                }
            } else {
                requestStoragePermissionLauncher.launch(permission)
            }
        }
    }

    private fun checkAndRequestCameraPermission() {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            dispatchTakePictureIntent()
        } else {
            requestCameraPermissionLauncher.launch(permission)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            tempPhotoFile = try {
                createImageFile()
            } catch (ex: IOException) {
                Toast.makeText(this, "Gagal membuat file gambar sementara.", Toast.LENGTH_SHORT).show()
                Log.e("CAMERA", "Error creating image file", ex)
                null
            }
            tempPhotoFile?.also {
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    applicationContext.packageName + ".fileprovider",
                    it
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePhotoLauncher.launch(photoURI)
            }
        } else {
            Toast.makeText(this, "Tidak ada aplikasi kamera yang ditemukan.", Toast.LENGTH_SHORT).show()
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir: File? = externalCacheDir

        if (storageDir == null) {
            throw IOException("Gagal mendapatkan direktori penyimpanan cache eksternal.")
        }
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val imageFile = File.createTempFile(
            imageFileName, /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
        return imageFile
    }

    private fun uploadImageToApi(imageUri: Uri) {
        resultTextView.text = "Mengirim gambar ke API..."
        Log.d("API_CALL", "Mulai uploadImageToApi untuk URI: $imageUri")

        val fileToUpload: File? = try {
            copyUriToFile(imageUri, cacheDir)
        } catch (e: IOException) {
            Log.e("API_CALL", "Error menyalin URI ke file sementara: ${e.message}", e)
            resultTextView.text = "Gagal memproses gambar untuk diunggah."
            null
        }

        if (fileToUpload == null || !fileToUpload.exists() || !fileToUpload.canRead()) {
            resultTextView.text = "File gambar tidak ditemukan atau tidak dapat dibaca untuk diunggah."
            Log.e("API_CALL", "FileToUpload null atau tidak dapat diakses.")
            return
        }

        val client = apiClient

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                fileToUpload.name,
                fileToUpload.asRequestBody("image/*".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(apiPredictUrl)
            .post(requestBody)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d("API_CALL", "Mencoba membuat OkHttp call...")
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                Log.d("API_CALL", "Respons diterima. Code: ${response.code}, Body: $responseBody")

                fileToUpload.delete()
                Log.d("API_CALL", "File sementara dihapus: ${fileToUpload.absolutePath}")

                launch(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val jsonResponse = JSONObject(responseBody)
                        val prediksi = jsonResponse.getInt("prediksi")

                        resultTextView.text = "Prediksi Model: $prediksi"

                        val classNames = arrayOf("Chickenpox", "Measles", "Monkeypox", "Normal")
                        if (prediksi >= 0 && prediksi < classNames.size) {
                            resultTextView.append("\n(${classNames[prediksi]})")
                        }

                    } else {
                        resultTextView.text = "Error API: ${response.code} - $responseBody"
                        Log.e("API_CALL", "API Tidak Sukses: ${response.code} - $responseBody")
                    }
                }
            } catch (e: IOException) {
                launch(Dispatchers.Main) {
                    resultTextView.text = "Koneksi Error: ${e.message}"
                    Log.e("API_CALL", "Koneksi Error (IOException): ${e.message}", e)
                }
                e.printStackTrace()
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    resultTextView.text = "Error Tak Terduga: ${e.message}"
                    Log.e("API_CALL", "Error Tak Terduga: ${e.message}", e)
                }
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class)
    private fun copyUriToFile(uri: Uri, destDir: File): File? {
        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        if (inputStream == null) {
            Log.e("COPY_URI", "Tidak dapat membuka InputStream dari URI: $uri")
            return null
        }

        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        val tempFileName = "upload_${timeStamp}.tmp"
        val tempFile = File(destDir, tempFileName)

        FileOutputStream(tempFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }

        inputStream.close()
        Log.d("COPY_URI", "File sementara dibuat: ${tempFile.absolutePath}, Size: ${tempFile.length()}")
        return tempFile
    }

    private fun showPermissionDeniedDialog(permission: String) {
        val permissionName = when(permission) {
            Manifest.permission.READ_MEDIA_IMAGES -> "Akses Media"
            Manifest.permission.CAMERA -> "Kamera"
            else -> "Izin Tidak Dikenali"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Izin Ditolak Permanen")
            .setMessage("Anda telah menolak izin $permissionName secara permanen. Silakan berikan izin secara manual melalui Pengaturan Aplikasi.")
            .setPositiveButton("Buka Pengaturan") { dialog, which ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Batal") { dialog, which ->
                dialog.dismiss()
                Toast.makeText(this, "Aplikasi tidak dapat berfungsi tanpa izin tersebut.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
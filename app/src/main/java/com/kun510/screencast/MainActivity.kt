package com.kun510.screencast

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.kun510.screencast.Constant.SERVER_CONNECT
import io.reactivex.disposables.Disposable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var disposable: Disposable? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        findViewById<Button>(R.id.startBtn).setOnClickListener {
            startActivityForResult(mediaProjectionManager!!.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE)
        }
        findViewById<TextView>(R.id.textViewIP).text = getString(R.string.ip, SERVER_CONNECT)
        if (!_startedNodeAlready) {
            _startedNodeAlready = true
            Thread {
                // The path where we expect the node project to be at runtime.
                val nodeDir = applicationContext.filesDir.absolutePath + "/nodejs-project"
                if (wasAPKUpdated()) {
                    // Recursively delete any existing nodejs-project.
                    val nodeDirReference = File(nodeDir)
                    if (nodeDirReference.exists()) {
                        deleteFolderRecursively(File(nodeDir))
                    }
                    // Copy the node project from assets into the application's data path.
                    copyAssetFolder(applicationContext.assets, "nodejs-project", nodeDir)

                    saveLastUpdateTime()
                }
                startNodeWithArguments(
                    arrayOf(
                        "node",
                        "$nodeDir/index.js"
                    )
                )
            }.start()
        }
    }
    external fun startNodeWithArguments(arguments: Array<String>): Int
    private fun wasAPKUpdated(): Boolean {
        val prefs: SharedPreferences = applicationContext.getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE)
        val previousLastUpdateTime = prefs.getLong("NODEJS_MOBILE_APK_LastUpdateTime", 0)
        var lastUpdateTime: Long = 1
        try {
            val packageInfo: PackageInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
            lastUpdateTime = packageInfo.lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return lastUpdateTime != previousLastUpdateTime
    }
    private fun saveLastUpdateTime() {
        var lastUpdateTime: Long = 1
        try {
            val packageInfo: PackageInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
            lastUpdateTime = packageInfo.lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        val prefs: SharedPreferences = applicationContext.getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = prefs.edit()
        editor.putLong("NODEJS_MOBILE_APK_LastUpdateTime", lastUpdateTime)
        editor.apply()
    }

    private fun deleteFolderRecursively(file: File): Boolean {
        return try {
            var res = true
            file.listFiles()?.let { files ->
                for (childFile in files) {
                    if (childFile.isDirectory) {
                        res = res && deleteFolderRecursively(childFile)
                    } else {
                        res = res && childFile.delete()
                    }
                }
            }
            res && file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toPath: String): Boolean {
        return try {
            val files = assetManager.list(fromAssetPath)
            var res = true

            if (files?.isEmpty() == true) {
                // If it's a file, it won't have any assets "inside" it.
                res = res && copyAsset(assetManager, fromAssetPath, toPath)
            } else {
                File(toPath).mkdirs()
                files?.forEach { file ->
                    res = res && copyAssetFolder(assetManager, "$fromAssetPath/$file", "$toPath/$file")
                }
            }
            res
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    private fun copyAsset(assetManager: AssetManager, fromAssetPath: String, toPath: String): Boolean {
        var `in`: InputStream? = null
        var out: OutputStream? = null
        return try {
            `in` = assetManager.open(fromAssetPath)
            File(toPath).createNewFile()
            out = FileOutputStream(toPath)
            copyFile(`in`, out)
            `in`?.close()
            out.flush()
            out.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            `in`?.close()
            out?.close()
        }
    }
    @Throws(IOException::class)
    private fun copyFile(`in`: InputStream, out: OutputStream) {
        val buffer = ByteArray(1024)
        var read: Int
        while (`in`.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
        }
    }
    override fun onDestroy() {
        disposable?.dispose()
        disposable = null
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (Activity.RESULT_OK != resultCode) {
                Toast.makeText(this, "Screen cast permission denied", Toast.LENGTH_SHORT).show()
                return
            }

            if (null == data) {
                Toast.makeText(this, "No screen cast found", Toast.LENGTH_SHORT).show()
                return
            }

            ContextCompat.startForegroundService(applicationContext, ScreenCastService.getIntent(applicationContext, data))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.getStringExtra(EXTRA_DATA) ?: return
        when (action) {
            ACTION_STOP_STREAM -> {
                stopService(ScreenCastService.getIntent(applicationContext))
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_SCREEN_CAPTURE = 10001
        const val ACTION_STOP_STREAM = "ACTION_STOP_STREAM"
        private const val EXTRA_DATA = "EXTRA_DATA"
        var _startedNodeAlready = false
        init {
            System.loadLibrary("native-lib")
            System.loadLibrary("node")
        }
        fun getStartIntent(context: Context, action: String): Intent {
            return Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_DATA, action)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

    }
}

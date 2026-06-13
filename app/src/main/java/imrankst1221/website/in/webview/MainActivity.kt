package imrankst1221.website.`in`.webview

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    
    companion object {
        const val SPEAKER = 1
        const val EARPIECE = 0
        const val PERMISSION_REQUEST_CODE = 123
    }

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this)
        setContentView(webView)

        // Screen on rakhne ke liye taaki call kate nahi
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        configureWebView()

        if (checkRuntimePermissions()) {
            loadChatWebsite()
        } else {
            ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }
            if (allGranted) {
                Toast.makeText(this, "Permissions Granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Mic and Camera access required for calls.", Toast.LENGTH_LONG).show()
            }
            loadChatWebsite()
        }
    }

    private fun configureWebView() {
        val settings = webView.settings
        
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.addJavascriptInterface(AudioToggle(this), "AudioToggle")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectAudioToggleConstants(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectAudioToggleConstants(view)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    request.grant(request.resources)
                }
            }
        }
    }

    private fun injectAudioToggleConstants(view: WebView?) {
        val jsSnippet = """
            if (typeof window.AudioToggle !== 'undefined') {
                window.AudioToggle.SPEAKER = $SPEAKER;
                window.AudioToggle.EARPIECE = $EARPIECE;
            }
        """.trimIndent()
        view?.evaluateJavascript(jsSnippet, null)
    }

    private fun loadChatWebsite() {
        webView.loadUrl("https://secure-talk.42web.io/")
    }

    private fun checkRuntimePermissions(): Boolean {
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    inner class AudioToggle(private val context: Context) {
        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        @JavascriptInterface
        fun setAudioMode(mode: Int) {
            Handler(Looper.getMainLooper()).post {
                try {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    
                    if (mode == SPEAKER) {
                        audioManager.isSpeakerphoneOn = true
                        Toast.makeText(context, "Speaker Mode Active", Toast.LENGTH_SHORT).show()
                    } else if (mode == EARPIECE) {
                        audioManager.isSpeakerphoneOn = false
                        Toast.makeText(context, "Earpiece Mode Active", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

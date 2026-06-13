package imrankst1221.website.`in`.webview


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    
    // Constant modes matching call (1).php window.AudioToggle calls
    companion object {
        const val SPEAKER = 1
        const val EARPIECE = 0
    }

    // Required Android Runtime Permissions for interactive WebRTC calls
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    )

    // Modern Activity Result Contract to request Camera and Mic permissions safely
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        
        if (recordGranted && cameraGranted) {
            Toast.makeText(this, "Permissions Granted! Loading secure-talk...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Microphone and Camera accesses are required for calls.", Toast.LENGTH_LONG).show()
        }
        // Proceed to load the WebView viewport regardless
        loadChatWebsite()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup simple layout container with a WebView
        webView = WebView(this)
        setContentView(webView)

        // Keep screen awake during interactive talking sessions to prevent standby disconnects
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        configureWebView()

        if (checkRuntimePermissions()) {
            loadChatWebsite()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    private fun configureWebView() {
        val settings = webView.settings
        
        // 1. Enable Core Engines
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        
        // 2. Explicitly enable media playback without requiring direct user action gestures first
        settings.mediaPlaybackRequiresUserGesture = false
        
        // Viewport scale setup
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        
        // Bypass mixed content limitations for local media capture pipelines if needed
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // 3. Register the explicit "AudioToggle" JS bridge bound to matching window scope
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

        // 4. Override WebChromeClient callback to instantly grant WebRTC media capture streams
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    // Grant permissions for RESOURCE_AUDIO_CAPTURE and RESOURCE_VIDEO_CAPTURE
                    request.grant(request.resources)
                }
            }
        }
    }

    /**
     * Pre-injects expected Javascript constants: window.AudioToggle.SPEAKER = 1, EARPIECE = 0
     * which call (1).php requires for its audio switches.
     */
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
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Core JavascriptInterface bridge exposed as window.AudioToggle in the WebView scope.
     */
    inner class AudioToggle(private val context: Context) {
        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        @JavascriptInterface
        fun setAudioMode(mode: Int) {
            Handler(Looper.getMainLooper()).post {
                try {
                    // Set audio mode to COMMUNICATING for VoIP calling parameters
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    
                    if (mode == SPEAKER) {
                        // Switch device stream path to open speakerphone
                        audioManager.isSpeakerphoneOn = true
                        Toast.makeText(context, "Speaker Mode Active", Toast.LENGTH_SHORT).show()
                    } else if (mode == EARPIECE) {
                        // Switch device stream path to internal handset earpiece speaker
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

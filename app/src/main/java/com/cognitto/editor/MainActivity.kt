package com.cognitto.editor

import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.view.WindowManager
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var pendingSave = false
    private var pendingName = "untitled.txt"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 14, 18)
        window.navigationBarColor = Color.rgb(12, 14, 18)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        web = WebView(this).apply {
            setBackgroundColor(Color.rgb(12, 14, 18))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.setSupportZoom(false)
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(Bridge(), "Cognitto")
            loadUrl("file:///android_asset/editor.html")
        }
        setContentView(web)
    }

    inner class Bridge {
        @JavascriptInterface fun saveFile(name: String, content: String) {
            runOnUiThread {
                pendingName = name.ifBlank { "untitled.txt" }
                pendingSave = true
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeFor(pendingName)
                    putExtra(Intent.EXTRA_TITLE, pendingName)
                }
                startActivityForResult(intent, 41)
                saveBuffer = content
            }
        }
        @JavascriptInterface fun openFile() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, 42)
            }
        }
        @JavascriptInterface fun toast(message: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
        }
    }

    private var saveBuffer = ""
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        try {
            if (requestCode == 41 && pendingSave) {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(saveBuffer) }
                pendingSave = false
                web.evaluateJavascript("window.fileSaved()", null)
            } else if (requestCode == 42) {
                val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val name = queryName(uri)
                val escaped = org.json.JSONObject.quote(content)
                val escapedName = org.json.JSONObject.quote(name)
                web.evaluateJavascript("window.loadOpenedFile($escapedName,$escaped)", null)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "File operation failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun queryName(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/') ?: "opened-file.txt"
    private fun mimeFor(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js" -> "text/javascript"
        "py" -> "text/x-python"
        else -> "text/plain"
    }

    override fun onBackPressed() {
        web.evaluateJavascript("window.handleBack()", null)
    }
}

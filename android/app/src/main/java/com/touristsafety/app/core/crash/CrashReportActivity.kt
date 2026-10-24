package com.touristsafety.app.core.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.touristsafety.app.MainActivity

class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val errorDetails = intent.getStringExtra(EXTRA_ERROR_DETAILS)
            ?: "No error details available."

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(40, 60, 40, 40)
        }

        val titleView = TextView(this).apply {
            text = "⚠️ TrailGuard Diagnostic Alert"
            textSize = 20f
            setTextColor(Color.parseColor("#FF5252"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 20)
        }
        rootLayout.addView(titleView)

        val subtitleView = TextView(this).apply {
            text = "The application encountered a problem during startup. Technical details:"
            textSize = 14f
            setTextColor(Color.parseColor("#E0E0E0"))
            setPadding(0, 0, 0, 24)
        }
        rootLayout.addView(subtitleView)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(24, 24, 24, 24)
        }

        val errorTextView = TextView(this).apply {
            text = errorDetails
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView.addView(errorTextView)
        rootLayout.addView(scrollView)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 0)
        }

        val copyButton = Button(this).apply {
            text = "Copy Diagnostic"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                marginEnd = 16
            }
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("TrailGuard Crash Diagnostic", errorDetails)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@CrashReportActivity, "Diagnostic copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        buttonLayout.addView(copyButton)

        val restartButton = Button(this).apply {
            text = "Restart App"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            setOnClickListener {
                val restartIntent = Intent(this@CrashReportActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(restartIntent)
                finish()
            }
        }
        buttonLayout.addView(restartButton)

        rootLayout.addView(buttonLayout)
        setContentView(rootLayout)
    }

    companion object {
        const val EXTRA_ERROR_DETAILS = "extra_error_details"
    }
}


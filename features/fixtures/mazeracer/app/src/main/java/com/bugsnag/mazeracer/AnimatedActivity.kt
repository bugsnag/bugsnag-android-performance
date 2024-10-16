package com.bugsnag.mazeracer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.ActionBar.LayoutParams
import androidx.appcompat.app.AppCompatActivity
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

class AnimatedActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private var span: Span? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        span =
            BugsnagPerformance.startSpan(
                "Slow Animation",
                SpanOptions.within(null)
                    .makeCurrentContext(true)
                    .setFirstClass(true),
            )

        setContentView(
            BadPainterView(this) {
                span?.end()
                finish()
            },
        )
    }

    /**
     * Animations are disabled by default on most test devices, so we run our own View with
     * a fake animation that ignores all of the system settings. This lets us force some dropped
     * and frozen frames.
     */
    private class BadPainterView(
        context: Context?,
        private val onTestComplete: () -> Unit,
    ) : View(context),
        Runnable {
        init {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        private var frameIndex = 0

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            frameIndex = 0
        }

        @Suppress("MagicNumber")
        override fun onDraw(canvas: Canvas) {
            frameIndex++

            if (frameIndex % 5 == 0) {
                // delay enough to drop a frame or two
                Thread.sleep(30L)
            }

            if (frameIndex == 50) {
                // simulate a frozen frame
                Thread.sleep(800L)
            }

            canvas.drawColor(Color.rgb(frameIndex % 255, 0, 0))

            if (frameIndex < 120) {
                handler.postDelayed(this, 16L)
            } else {
                handler.post { onTestComplete() }
            }
        }

        override fun run() {
            invalidate()
        }
    }
}

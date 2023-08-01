package com.bugsnag.android.performance.test

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Build

enum class ActivityLifecycleStep {
    DESTROYED {
        override fun advance(
            activity: Activity,
            callbacks: ActivityLifecycleCallbacks
        ): ActivityLifecycleStep {
            callbacks.lifecycleStep(
                activity,
                { onActivityPreCreated(it, null) },
                { onActivityCreated(it, null) },
                { onActivityPostCreated(it, null) },
            )
            return CREATED
        }

        override fun backward(
            activity: Activity,
            callbacks: ActivityLifecycleCallbacks
        ): ActivityLifecycleStep {
            return this
        }
    },
    CREATED {
        override fun advance(
            activity: Activity,
            callbacks: ActivityLifecycleCallbacks
        ): ActivityLifecycleStep {
            callbacks.lifecycleStep(
                activity,
                ActivityLifecycleCallbacks::onActivityPreStarted,
                ActivityLifecycleCallbacks::onActivityStarted,
                ActivityLifecycleCallbacks::onActivityPostStarted,
            )
            return STARTED
        }

        override fun backward(
            activity: Activity,
            callbacks: ActivityLifecycleCallbacks
        ): ActivityLifecycleStep {
            callbacks.lifecycleStep(
                activity,
                ActivityLifecycleCallbacks::onActivityPreDestroyed,
                ActivityLifecycleCallbacks::onActivityDestroyed,
                ActivityLifecycleCallbacks::onActivityPostDestroyed,
            )
            return DESTROYED
        }
    },
    STARTED {
        override fun advance(
            activity: Activity,
            callbacks: ActivityLifecycleCallbacks
        ): ActivityLifecycleStep {
            callbacks.lifecycleStep(
                activity,
                ActivityLifecycleCallbacks::onActivityPreResumed,
                ActivityLifecycleCallbacks::onActivityResumed,
                ActivityLifecycleCallbacks::onActivityPostResumed,
            )
            return RESUMED
        }

        override fun backward(
            activity: Activity,
            callbacks: ActivityLifecycleCallbacks
        ): ActivityLifecycleStep {
            callbacks.lifecycleStep(
                activity,
                ActivityLifecycleCallbacks::onActivityPreStopped,
                ActivityLifecycleCallbacks::onActivityStopped,
                ActivityLifecycleCallbacks::onActivityPostStopped,
            )
            return CREATED
        }
    },
    RESUMED {
        override fun advance(
            activity: Activity,
            callbacks: ActivityLifecycleCallbacks
        ): ActivityLifecycleStep {
            return this
        }

        override fun backward(
            activity: Activity,
            callbacks: ActivityLifecycleCallbacks
        ): ActivityLifecycleStep {
            callbacks.lifecycleStep(
                activity,
                ActivityLifecycleCallbacks::onActivityPrePaused,
                ActivityLifecycleCallbacks::onActivityPaused,
                ActivityLifecycleCallbacks::onActivityPostPaused,
            )
            return STARTED
        }
    };

    protected inline fun ActivityLifecycleCallbacks.lifecycleStep(
        activity: Activity,
        pre: ActivityLifecycleCallbacks.(Activity) -> Unit,
        step: ActivityLifecycleCallbacks.(Activity) -> Unit,
        post: ActivityLifecycleCallbacks.(Activity) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pre(activity)
        }

        step(activity)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            post(activity)
        }
    }

    abstract fun advance(
        activity: Activity,
        callbacks: ActivityLifecycleCallbacks
    ): ActivityLifecycleStep

    abstract fun backward(
        activity: Activity,
        callbacks: ActivityLifecycleCallbacks
    ): ActivityLifecycleStep
}

/**
 * Simple test utility that invokes `ActivityLifecycleCallbacks` to progress between lifecycle
 * states. No state is maintained internally.
 */
class ActivityLifecycleHelper(
    private val callbacks: ActivityLifecycleCallbacks,
    /**
     * Called between each state change (created -> started, started -> destroyed)
     */
    private val stepCallback: () -> Unit = {}
) {
    fun progressLifecycle(
        activity: Activity,
        from: ActivityLifecycleStep,
        to: ActivityLifecycleStep
    ) {
        when {
            from < to -> {
                var step = from
                while (step != to) {
                    step = step.advance(activity, callbacks)
                    stepCallback()
                }
            }

            from > to -> {
                var step = from
                while (step != to) {
                    step = step.backward(activity, callbacks)
                    stepCallback()
                }
            }
        }
    }
}

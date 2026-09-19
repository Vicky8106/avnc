/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vncandroid.free.pollingAssert
import com.vncandroid.free.runOnMainSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CancellationException
import kotlin.concurrent.thread
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(AndroidJUnit4::class)
class LiveRequestTest {

    @Test
    fun simpleRequest() {
        val liveRequest = LiveRequest<Any, Any>()
        val requestData = Any()
        val responseData = Any()
        var result: Any? = null

        runOnMainSync {
            liveRequest.observe(LiveEventTest.ActiveOwner()) {
                Assert.assertSame(requestData, liveRequest.value)
                liveRequest.offerResponse(responseData)
            }
        }
        thread {
            result = liveRequest.getResponseFor(requestData, CoroutineScope(EmptyCoroutineContext))
        }
        pollingAssert {
            Assert.assertSame(responseData, result)
        }
    }

    @Test
    fun requestShouldBeCanceledOnScopeTermination() {
        val scope = CoroutineScope(EmptyCoroutineContext)
        val liveRequest = LiveRequest<Any, Any>()

        val t = thread {
            Assert.assertThrows(CancellationException::class.java) {
                liveRequest.getResponseFor(Any(), scope)
            }
        }

        scope.cancel()
        t.join()
    }

    @Test
    fun requestShouldBeCanceledOnAlreadyTerminatedScope() {
        val scope = CoroutineScope(EmptyCoroutineContext).apply { cancel() }
        val liveRequest = LiveRequest<Any, Any>()

        Assert.assertThrows(CancellationException::class.java) {
            liveRequest.getResponseFor(Any(), scope)
        }
    }
}
package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.AutoRefreshManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Suru Auto", appName)
  }

  @Test
  fun `test AutoRefreshManager url and interval updates`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    AutoRefreshManager.init(context)

    AutoRefreshManager.setUrl("example.com")
    assertEquals("https://example.com", AutoRefreshManager.state.value.url)

    AutoRefreshManager.setInterval(300)
    assertEquals(300, AutoRefreshManager.state.value.intervalSeconds)

    AutoRefreshManager.start()
    assertTrue(AutoRefreshManager.state.value.isRunning)

    AutoRefreshManager.pause()
    assertTrue(AutoRefreshManager.state.value.isPaused)

    AutoRefreshManager.stop()
    assertTrue(!AutoRefreshManager.state.value.isRunning)
  }

  @Test
  fun `test AutoRefreshManager updates current active url dynamically during navigation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    AutoRefreshManager.init(context)

    AutoRefreshManager.setUrl("https://example.com")
    assertEquals("https://example.com", AutoRefreshManager.state.value.url)

    // User navigates within the WebView to another page
    AutoRefreshManager.updatePageStatus(
      url = "https://example.com/dashboard/orders",
      title = "Orders",
      progress = 45,
      isLoading = true
    )

    assertEquals("https://example.com/dashboard/orders", AutoRefreshManager.state.value.url)
    assertEquals(45, AutoRefreshManager.state.value.pageProgress)
    assertTrue(AutoRefreshManager.state.value.isLoading)

    // Page finished loading
    AutoRefreshManager.updatePageStatus(
      url = "https://example.com/dashboard/orders",
      title = "Orders Completed",
      progress = 100,
      isLoading = false
    )
    assertEquals(false, AutoRefreshManager.state.value.isLoading)
    assertEquals("https://example.com/dashboard/orders", AutoRefreshManager.state.value.url)
  }
}

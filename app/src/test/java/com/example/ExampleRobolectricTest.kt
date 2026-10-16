package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.CryptoEngine
import com.example.optimizer.SmartTransferOptimizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertEquals("LinkDrop", appName)
  }

  @Test
  fun `crypto engine generates valid pin and hashes`() {
    val pin = CryptoEngine.generateVerificationPin()
    assertEquals(4, pin.length)

    val testBytes = "Hello LinkDrop".toByteArray()
    val hash = CryptoEngine.sha256(testBytes)
    assertEquals(64, hash.length)

    val key = CryptoEngine.generateSessionKey()
    val encrypted = CryptoEngine.encrypt(testBytes, key)
    val decrypted = CryptoEngine.decrypt(encrypted, key)
    assertEquals("Hello LinkDrop", String(decrypted))
  }

  @Test
  fun `smart transfer optimizer analyzes batch`() {
    val files = listOf("report.docx", "data.json", "log.txt")
    val recommendation = SmartTransferOptimizer.analyzeBatch(
      fileNames = files,
      totalSizeBytes = 50 * 1024 * 1024L,
      isWifiConnected = true
    )
    assertNotNull(recommendation)
    assertTrue(recommendation.enableCompression)
  }
}

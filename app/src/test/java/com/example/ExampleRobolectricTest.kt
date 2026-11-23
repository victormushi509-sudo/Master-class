package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    assertEquals("Victor Video", appName)
  }

  @Test
  fun `scene list json roundtrip preserves scenes`() {
    val scenes = listOf(
      com.example.data.model.SceneItem(
        sceneNumber = 1,
        title = "Opening",
        narrationText = "Welcome to Victor Video.",
        subtitleText = "Welcome to Victor Video.",
        visualPrompt = "Dark navy studio with glowing blue lights",
        motionDirection = "zoom_in",
        durationSeconds = 5
      )
    )
    val json = com.example.data.model.SceneItem.listToJson(scenes)
    val parsed = com.example.data.model.SceneItem.listFromJson(json)
    assertEquals(1, parsed.size)
    assertEquals("Opening", parsed.first().title)
    assertEquals(5, parsed.first().durationSeconds)
  }
}

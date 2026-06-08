package com.helucryptic.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun complete_onboarding_wizard_lands_on_chat_list() {
        hilt.inject()
        compose.onNodeWithText("Pick a username").assertIsDisplayed()
        compose.onNodeWithTag("username_field").performTextInput("testuser")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Looks good →").performClick()
        compose.onNodeWithText("Skip, use default").performClick()
        compose.onNodeWithText("helucryptic").assertIsDisplayed()
    }
}

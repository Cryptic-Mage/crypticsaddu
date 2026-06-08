package com.helucryptic.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.helucryptic.android.crypto.IdentityStore
import com.helucryptic.android.crypto.IdentityKeys
import com.helucryptic.android.data.repository.ContactRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ChatTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var identityStore: IdentityStore

    @Inject
    lateinit var contactRepository: ContactRepository

    @Before
    fun setUp() {
        hilt.inject()
        // Save identity so we skip onboarding
        identityStore.saveIdentity(
            username = "alice",
            keys = IdentityKeys(
                x25519Priv = "xPrivVal",
                x25519Pub = "xPubVal",
                ed25519Priv = "edPrivVal",
                ed25519Pub = "edPubVal"
            )
        )
        // Seed a contact
        runBlocking {
            contactRepository.upsertFromHello("bob", "edPubValBob", "xPubValBob")
        }
    }

    @Test
    fun send_message_bubble_appears() {
        // Wait for contact to appear on ChatListScreen and click
        compose.onNodeWithText("bob").assertIsDisplayed()
        compose.onNodeWithText("bob").performClick()
        
        // Verify we navigated to ChatScreen and type in input
        compose.onNodeWithTag("message_input").performTextInput("hello there")
        compose.onNodeWithContentDescription("Send").performClick()
        
        // Assert the sent message bubble is displayed
        compose.onNodeWithText("hello there").assertIsDisplayed()
    }
}

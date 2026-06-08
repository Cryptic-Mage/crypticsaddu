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
class ContactsTest {
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
        // Seed an unverified contact
        runBlocking {
            contactRepository.upsertFromHello("bob", "edPubValBob", "xPubValBob")
        }
    }

    @Test
    fun mark_contact_as_verified_shows_badge() {
        // Wait for main screen and click on Contacts bottom navigation tab
        compose.onNodeWithText("Contacts").performClick()

        // Wait for contacts list to display bob and click the row
        compose.onNodeWithTag("contact_row_bob").performClick()

        // Verify we navigated to ContactDetailScreen and click Mark as Verified
        compose.onNodeWithText("Mark as Verified").performClick()

        // Assert the Verified status badge/text is shown
        compose.onNodeWithText("Verified").assertIsDisplayed()
    }
}

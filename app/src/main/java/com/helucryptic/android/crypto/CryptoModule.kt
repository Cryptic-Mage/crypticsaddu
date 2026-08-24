package com.helucryptic.android.crypto

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {
    @Provides @Singleton
    fun provideCryptoManager() = CryptoManager()
    // IdentityStore uses @Inject constructor(@ApplicationContext) - Hilt auto-provides it.
}

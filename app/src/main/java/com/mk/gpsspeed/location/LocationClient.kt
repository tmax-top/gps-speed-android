package com.mk.gpsspeed.location

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface LocationClient {
    fun locationUpdates(intervalMillis: Long): Flow<Location>
}

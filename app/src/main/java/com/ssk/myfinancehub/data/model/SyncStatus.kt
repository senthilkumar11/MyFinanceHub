package com.ssk.myfinancehub.data.model

enum class SyncStatus {
    LOCAL,          // Only exists locally
    SYNCED,         // Successfully synced with Catalyst
    SYNC_PENDING,   // Waiting to be synced
    SYNC_FAILED     // Sync failed
}

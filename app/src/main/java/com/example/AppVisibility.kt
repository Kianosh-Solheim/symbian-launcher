package com.example

/** Whether the launcher is currently on screen; lets background work defer until it is. */
object AppVisibility {
    @Volatile var visible: Boolean = false
}

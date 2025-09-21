package com.screen.recorder

enum class AudioMode(val modeValue: Int) {
    NONE(0),
    MEDIA(1),
    MEDIA_MIC(2);

    companion object {
        fun fromValue(value: Int): AudioMode {
            return AudioMode.entries.find { it.modeValue == value } ?: NONE
        }
    }
}

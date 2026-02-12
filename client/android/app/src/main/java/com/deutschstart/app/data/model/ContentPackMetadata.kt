package com.deutschstart.app.data.model

import com.google.gson.annotations.SerializedName

data class ContentPackMetadata(
    @SerializedName("filename") val filename: String,
    @SerializedName("url") val url: String,
    @SerializedName("size") val size: Long,
    @SerializedName("created_at") val createdAt: Double
)

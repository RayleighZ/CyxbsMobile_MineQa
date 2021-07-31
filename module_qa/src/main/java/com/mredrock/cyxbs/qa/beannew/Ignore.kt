package com.mredrock.cyxbs.qa.beannew

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * @date 2021-03-05
 * @author Sca RayleighZ
 */
data class Ignore(
        @SerializedName("uid")
        val uid: String,
        @SerializedName("avatar")
        val avatar: String,
        @SerializedName("nickName")
        val nickName: String,
        @SerializedName("introduction")
        val introduction: String
): Serializable

/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * KizzyRepositoryImpl.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.bt.bttune.kizzy.repository

import com.bt.bttune.kizzy.remote.ApiService
import com.bt.bttune.kizzy.utils.toImageAsset

/**
 * Modified by Zion Huang
 */
class KizzyRepository {
    private val api = ApiService()

    suspend fun getImage(url: String): String? {
        return api.getImage(url).getOrNull()?.toImageAsset()
    }
}

package com.kochvaia.app.data.repo

import com.kochvaia.app.data.remote.ApiService
import com.kochvaia.app.data.remote.CreateKidRequest
import com.kochvaia.app.data.remote.KidDto
import com.kochvaia.app.data.remote.MeResponse
import com.kochvaia.app.data.remote.PatchKidRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KidRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun me(): MeResponse = api.me()
    suspend fun list(): List<KidDto> = api.listKids().kids
    suspend fun add(name: String, emoji: String?, color: String?): KidDto =
        api.createKid(CreateKidRequest(displayName = name, avatarEmoji = emoji, avatarColor = color))
    suspend fun rename(id: String, newName: String) =
        api.patchKid(id, PatchKidRequest(displayName = newName))
    suspend fun delete(id: String) = api.deleteKid(id)
}

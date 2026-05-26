package com.kochvaia.app.data.repo

import com.kochvaia.app.data.remote.ApiService
import com.kochvaia.app.data.remote.CreateItemRequest
import com.kochvaia.app.data.remote.ItemDto
import com.kochvaia.app.data.remote.PatchItemRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun list(): List<ItemDto> = api.listItems().items
    suspend fun add(name: String, costStars: Int, emoji: String?): ItemDto =
        api.createItem(CreateItemRequest(name, costStars, emoji))
    suspend fun edit(id: String, name: String?, costStars: Int?, emoji: String?) =
        api.patchItem(id, PatchItemRequest(name, costStars, emoji))
    suspend fun delete(id: String) = api.deleteItem(id)
}

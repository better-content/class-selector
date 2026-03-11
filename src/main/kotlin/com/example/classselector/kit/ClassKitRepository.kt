package com.example.classselector.kit

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager

object ClassKitRepository {
    private val gson = Gson()
    private val type = object : TypeToken<List<ClassKit>>() {}.type
    private val kitsPath = ResourceLocation("classselector", "class_kits/kits.json")

    @Volatile
    private var cached: List<ClassKit> = emptyList()

    fun parse(json: String): List<ClassKit> = gson.fromJson<List<ClassKit>>(json, type) ?: emptyList()

    fun load(resourceManager: ResourceManager): List<ClassKit> {
        val resource = resourceManager.getResource(kitsPath).orElseThrow {
            IllegalStateException("Missing class kit json at $kitsPath")
        }
        val parsed = resource.openAsReader().use { parse(it.readText()) }
        require(parsed.isNotEmpty()) { "No class kits defined at $kitsPath" }
        require(parsed.all { it.id.isNotBlank() }) { "Every class kit must have a non-blank id" }
        val ids = parsed.map { it.id }
        require(ids.size == ids.toSet().size) { "Duplicate class kit ids found: $ids" }
        cached = parsed
        return parsed
    }

    fun get(): List<ClassKit> = cached
}

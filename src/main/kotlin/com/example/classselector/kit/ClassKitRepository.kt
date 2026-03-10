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

    fun parse(json: String): List<ClassKit> = gson.fromJson(json, type)

    fun load(resourceManager: ResourceManager): List<ClassKit> {
        val resource = resourceManager.getResource(kitsPath).orElseThrow {
            IllegalStateException("Missing class kit json at $kitsPath")
        }
        val parsed = resource.openAsReader().use { parse(it.readText()) }
        cached = parsed
        return parsed
    }

    fun get(): List<ClassKit> = cached
}

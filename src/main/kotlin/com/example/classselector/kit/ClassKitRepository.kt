package com.example.classselector.kit

import com.example.classselector.ClassSelectorMod
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager

object ClassKitRepository {
    private val gson = Gson()
    private val type = object : TypeToken<List<ClassKit>>() {}.type
    private val kitsPath = ResourceLocation.fromNamespaceAndPath(ClassSelectorMod.MOD_ID, "class_kits/kits.json")

    @Volatile
    private var cached: List<ClassKit> = emptyList()

    fun parse(json: String): List<ClassKit> = gson.fromJson<List<ClassKit>>(json, type) ?: emptyList()

    fun load(resourceManager: ResourceManager): List<ClassKit> {
        val resource = resourceManager.getResource(kitsPath).orElseThrow {
            IllegalStateException("Missing class kit json at $kitsPath")
        }
        val parsed = resource.openAsReader().use { parse(it.readText()) }
        val validated = validate(parsed)
        cached = validated
        return validated
    }

    fun get(): List<ClassKit> = cached

    internal fun validate(kits: List<ClassKit>): List<ClassKit> {
        require(kits.isNotEmpty()) { "No class kits defined at $kitsPath" }
        require(kits.all { it.id.isNotBlank() }) { "Every class kit must have a non-blank id" }
        val ids = kits.map { it.id }
        require(ids.size == ids.toSet().size) { "Duplicate class kit ids found: $ids" }

        kits.forEach { kit ->
            require(kit.title.isNotBlank()) { "Class kit '${kit.id}' must have a non-blank title" }
            require(kit.blurb.isNotBlank()) { "Class kit '${kit.id}' must have a non-blank blurb" }
            require(kit.description.isNotBlank()) { "Class kit '${kit.id}' must have a non-blank description" }
            require(kit.items.isNotEmpty()) { "Class kit '${kit.id}' must define at least one item" }

            kit.items.forEachIndexed { index, item ->
                require(ResourceLocation.tryParse(item.item) != null) {
                    "Class kit '${kit.id}' item #${index + 1} has an invalid item id '${item.item}'"
                }
                require(item.count > 0) {
                    "Class kit '${kit.id}' item #${index + 1} must have a positive count"
                }
                require(KitSlot.isSupported(item.slot)) {
                    "Class kit '${kit.id}' item #${index + 1} uses unsupported slot '${item.slot}'"
                }
            }
        }

        return kits
    }
}

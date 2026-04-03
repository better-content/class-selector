package com.example.classselector.client

import com.example.classselector.kit.ClassKit

object ClassSelectionState {
    var activeInCurrentWorld: Boolean = false
    var kits: List<ClassKit> = emptyList()
    var promptOpen: Boolean = false
    var selectionRequired: Boolean = false
    var reminderCooldownTicks: Int = 0
}

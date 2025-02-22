/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.types.regions.Scope

data class MirScope(val scope: Scope) {
    private val drops = mutableListOf<Drop>()
    var cachedUnwindDrop: DropTree.DropNode? = null
        private set

    fun setCachedUnwindDrop(dropNode: DropTree.DropNode) {
        cachedUnwindDrop = dropNode
    }

    fun reversedDrops(): Iterator<Drop> = drops.asReversed().iterator()

    fun drops(): Iterator<Drop> = drops.iterator()

    fun addDrop(drop: Drop) {
        drops.add(drop)
    }

    fun invalidateCaches() {
        cachedUnwindDrop = null
    }
}

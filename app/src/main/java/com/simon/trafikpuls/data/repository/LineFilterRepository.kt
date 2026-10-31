package com.simon.trafikpuls.data.repository

import com.simon.trafikpuls.data.local.LineFilterDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Which lines the user wants to see per stop (areaId -> line keys). Separate for demo and live. */
class LineFilterRepository(
    private val dao: LineFilterDao,
    demoModeFlow: Flow<Boolean>,
    private val isDemoMode: suspend () -> Boolean,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val filters: Flow<Map<String, Set<String>>> = demoModeFlow
        .distinctUntilChanged()
        .flatMapLatest { demo -> dao.observe(demo) }
        .map { rows -> rows.groupBy({ it.areaId }, { it.lineKey }).mapValues { (_, keys) -> keys.toSet() } }
        .distinctUntilChanged()

    fun filterFor(areaId: String): Flow<Set<String>> =
        filters.map { it[areaId].orEmpty() }.distinctUntilChanged()

    /** An empty set removes the filter (all lines are shown again). */
    suspend fun setFilter(areaId: String, lineKeys: Set<String>) = dao.replace(areaId, isDemoMode(), lineKeys)
}

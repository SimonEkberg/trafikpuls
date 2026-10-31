package com.simon.trafikpuls.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation routes.

@Serializable
data object DashboardRoute

@Serializable
data object AlertsRoute

@Serializable
data object SearchRoute

@Serializable
data object SettingsRoute

@Serializable
data class StopBoardRoute(val areaId: String, val name: String)

/** [stopId] marks the stop the user came from, highlighted along the trip. */
@Serializable
data class TripRoute(val tripId: String, val startDate: String, val stopId: String? = null)

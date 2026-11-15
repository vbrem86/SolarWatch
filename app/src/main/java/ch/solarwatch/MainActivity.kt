package ch.solarwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import ch.solarwatch.data.Metric
import ch.solarwatch.ui.AccessScreen
import ch.solarwatch.ui.ChartSettingsScreen
import ch.solarwatch.ui.ComplicationScreen
import ch.solarwatch.ui.IconPickerScreen
import ch.solarwatch.ui.IconsScreen
import ch.solarwatch.ui.LayoutScreen
import ch.solarwatch.ui.LayoutTarget
import ch.solarwatch.ui.MetricPickerScreen
import ch.solarwatch.ui.SettingsScreen
import ch.solarwatch.ui.SolarScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val nav = rememberSwipeDismissableNavController()
                SwipeDismissableNavHost(navController = nav, startDestination = "main") {
                    composable("main") { SolarScreen(onOpenSettings = { nav.navigate("settings") }) }
                    composable("settings") {
                        SettingsScreen(
                            onLayout = { nav.navigate("layout/${it.name}") },
                            onChart = { nav.navigate("chart") },
                            onIcons = { nav.navigate("icons") },
                            onComplication = { nav.navigate("complication") },
                            onAccess = { nav.navigate("access") },
                        )
                    }
                    composable(
                        "layout/{target}",
                        arguments = listOf(navArgument("target") { type = NavType.StringType }),
                    ) { e ->
                        val target = LayoutTarget.valueOf(e.arguments?.getString("target") ?: "APP")
                        LayoutScreen(target) { r, i -> nav.navigate("pick/${target.name}/$r/$i") }
                    }
                    composable(
                        "pick/{target}/{row}/{index}",
                        arguments = listOf(
                            navArgument("target") { type = NavType.StringType },
                            navArgument("row") { type = NavType.IntType },
                            navArgument("index") { type = NavType.IntType },
                        ),
                    ) { e ->
                        val a = e.arguments
                        MetricPickerScreen(
                            target = LayoutTarget.valueOf(a?.getString("target") ?: "APP"),
                            row = a?.getInt("row") ?: 0,
                            index = a?.getInt("index") ?: 0,
                            onDone = { nav.popBackStack() },
                        )
                    }
                    composable("chart") { ChartSettingsScreen() }
                    composable("icons") { IconsScreen { m -> nav.navigate("icon/${m.name}") } }
                    composable(
                        "icon/{metric}",
                        arguments = listOf(navArgument("metric") { type = NavType.StringType }),
                    ) { e ->
                        IconPickerScreen(
                            metric = Metric.valueOf(e.arguments?.getString("metric") ?: Metric.PV.name),
                            onDone = { nav.popBackStack() },
                        )
                    }
                    composable("complication") { ComplicationScreen(onDone = { nav.popBackStack() }) }
                    composable("access") { AccessScreen() }
                }
            }
        }
    }
}

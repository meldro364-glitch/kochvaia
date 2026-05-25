package com.kochvaia.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.kochvaia.app.data.Role
import com.kochvaia.app.data.Session
import com.kochvaia.app.data.SessionStore
import com.kochvaia.app.ui.kid.KidHomeScreen
import com.kochvaia.app.ui.kid.KidPairingScreen
import com.kochvaia.app.ui.kid.KidSiblingScreen
import com.kochvaia.app.ui.onboarding.ModePickerScreen
import com.kochvaia.app.ui.parent.ParentDashboardScreen
import com.kochvaia.app.ui.parent.ParentKidDetailScreen
import com.kochvaia.app.ui.parent.ParentShareQrScreen
import com.kochvaia.app.ui.parent.ParentSignInScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Root composable. Picks the start destination from the persisted session, then
 * hosts the nav graph for all in-app flows.
 */
@Composable
fun KochvaiaApp() {
    val rootVm: RootViewModel = hiltViewModel()
    var session by remember { mutableStateOf(rootVm.currentSession()) }

    val nav: NavHostController = rememberNavController()
    val start = when (session?.role) {
        Role.parent -> Routes.PARENT_DASHBOARD
        Role.kid -> Routes.KID_HOME
        null -> Routes.MODE_PICKER
    }

    NavHost(navController = nav, startDestination = start) {
        modePicker(nav)
        parentGraph(nav, onSessionChanged = { session = rootVm.currentSession() })
        kidGraph(nav, onSessionChanged = { session = rootVm.currentSession() })
    }
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val sessionStore: SessionStore,
) : ViewModel() {
    fun currentSession(): Session? = sessionStore.load()
}

private fun NavGraphBuilder.modePicker(nav: NavHostController) {
    composable(Routes.MODE_PICKER) {
        ModePickerScreen(
            onPickParent = { nav.navigate(Routes.PARENT_SIGN_IN) },
            onPickKid = { nav.navigate(Routes.KID_PAIRING) },
        )
    }
    // Deep link from QR scan / browser: kochvaia://join?code=XXXX-YYYY.
    composable(
        route = Routes.JOIN_FROM_DEEP_LINK,
        arguments = listOf(navArgument("code") { type = NavType.StringType; defaultValue = "" }),
        deepLinks = listOf(navDeepLink { uriPattern = "kochvaia://join?code={code}" }),
    ) { entry ->
        val code = entry.arguments?.getString("code").orEmpty()
        // For now route deep links through the kid-pairing screen; it will
        // detect parent-invite codes and bounce to ParentSignInScreen if needed.
        LaunchedEffect(code) {
            if (code.isNotBlank()) {
                nav.navigate(Routes.KID_PAIRING) {
                    popUpTo(Routes.MODE_PICKER) { inclusive = false }
                }
            }
        }
    }
}

private fun NavGraphBuilder.parentGraph(
    nav: NavHostController,
    onSessionChanged: () -> Unit,
) {
    composable(Routes.PARENT_SIGN_IN) {
        ParentSignInScreen(
            onSignedIn = {
                onSessionChanged()
                nav.navigate(Routes.PARENT_DASHBOARD) {
                    popUpTo(Routes.MODE_PICKER) { inclusive = true }
                }
            },
        )
    }
    composable(Routes.PARENT_DASHBOARD) {
        ParentDashboardScreen(
            onOpenKid = { id -> nav.navigate(Routes.parentKidDetail(id)) },
            onShareQr = { id -> nav.navigate(Routes.parentShareQr(id)) },
            onShareCoParent = { nav.navigate(Routes.parentShareQr(null)) },
            onSignOut = {
                onSessionChanged()
                nav.navigate(Routes.MODE_PICKER) {
                    popUpTo(0) { inclusive = true }
                }
            },
        )
    }
    composable(
        route = Routes.PARENT_KID_DETAIL,
        arguments = listOf(navArgument("kidId") { type = NavType.StringType }),
    ) { entry ->
        val kidId = entry.arguments?.getString("kidId").orEmpty()
        ParentKidDetailScreen(kidId = kidId, onBack = { nav.popBackStack() })
    }
    composable(
        route = Routes.PARENT_SHARE_QR,
        arguments = listOf(navArgument("kidId") { type = NavType.StringType; defaultValue = "" }),
    ) { entry ->
        val kidIdArg = entry.arguments?.getString("kidId").orEmpty()
        ParentShareQrScreen(
            kidId = kidIdArg.ifBlank { null },
            onBack = { nav.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.kidGraph(
    nav: NavHostController,
    onSessionChanged: () -> Unit,
) {
    composable(Routes.KID_PAIRING) {
        KidPairingScreen(
            onPaired = {
                onSessionChanged()
                nav.navigate(Routes.KID_HOME) {
                    popUpTo(Routes.MODE_PICKER) { inclusive = true }
                }
            },
            onBack = { nav.popBackStack() },
        )
    }
    composable(Routes.KID_HOME) {
        KidHomeScreen(
            onOpenSibling = { id -> nav.navigate(Routes.kidSibling(id)) },
        )
    }
    composable(
        route = Routes.KID_SIBLING,
        arguments = listOf(navArgument("kidId") { type = NavType.StringType }),
    ) { entry ->
        val kidId = entry.arguments?.getString("kidId").orEmpty()
        KidSiblingScreen(
            kidId = kidId,
            onBack = { nav.popBackStack() },
            // Tapping our own paired-as chip → return to KID_HOME without
            // stacking another screen.
            onOpenOwnHome = {
                nav.popBackStack(Routes.KID_HOME, inclusive = false)
            },
            // Jumping between siblings replaces the current sibling screen so
            // the back stack stays shallow (HOME → SIBLING, not HOME → A → B → C).
            onOpenSibling = { otherId ->
                nav.navigate(Routes.kidSibling(otherId)) {
                    popUpTo(Routes.KID_SIBLING) { inclusive = true }
                }
            },
        )
    }
}

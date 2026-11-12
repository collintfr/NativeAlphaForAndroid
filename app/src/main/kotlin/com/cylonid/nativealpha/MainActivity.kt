package com.cylonid.nativealpha

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cylonid.nativealpha.browser.AdblockDashboardActivity
import com.cylonid.nativealpha.helper.BiometricPromptHelper
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.ui.AboutScreen
import com.cylonid.nativealpha.ui.AddWebAppSheet
import com.cylonid.nativealpha.ui.AppViewModel
import com.cylonid.nativealpha.ui.GlobalSettingsScreen
import com.cylonid.nativealpha.ui.HomeScreen
import com.cylonid.nativealpha.ui.NativeAlphaTheme
import com.cylonid.nativealpha.ui.UsageScreen
import com.cylonid.nativealpha.ui.WebAppSettingsScreen
import com.cylonid.nativealpha.util.EntryPointUtils

class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        EntryPointUtils.entryPointReached(this)

        setContent {
            NativeAlphaTheme {
                val nav = rememberNavController()
                var showAdd by remember { mutableStateOf(false) }
                fun openAdblock() = startActivity(Intent(this, AdblockDashboardActivity::class.java))

                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            vm = vm,
                            onAdd = { showAdd = true },
                            onOpenSettings = { app ->
                                val go = { nav.navigate("webapp/${app.ID}") }
                                // Protected web apps (and their time limits) need authentication to edit.
                                if (app.isBiometricProtection) {
                                    BiometricPromptHelper(this@MainActivity).showPrompt({ go() }, {}, getString(R.string.bioprompt_restricted_webapp))
                                } else go()
                            },
                            onGlobalSettings = { nav.navigate("settings") },
                            onUsage = { nav.navigate("usage") },
                            onAdblock = { openAdblock() },
                            onAbout = { nav.navigate("about") },
                        )
                        if (showAdd) AddWebAppSheet(vm) { showAdd = false }
                    }
                    composable("webapp/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                        WebAppSettingsScreen(vm, entry.arguments!!.getInt("id")) { nav.popBackStack() }
                    }
                    composable("settings") {
                        GlobalSettingsScreen(
                            vm = vm,
                            onBack = { nav.popBackStack() },
                            onDefaultWebAppSettings = { nav.navigate("webapp/${AppViewModel.GLOBAL_ID}") },
                            onAdblockDashboard = { openAdblock() },
                        )
                    }
                    composable("usage") { UsageScreen(vm) { nav.popBackStack() } }
                    composable("about") { AboutScreen { nav.popBackStack() } }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DataManager.getInstance().loadAppData()
        vm.refresh()
    }
}

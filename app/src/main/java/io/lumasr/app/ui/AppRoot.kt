package io.lumasr.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.lumasr.app.AppViewModel
import io.lumasr.app.batch.BatchRepository
import io.lumasr.app.model.Dest
import io.lumasr.app.ui.batch.TaskScreen
import io.lumasr.app.ui.benchmark.BenchmarkScreen
import io.lumasr.app.ui.compare.CompareScreen
import io.lumasr.app.ui.config.BatchConfigScreen
import io.lumasr.app.ui.config.SingleConfigScreen
import io.lumasr.app.ui.components.AppTab
import io.lumasr.app.ui.components.LumaBottomBar
import io.lumasr.app.ui.folder.FolderOverviewScreen
import io.lumasr.app.ui.folder.GridSelectScreen
import io.lumasr.app.ui.home.HomeScreen
import io.lumasr.app.ui.models.ModelGuideScreen
import io.lumasr.app.ui.perf.PerfLogScreen
import io.lumasr.app.ui.processing.ProcessingScreen
import io.lumasr.app.ui.settings.ModelsScreen
import io.lumasr.app.ui.settings.SettingsScreen

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current
    val dest = vm.dest
    val progress by vm.progress.collectAsState()
    val stage by vm.stage.collectAsState()
    val batchState by BatchRepository.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    BackHandler(enabled = true) {
        when (dest) {
            Dest.Home -> (context as? Activity)?.finish()
            Dest.Tasks, Dest.Settings -> vm.openHome()
            Dest.Models -> vm.backFromModels()
            Dest.Benchmark -> vm.backFromBenchmark()
            Dest.ModelGuide -> vm.backFromModelGuide()
            Dest.PerfLog -> vm.backFromPerfLog()
            is Dest.FolderOverview -> vm.backFromFolder()
            is Dest.Grid -> vm.backToOverview()
            is Dest.BatchConfig -> vm.backToGrid()
            is Dest.Configure -> vm.backHome()
            Dest.Processing -> vm.cancelProcessing()
            Dest.Compare -> vm.backFromCompare()
        }
    }

    LaunchedEffect(vm.errorMessage) {
        vm.errorMessage?.let {
            snackbar.showSnackbar(it)
            vm.consumeError()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (dest) {
            Dest.Home -> TabLayout(AppTab.HOME, vm) {
                HomeScreen(vm = vm, batchState = batchState)
            }

            Dest.Tasks -> TabLayout(AppTab.TASKS, vm) {
                TaskScreen(vm = vm, state = batchState)
            }

            Dest.Settings -> TabLayout(AppTab.SETTINGS, vm) {
                SettingsScreen(vm = vm)
            }

            Dest.Models -> ModelsScreen(vm = vm)
            Dest.Benchmark -> BenchmarkScreen(vm = vm)
            Dest.ModelGuide -> ModelGuideScreen(vm = vm)
            Dest.PerfLog -> PerfLogScreen(vm = vm)
            is Dest.FolderOverview -> FolderOverviewScreen(vm = vm)
            is Dest.Grid -> GridSelectScreen(vm = vm)
            is Dest.BatchConfig -> BatchConfigScreen(vm = vm)
            is Dest.Configure -> SingleConfigScreen(vm = vm, uris = dest.uris, batch = false)
            Dest.Processing -> ProcessingScreen(
                vm = vm,
                progress = progress,
                stage = stage,
                count = vm.pendingUris.size,
            )
            Dest.Compare -> CompareScreen(vm = vm)
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun TabLayout(tab: AppTab, vm: AppViewModel, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            content()
        }
        LumaBottomBar(tab) { selected ->
            when (selected) {
                AppTab.HOME -> vm.openHome()
                AppTab.TASKS -> vm.openTasks()
                AppTab.SETTINGS -> vm.openSettings()
            }
        }
    }
}

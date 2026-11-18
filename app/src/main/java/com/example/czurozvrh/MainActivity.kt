package com.example.czurozvrh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.czurozvrh.ui.LoginScreen
import com.example.czurozvrh.ui.RozvrhViewModel
import com.example.czurozvrh.ui.TimetableScreen
import com.example.czurozvrh.ui.theme.CZURozvrhTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CZURozvrhTheme {
                val vm: RozvrhViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                if (state.loggedIn) TimetableScreen(state, vm) else LoginScreen(state, vm)
            }
        }
    }
}

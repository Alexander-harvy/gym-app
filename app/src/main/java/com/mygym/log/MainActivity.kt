package com.mygym.log

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mygym.log.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { val vm: GymViewModel = viewModel(factory=GymViewModel.factory(application as GymApplication)); GymApp(vm) }
    }
}

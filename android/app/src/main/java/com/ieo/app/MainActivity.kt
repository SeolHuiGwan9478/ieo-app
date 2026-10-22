package com.ieo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ieo.app.ui.theme.IeoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IeoTheme {
                HomeScreen()
            }
        }
    }
}

@Composable
fun HomeScreen() {
    // Surface가 배경색과 그에 맞는 글자색(onBackground)을 함께 정해 줌
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = "Ieo", style = MaterialTheme.typography.displayLarge)
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun HomeScreenPreview() {
    IeoTheme(darkTheme = true) {
        HomeScreen()
    }
}

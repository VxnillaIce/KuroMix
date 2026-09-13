package com.kuromify.kuromix.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromify.kuromix.R
import com.kuromify.kuromix.root.RootShell
import com.kuromify.kuromix.ui.component.OS3GradientBanner
import top.yukonga.miuix.kmp.basic.*

@Composable
fun AboutScreen() {
    var hyperOSVersion by remember { mutableStateOf("Checking…") }
    var deviceName by remember { mutableStateOf("Checking…") }

    LaunchedEffect(Unit) {
        hyperOSVersion = RootShell.getHyperOSVersion()
        deviceName = RootShell.getMarketName()
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(title = "About")
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                OS3GradientBanner(height = 180.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.drawable.kuromify_logo),
                            contentDescription = "KuroMix Logo",
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "KuroMix",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                SmallTitle(text = "DEVICE INFO")
                Card {
                    BasicComponent(
                        title = "Device Model",
                        summary = deviceName
                    )
                    BasicComponent(
                        title = "System Version",
                        summary = "HyperOS $hyperOSVersion"
                    )
                    BasicComponent(
                        title = "Android Version",
                        summary = Build.VERSION.RELEASE
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SmallTitle(text = "ABOUT APP")
                Card {
                    BasicComponent(
                        title = "Developer",
                        summary = "Vxnilla Ice"
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SmallTitle(text = "REFERENCES")
                Card {
                    BasicComponent(
                        title = "UI Toolkit",
                        summary = "MIUIX Library by YukongA"
                    )
                    BasicComponent(
                        title = "Visual Effects",
                        summary = "HyperCeiler Module"
                    )
                    BasicComponent(
                        title = "Inspiration",
                        summary = "ZHITool and REAREye Github"
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

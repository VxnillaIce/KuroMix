package com.kuromify.kuromix.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromify.kuromix.R
import com.kuromify.kuromix.root.RootShell
import com.kuromify.kuromix.ui.component.OS3GradientBanner
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun AboutScreen(bottomPadding: Dp = 0.dp) {
    var hyperOSVersion by remember { mutableStateOf("Checking…") }
    var deviceName by remember { mutableStateOf("Checking…") }

    LaunchedEffect(Unit) {
        hyperOSVersion = RootShell.getHyperOSVersion()
        deviceName = RootShell.getMarketName()
    }

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "About",
                largeTitle = "About",
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = bottomPadding + 16.dp
            )
        ) {
            item {
                OS3GradientBanner(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    height = 180.dp
                ) {
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
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
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
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    BasicComponent(
                        title = "Developer",
                        summary = "Vxnilla Ice"
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SmallTitle(text = "REFERENCES")
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
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

/*
 * USB 设备选择对话框
 * 
 * 功能：
 * - 扫描并显示可用的 USB 设备
 * - 请求 USB 权限
 * - 连接选中的 USB 设备
 */

package com.mobile.scrcpy.android.ui.components

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mobile.scrcpy.android.common.AppDimens
import com.mobile.scrcpy.android.common.AppTextSizes
import com.mobile.scrcpy.android.common.BilingualTexts
import com.mobile.scrcpy.android.core.adb.usb.UsbDeviceInfo
import kotlinx.coroutines.launch

/**
 * USB 设备选择对话框
 * 
 * @param onDismiss 关闭对话框回调
 * @param onScanDevices 扫描设备回调
 * @param onConnectDevice 连接设备回调
 * @param usbDevices USB 设备列表
 * @param isScanning 是否正在扫描
 */
@Composable
fun UsbDeviceDialog(
    onDismiss: () -> Unit,
    onScanDevices: suspend () -> Unit,
    onConnectDevice: suspend (UsbDevice) -> Result<String>,
    usbDevices: List<UsbDeviceInfo>,
    isScanning: Boolean
) {
    val scope = rememberCoroutineScope()
    var isConnecting by remember { mutableStateOf(false) }
    var connectingDeviceId by remember { mutableStateOf<String?>(null) }
    
    // 自动扫描一次
    LaunchedEffect(Unit) {
        onScanDevices()
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(AppDimens.WINDOW_WIDTH_RATIO)
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(AppDimens.windowCornerRadius),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.paddingStandard)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppDimens.paddingStandard),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = BilingualTexts.USB_DEVICE_LIST_TITLE.get(),
                        fontSize = AppTextSizes.title,
                        fontWeight = FontWeight.Bold
                    )
                    
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isConnecting
                    ) {
                        Text(BilingualTexts.BUTTON_CLOSE.get())
                    }
                }
                
                HorizontalDivider()

                Spacer(modifier = Modifier.height(AppDimens.spacingStandard))
                
                // 扫描按钮
                Button(
                    onClick = {
                        scope.launch {
                            onScanDevices()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isScanning && !isConnecting
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (isScanning) BilingualTexts.USB_SCANNING_DEVICES.get()
                        else BilingualTexts.USB_SCAN_BUTTON.get()
                    )
                }
                
                Spacer(modifier = Modifier.height(AppDimens.spacingStandard))
                
                // 设备列表
                if (usbDevices.isEmpty() && !isScanning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = BilingualTexts.USB_NO_DEVICES_FOUND.get(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = AppTextSizes.body
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(usbDevices) { deviceInfo ->
                            UsbDeviceItem(
                                deviceInfo = deviceInfo,
                                isConnecting = isConnecting && connectingDeviceId == deviceInfo.deviceName,
                                onConnect = {
                                    scope.launch {
                                        isConnecting = true
                                        connectingDeviceId = deviceInfo.deviceName
                                        val result = onConnectDevice(deviceInfo.device)
                                        isConnecting = false
                                        connectingDeviceId = null
                                        
                                        if (result.isSuccess) {
                                            onDismiss()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * USB 设备列表项
 */
@Composable
private fun UsbDeviceItem(
    deviceInfo: UsbDeviceInfo,
    isConnecting: Boolean,
    onConnect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimens.cardCornerRadius))
            .clickable(enabled = !isConnecting && deviceInfo.hasPermission) { onConnect() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // USB 图标
            Icon(
                imageVector = Icons.Default.Usb,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (deviceInfo.hasPermission) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 设备信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = deviceInfo.getDisplayName(),
                    fontSize = AppTextSizes.body,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "${BilingualTexts.USB_SERIAL_NUMBER.get()}: ${deviceInfo.serialNumber}",
                    fontSize = AppTextSizes.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${BilingualTexts.USB_PERMISSION.get()}: ",
                        fontSize = AppTextSizes.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (deviceInfo.hasPermission) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Text(
                            text = BilingualTexts.USB_PERMISSION_GRANTED_STATUS.get(),
                            fontSize = AppTextSizes.caption,
                            color = Color(0xFF4CAF50)
                        )
                    } else {
                        Text(
                            text = BilingualTexts.USB_PERMISSION_NOT_GRANTED_STATUS.get(),
                            fontSize = AppTextSizes.caption,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // 连接按钮
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else if (deviceInfo.hasPermission) {
                TextButton(onClick = onConnect) {
                    Text(BilingualTexts.USB_CONNECT_BUTTON.get())
                }
            }
        }
    }
}

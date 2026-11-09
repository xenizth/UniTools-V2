package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manager.UsbStatus
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen

@Composable
fun UsbStatusPill(
    usbStatus: UsbStatus,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("usb_status_pill"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (usbStatus.isConnected) {
                if (usbStatus.isUnisocBrom) {
                    StatusGreen.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                }
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRefresh() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Connection indicator dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (usbStatus.isConnected) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = if (usbStatus.isConnected) Icons.Default.Usb else Icons.Default.Cable,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (usbStatus.isConnected) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = if (usbStatus.isConnected) {
                        "USB: ${usbStatus.deviceName}"
                    } else {
                        "USB Auto-Detect: No device attached (OTG)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (usbStatus.isConnected) FontWeight.Bold else FontWeight.Normal,
                    color = if (usbStatus.isConnected) {
                        if (usbStatus.isUnisocBrom) StatusGreen else MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh USB",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onRefresh() },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Android holds USB access per-device via a system permission dialog, separate from
        // whether a device is merely plugged in. Surface that explicitly instead of only ever
        // showing "connected" and leaving the user to guess why an operation still won't run.
        if (usbStatus.isConnected && !usbStatus.hasPermission) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = StatusAmber
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "USB access not granted",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusAmber
                    )
                }

                if (usbStatus.permissionRequestInFlight) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = StatusAmber
                    )
                } else {
                    TextButton(onClick = onRequestPermission) {
                        Text("Grant Access", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

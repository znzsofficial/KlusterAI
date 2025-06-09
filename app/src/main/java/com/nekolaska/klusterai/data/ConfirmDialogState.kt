package com.nekolaska.klusterai.data

data class ConfirmDialogState(val title: String, val text: String, val onConfirm: () -> Unit)
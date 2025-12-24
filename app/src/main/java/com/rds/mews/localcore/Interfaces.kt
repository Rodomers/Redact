package com.rds.mews.localcore

interface ButtonInputs {
    val action: () -> Unit
    val toast: String?
}
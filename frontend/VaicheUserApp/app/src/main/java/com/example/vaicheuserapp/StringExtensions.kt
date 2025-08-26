package com.example.vaicheuserapp

import java.text.Normalizer

// Extension function to remove Vietnamese diacritics and normalize special characters
fun String.normalizeVietnamese(): String {
    var normalizedString = this
    // Replace 'đ' and 'Đ' with 'd'
    normalizedString = normalizedString.replace('đ', 'd').replace('Đ', 'D')

    // Normalize accents (combining diacritical marks)
    normalizedString = Normalizer.normalize(normalizedString, Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")

    // Further replacements for specific characters if NFD doesn't cover them perfectly for your use case
    // (Often, NFD handles most, but specific fonts or systems can sometimes be tricky)
    // For example, if 'ê' somehow remains 'ê' and you want 'e', though NFD should catch it.
    // It's safer to rely on NFD primarily.

    return normalizedString
}
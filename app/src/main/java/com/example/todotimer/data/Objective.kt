package com.example.todotimer.data

data class Objective(
    val id: Long,
    val title: String,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
)

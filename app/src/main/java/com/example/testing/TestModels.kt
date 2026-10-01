package com.example.testing

enum class TestStatus {
    IDLE,
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED
}

data class TestCaseResult(
    val id: String,
    val name: String,
    val description: String,
    val status: TestStatus = TestStatus.IDLE,
    val durationMs: Long = 0L,
    val details: String = "",
    val error: String? = null
)

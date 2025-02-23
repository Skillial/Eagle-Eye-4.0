package com.wangGang.eagleEye.io

enum class ResultType {
    BEFORE, AFTER;

    override fun toString(): String {
        return when (this) {
            BEFORE -> "result_before"
            AFTER -> "result_after"
        }
    }
}
package com.rds.mews

data class Message(var id: Long, var time: Long, var link: String, var source: String, var mess: String)
data class RSS(var id: Long, var source: String, var link: String)
data class Title(var id: Long, var time: Long, var title: String, var text: String, var sources: String, var links: String)
package com.petmanager.domain.model

data class ChatInfo(
    var userID: String = "",
    var userName: String = "",
    var hostID: String = "",
    var hostName: String = "",
    var postID: String = "",
    var title: String = ""
) {
    // 매개변수가 없는 기본 생성자
    constructor() : this("", "", "", "", "", "")
}


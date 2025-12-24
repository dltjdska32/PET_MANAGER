package com.petmanager.domain.model

data class Message(
    var message: String?,
    var sendId: String?
){
    constructor():this("","")
}


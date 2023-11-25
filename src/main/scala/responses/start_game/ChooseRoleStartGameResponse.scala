package com.natalia
package responses.start_game

case class ChooseRoleStartGameResponse(message: String, chooseRoleUrl: String)

import spray.json.DefaultJsonProtocol.*
import spray.json.{JsonFormat, RootJsonFormat}

object ChooseRoleStartGameResponse {
  // JsonFormat for StartGameResponse
  implicit val responseFormat: RootJsonFormat[ChooseRoleStartGameResponse] = jsonFormat2(ChooseRoleStartGameResponse.apply)
}
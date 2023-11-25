package com.natalia
package responses.start_game

case class RoleAssignedStartGameResponse(message: String, moveToNewPositionUrl: String, distanceToNearestWinningPositionUrl: String)

import spray.json.DefaultJsonProtocol.*
import spray.json.{JsonFormat, RootJsonFormat}

object RoleAssignedStartGameResponse {
  // JsonFormat for StartGameResponse
  implicit val responseFormat: RootJsonFormat[RoleAssignedStartGameResponse] = jsonFormat3(RoleAssignedStartGameResponse.apply)
}
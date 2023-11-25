package com.natalia
package responses.choose_role

case class ChooseRoleResponse(message: String, moveToNewPositionUrl: String, distanceToNearestWinningPositionUrl: String)

import spray.json.DefaultJsonProtocol.*
import spray.json.{JsonFormat, RootJsonFormat}

object ChooseRoleResponse {
  implicit val responseFormat: RootJsonFormat[ChooseRoleResponse] = jsonFormat3(ChooseRoleResponse.apply)
}
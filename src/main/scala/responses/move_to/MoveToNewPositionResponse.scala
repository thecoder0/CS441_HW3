package com.natalia
package responses.move_to

case class MoveToNewPositionResponse(currentPosition: Int, message: String, moveToNewPositionUrl: String, distanceToNearestWinningPositionUrl: String)

import spray.json.DefaultJsonProtocol.*
import spray.json.{JsonFormat, RootJsonFormat}

object MoveToNewPositionResponse {
  implicit val responseFormat: RootJsonFormat[MoveToNewPositionResponse] = jsonFormat4(MoveToNewPositionResponse.apply)
}

package com.natalia
package responses.distance_to_nearest_winning_position

case class DistanceToWinningPositionResponse(message: String, moveToNewPositionUrl: String, distanceToNearestWinningPositionUrl: String)

import spray.json.DefaultJsonProtocol.*
import spray.json.{JsonFormat, RootJsonFormat}

object DistanceToWinningPositionResponse {
  implicit val responseFormat: RootJsonFormat[DistanceToWinningPositionResponse] = jsonFormat3(DistanceToWinningPositionResponse.apply)
}

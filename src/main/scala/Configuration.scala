package com.natalia

import com.lsc.Main.logger
import com.typesafe.config.{Config, ConfigFactory}

object Configuration {
  val config: Config = ConfigFactory.load()
  logger.info("Configuration set up successfully")

  // GRAPH PATHS

  def getOriginalGraphPath: String = config.getString("ORIGINAL_GRAPH_PATH")
  def getPerturbedGraphPath: String = config.getString("PERTURBED_GRAPH_PATH")

  // ROLES

  def getRolePoliceman: String = config.getString("ROLE_POLICEMAN")
  def getRoleThief: String = config.getString("ROLE_THIEF")

  // PATH SEGMENTS

  def getPtGamePathSegment: String = config.getString("PT_GAME_PATH_SEGMENT")
  def getStartGamePathSegment: String = config.getString("START_GAME_PATH_SEGMENT")
  def getPlayerPathSegment: String = config.getString("PLAYER_PATH_SEGMENT")
  def getChooseRolePathSegment: String = config.getString("CHOOSE_ROLE_PATH_SEGMENT")
  def getMoveToPathSegment: String = config.getString("MOVE_TO_PATH_SEGMENT")
  def getDistanceToNearestWinningPositionPathSegment: String =
    config.getString("DISTANCE_TO_NEAREST_WINNING_POSITION_PATH_SEGMENT")

  // TEXT

  def getMakeMovePrompt: String = config.getString("MAKE_MOVE_PROMPT")

  // SERVER

  def getServerDomain: String = config.getString("SERVER_DOMAIN")
  def getServerPort: Int = config.getInt("SERVER_PORT")

  //  def getSimRankMaxDepth: Int = config.getInt("simRankMaxDepth")
  //  def getSimRankDecayControl: Double = config.getDouble("simRankDecayControl")
}

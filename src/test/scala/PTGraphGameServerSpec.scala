package com.natalia

import NetGraphAlgebraDefs.NodeObject
import com.natalia.PTGraphGameServer.{PT_GAME_PATH_SEGMENT, ROLE_POLICEMAN, ROLE_THIEF, getDistanceToNearestWinningPositionUrl}
import org.apache.spark.graphx.{Edge, Graph}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.flatspec.AnyFlatSpec

class PTGraphGameServerSpec extends AnyFlatSpec {

  // 1.) getDistanceToNearestWinningPositionUrl

  // Check that the correct url is returned
  "getDistanceToNearestWinningPositionUrl" should "return correct url based on playerNum" in {
    val expected1 = s"http://localhost:9090/$PT_GAME_PATH_SEGMENT/player/1/distance-to-nearest-winning-position"
    val actual1 = getDistanceToNearestWinningPositionUrl(1)

    assert(actual1 == expected1)

    val expected2 = s"http://localhost:9090/$PT_GAME_PATH_SEGMENT/player/2/distance-to-nearest-winning-position"
    val actual2 = getDistanceToNearestWinningPositionUrl(2)

    assert(actual2 == expected2)
  }

  // 2.) endGameAndCleanUpGameState

  // Check that the game state is reset to initial values
  "endGameAndCleanUpGameState" should "reset the game state to initial values" in {
    PTGraphGameServer.numPlayers = 2
    PTGraphGameServer.player1Role = Some("Thief")
    PTGraphGameServer.player2Role = Some("Policeman")
    PTGraphGameServer.player1Position = 10
    PTGraphGameServer.player2Position = 20
    PTGraphGameServer.isGameActive = true

    PTGraphGameServer.endGameAndCleanUpGameState

    assert(PTGraphGameServer.numPlayers == 0)
    assert(PTGraphGameServer.player1Role == None)
    assert(PTGraphGameServer.player2Role == None)
    assert(PTGraphGameServer.player1Position == 0)
    assert(PTGraphGameServer.player2Position == 0)
    assert(!PTGraphGameServer.isGameActive)

  }

  // 3.) isPlayerInWinningPosition

  // Check for winning position for Policeman
  "isPlayerInWinningPosition" should "return true if player's role is Policeman and player1Position == player2Position" in {
    PTGraphGameServer.player1Position = 10
    PTGraphGameServer.player2Position = 10
    PTGraphGameServer.player1Role = Some(ROLE_POLICEMAN)
    PTGraphGameServer.player2Role = Some(ROLE_THIEF)

    val result = PTGraphGameServer.isPlayerInWinningPosition(1)

    assert(result)
  }

  // Check for a case were Policeman is not in winning position
  "isPlayerInWinningPosition" should "return false if player's role is Policeman and player1Position != player2Position" in {
    PTGraphGameServer.player1Position = 15
    PTGraphGameServer.player2Position = 10
    PTGraphGameServer.player1Role = Some(ROLE_POLICEMAN)
    PTGraphGameServer.player2Role = Some(ROLE_THIEF)

    val result = PTGraphGameServer.isPlayerInWinningPosition(1)

    assert(!result)
  }

  // 4.) handleInvalidMove

  // Check for an invalid move
  "handleInvalidMove" should "end the game, set the correct winner, and return a message" in {
    // Set up the test scenario
    PTGraphGameServer.numPlayers = 2
    PTGraphGameServer.player1Role = Some("Thief")
    PTGraphGameServer.player2Role = Some("Policeman")
    PTGraphGameServer.player1Position = 10
    PTGraphGameServer.player2Position = 20
    PTGraphGameServer.isGameActive = true

    val _ = PTGraphGameServer.handleInvalidMove(1)

    // Assert that the game state is reset, and the correct winner is set
    assert(PTGraphGameServer.numPlayers == 0)
    assert(PTGraphGameServer.player1Role.isEmpty)
    assert(PTGraphGameServer.player2Role.isEmpty)
    assert(PTGraphGameServer.player1Position == 0)
    assert(PTGraphGameServer.player2Position == 0)
    assert(!PTGraphGameServer.isGameActive)
    assert(!PTGraphGameServer.didPlayer1Win)
    assert(PTGraphGameServer.didPlayer2Win)
  }

  // 5.) getAdjacentNodeIds

  "getAdjacentNodeIds" should "return list of adjacent node id's reachable from a node one hop away" in {
    val conf = new SparkConf().setAppName("SimRankSpec").setMaster("local[2]")
    val sc = new SparkContext(conf)

    val vertices: RDD[(Long, NodeObject)] = sc.parallelize(Seq(
      (1, generateNodeObject(1)),
      (2, generateNodeObject(2)),
      (3, generateNodeObject(3))
    ))

    val edges: RDD[Edge[Double]] = sc.parallelize(Seq(
      Edge(1L, 2L, 1.0),
      Edge(1L, 3L, 1.0)
    ))

    val graph = Graph(vertices, edges)

    val result = PTGraphGameServer.getAdjacentNodeIds(1, graph)

    assert(result.length == 2)
    assert(result.contains(2L))
    assert(result.contains(3L))

    sc.stop()
  }

  "getAdjacentNodeIds" should "return empty list if node represented by currentPosition parameter has no adjacent " +
    "reachable nodes" in {
    val conf = new SparkConf().setAppName("SimRankSpec").setMaster("local[2]")
    val sc = new SparkContext(conf)

    val vertices: RDD[(Long, NodeObject)] = sc.parallelize(Seq(
      (1, generateNodeObject(1)),
      (2, generateNodeObject(2)),
      (3, generateNodeObject(3))
    ))

    val edges: RDD[Edge[Double]] = sc.parallelize(Seq(
      Edge(1L, 2L, 1.0),
      Edge(1L, 3L, 1.0)
    ))

    val graph = Graph(vertices, edges)

    val result = PTGraphGameServer.getAdjacentNodeIds(3, graph)

    assert(result.isEmpty)

    sc.stop()
  }

  // HELPERS

  private def generateNodeObject(id: Int): NodeObject = {
    NodeObject(id, 0, 0, 1, 0, 0, 0, 0, 0.0, false)
  }
}

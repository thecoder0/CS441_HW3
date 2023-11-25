package com.natalia

import responses.choose_role.ChooseRoleResponse
import responses.move_to.MoveToNewPositionResponse
import responses.start_game.{ChooseRoleStartGameResponse, RoleAssignedStartGameResponse}

import NetGraphAlgebraDefs.{NetGraph, NodeObject}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.{Directive, PathMatcher, Route}
import com.google.common.graph.EndpointPair
import com.lsc.Main.logger
import com.natalia.responses.distance_to_nearest_winning_position.DistanceToWinningPositionResponse
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.graphx.lib.ShortestPaths
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.graphx.{Edge, EdgeDirection, Graph, Pregel, VertexId}
import spray.json.*

import java.io.{BufferedWriter, FileWriter}
import java.nio.file.{Files, Paths}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.RichOptional
import java.util.stream.Collectors
import scala.concurrent.ExecutionContext
import scala.util.Random
import scala.util.control.NonLocalReturns

object PTGraphGameServer {
  implicit val system: ActorSystem = ActorSystem("PTGraphGameServer")
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  // CONSTANTS

  val ROLE_POLICEMAN: String = Configuration.getRolePoliceman
  val ROLE_THIEF: String = Configuration.getRoleThief

  // PATH SEGMENTS

  val PT_GAME_PATH_SEGMENT: String = Configuration.getPtGamePathSegment
  val START_GAME_PATH_SEGMENT: String = Configuration.getStartGamePathSegment
  val PLAYER_PATH_SEGMENT: String = Configuration.getPlayerPathSegment
  val CHOOSE_ROLE_PATH_SEGMENT: String = Configuration.getChooseRolePathSegment
  val MOVE_TO_PATH_SEGMENT: String = Configuration.getMoveToPathSegment
  val DISTANCE_TO_NEAREST_WINNING_POSITION_PATH_SEGMENT: String = Configuration.getDistanceToNearestWinningPositionPathSegment

  // PATHS

  val START_GAME_PATH = path(PT_GAME_PATH_SEGMENT / START_GAME_PATH_SEGMENT)
  val chooseRoleEndpoint =
    path(PT_GAME_PATH_SEGMENT / PLAYER_PATH_SEGMENT / IntNumber / CHOOSE_ROLE_PATH_SEGMENT / Segment)
  val moveToEndpoint = path(PT_GAME_PATH_SEGMENT / PLAYER_PATH_SEGMENT / IntNumber / MOVE_TO_PATH_SEGMENT / IntNumber)
  val distanceToWinningPositionEndpoint =
    path(PT_GAME_PATH_SEGMENT / PLAYER_PATH_SEGMENT / IntNumber / DISTANCE_TO_NEAREST_WINNING_POSITION_PATH_SEGMENT)

  // PROMPT CONSTANTS

  val MAKE_MOVE_PROMPT: String = Configuration.getMakeMovePrompt

  // SERVER CONFIG

  val SERVER_DOMAIN: String = Configuration.getServerDomain
  val SERVER_PORT: Int = Configuration.getServerPort

  // GAME STATE

  var originalNetGraph: Option[NetGraph] = None
  var perturbedNetGraph: Option[NetGraph] = None

  var broadcastedOriginalGraphX: Option[Broadcast[Graph[NodeObject, Double]]] = None
  var broadcastedPerturbedGraphX: Option[Broadcast[Graph[NodeObject, Double]]] = None

  var numPlayers = 0
  var player1Role: Option[String] = None
  var player2Role: Option[String] = None
  var player1Position: Int = 0
  var player2Position: Int = 0
  var didPlayer1Win: Boolean = false
  var didPlayer2Win: Boolean = false
  var isGameActive: Boolean = false
  var originalValuableNodes: List[VertexId] = List[VertexId]()

  // MAIN

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("PolicemanAndThief").setMaster("local[4]")
    val sc = new SparkContext(conf)
    loadGraphs(sc)

    val startGameRoute = getStartGameRoute
    val chooseRoleRoute = getChooseRoleRoute
    val moveToRoute = getMoveToRoute
    val distanceToWinningPositionRoute = getDistanceToWinningPositionRoute

    val route = startGameRoute ~ chooseRoleRoute ~ moveToRoute ~ distanceToWinningPositionRoute

    val server = Http().newServerAt(SERVER_DOMAIN, SERVER_PORT).bind(route)
    server.map { _ =>
      logger.info(s"Successfully started on $SERVER_DOMAIN:$SERVER_PORT ")
    } recover { case ex =>
      logger.info("Failed to start the server due to: " + ex.getMessage)
    }
  }

  // MAIN ROUTES

  def getStartGameRoute: Route = {
    START_GAME_PATH {
      get {
        if (numPlayers == 2) {
          complete("Game already full.")
        }

        numPlayers += 1

        if (!isGameActive) {
          isGameActive = true
          didPlayer1Win = false
          didPlayer2Win = false
        }

        if (numPlayers == 1) {
          // Player 1 has joined the game
          handlePlayer1StartingTheGame
        } else {
          // Player 2 has joined the game
          handlePlayer2StartingTheGame
        }
      }
    }
  }

  def getChooseRoleRoute: Route = {
    chooseRoleEndpoint { (playerNum: Int, chosenRole: String) =>
      get {
        // Check if chosenRole is valid
        if (chosenRole != ROLE_POLICEMAN && chosenRole != ROLE_THIEF) {
          logAndDocumentGameEvent(s"Invalid role `$chosenRole` chosen")
          complete("Only the roles of either Policeman or Thief are allowed.")
        } else if (playerNum < 1 || playerNum > 2) { // Check if playerNum is valid
          logAndDocumentGameEvent(s"Invalid playerNum $playerNum indicated")
          complete("Only two players are allowed in this game.")
        } else {
          // Figure out the other player's role
          val otherPlayerRole = if (playerNum == 1) player2Role else player1Role

          if (otherPlayerRole.isDefined && chosenRole == otherPlayerRole.get) {
            assignOppositeRoleToPlayer(playerNum)
          } else {
            assignChosenRoleToPlayer(playerNum, chosenRole)
          }
        }
      }
    }
  }

  def getMoveToRoute: Route = {
    moveToEndpoint { (playerNum, newPosition) =>
      get {
        val thisPlayerRole = if (playerNum == 1) player1Role else player2Role

        // 1.) Validate that new position exists in both perturbed and original graph
        if (newPosition < 0 || newPosition > perturbedNetGraph.get.totalNodes) {
          val errorMessage = s"getMoveToRoute - Node $newPosition does not exist in the perturbed graph"
          throw new RuntimeException(errorMessage)
        } else if (newPosition > originalNetGraph.get.totalNodes) {
          val errorMessage = s"getMoveToRoute - Node $newPosition does not exist in the original graph"
          throw new RuntimeException(errorMessage)
        }

        val currentPosition = if (playerNum == 1) player1Position else player2Position

        // 2.) Validate that move is valid from `currentPosition` to `newPosition` in the perturbed graph
        val hasEdgeInPerturbedGraph = broadcastedPerturbedGraphX.get.value.edges
          .filter(edge => edge.srcId == currentPosition && edge.dstId == newPosition)
          .count() > 0

        if (!hasEdgeInPerturbedGraph) {
          // Tell the player that s/he has made an INVALID move and that s/he has lost. Also end the game
          handleInvalidMove(playerNum)
        }

        // 3.) Validate that move is valid from `currentPosition` to `newPosition` in the original graph
        val hasEdgeInOriginalGraph = broadcastedOriginalGraphX.get.value.edges
          .filter(edge => edge.srcId == currentPosition && edge.dstId == newPosition)
          .count() > 0

        if (!hasEdgeInOriginalGraph) {
          // Tell the player that s/he has made an INVALID move and that s/he has lost. Also end the game
          handleInvalidMove(playerNum)
        }

        // Move is valid, so assign the new position to the player
        if (playerNum == 1) {
          player1Position = newPosition
        } else {
          player2Position = newPosition
        }

        val adjacentPositionsCommaSeparated = getAdjacentNodeIds(
          newPosition,
          broadcastedPerturbedGraphX.get.value
        ).mkString(", ")
        val adjacentPositionsArrayText = s"[$adjacentPositionsCommaSeparated]"

        val message = s"You (Player $playerNum) ($thisPlayerRole) have moved to position $newPosition. The adjacent " +
          s"positions you can move to are $adjacentPositionsArrayText. $MAKE_MOVE_PROMPT"

        val distanceToNearestWinningPositionUrl = getDistanceToNearestWinningPositionUrl(playerNum)

        val responseJson = MoveToNewPositionResponse(
          newPosition,
          message,
          getMoveToNewPositionUrl(playerNum),
          distanceToNearestWinningPositionUrl
        ).toJson

        val response = ToResponseMarshallable(responseJson)

        complete(response)
      }
    }
  }

  def getDistanceToWinningPositionRoute: Route = {
    distanceToWinningPositionEndpoint { (playerNum) =>
      get {
        handleGettingClosestReachableDistanceToWinningPosition(playerNum)
      }
    }
  }

  // HELPER ROUTES

  def handlePlayer1StartingTheGame: Route = {
    val player1Message =
      s"Welcome Player $numPlayers. This is the Policeman / Thief Graph Game. Please send a GET " +
        "request to the `chooseRoleUrl` to chose your role. You can only choose between `policeman` and `thief`."

    val player1ChooseRoleUrl = "http://localhost:9090/pt-game/player/1/choose-role/<yourChosenRole>"

    val startGameResponseJson = ChooseRoleStartGameResponse(
      player1Message,
      player1ChooseRoleUrl
    ).toJson
    val response = ToResponseMarshallable(startGameResponseJson)

    logAndDocumentGameEvent("Player 1 entered the game.")

    complete(response)
  }

  def handlePlayer2StartingTheGame: Route = {
    logAndDocumentGameEvent("Player 2 entered the game.")

    if (player1Role.isDefined) {
      // Player 2 automatically gets assigned the role not chosen by Player 1
      player2Role = if (player1Role.get.contains(ROLE_POLICEMAN)) Some(ROLE_THIEF) else Some(ROLE_POLICEMAN)

      logAndDocumentGameEvent(s"Player 2 was assigned role of ${player2Role.get} since Player 1 " +
        s"already chose the role of ${player1Role.get}")

      val startingPosition = assignRandomPositionToPlayer(2)

      val didPlayerWin = isPlayerInWinningPosition(2)
      if (didPlayerWin) {
        // Declare player the winner and end game
        return declarePlayerAsWinnerAndEndGame(2)
      }

      val adjacentPositionsCommaSeparated = getAdjacentNodeIds(
        player2Position,
        broadcastedPerturbedGraphX.get.value
      ).mkString(", ")
      val adjacentPositionsArrayText = s"[$adjacentPositionsCommaSeparated]"

      val message = s"Welcome Player 2. This is the Policeman / Thief Graph Game. Player 1 has already chosen " +
        s"to be a ${player1Role.get}. You've been assigned the role of ${player2Role.get}. " +
        s"You are currently at position: $startingPosition. The adjacent positions you can move to are " +
        s"$adjacentPositionsArrayText. $MAKE_MOVE_PROMPT"

      val responseJson = RoleAssignedStartGameResponse(
        message,
        "http://localhost:9090/player-2/move-to/<nodeId>",
        getDistanceToNearestWinningPositionUrl(2)
      ).toJson
      val response = ToResponseMarshallable(responseJson)

      complete(response)
    } else {
      val player2Message =
        s"Welcome Player $numPlayers. This is the Policeman / Thief Graph Game. Please send a GET " +
          s"request to the `chooseRoleUrl` to chose your role. You can only choose between 'policeman' " +
          s"and 'thief'."

      val player2ChooseRoleUrl = "http://localhost:9090/pt-game/player/2/choose-role/<yourChosenRole>"

      val startGameResponseJson = ChooseRoleStartGameResponse(
        player2Message,
        player2ChooseRoleUrl
      ).toJson

      val response = ToResponseMarshallable(startGameResponseJson)

      complete(response)
    }
  }

  // Forces a role onto a player that's the opposite of the role the first player chose
  def assignOppositeRoleToPlayer(playerNum: Int): Route = {
    val otherPlayerNum = if (playerNum == 1) 2 else 1
    val otherPlayerRole = if (otherPlayerNum == 1) player1Role else player2Role

    if (otherPlayerRole.isEmpty) {
      val errorMessage = s"assignOppositeRoleToPlayer - `otherPlayerRole` must not be null when calling this " +
        s"function - failing the job."
      logger.error(errorMessage)
      throw new RuntimeException(errorMessage)
    }

    if (playerNum == 1) {
      player1Role = if (player2Role.contains(ROLE_POLICEMAN)) Some(ROLE_THIEF) else Some(ROLE_POLICEMAN)
    } else {
      player2Role = if (player1Role.contains(ROLE_POLICEMAN)) Some(ROLE_THIEF) else Some(ROLE_POLICEMAN)
    }

    val thisPlayerAssignedRole = if (playerNum == 1) player1Role else player2Role

    logAndDocumentGameEvent(s"Player $playerNum was assigned role of ${thisPlayerAssignedRole.get} since " +
      s"Player $otherPlayerNum already chose the role of ${otherPlayerRole.get}")

    // Assign the starting position to the player
    val startingPosition = assignRandomPositionToPlayer(playerNum)

    val didPlayerWin = isPlayerInWinningPosition(playerNum)
    if (didPlayerWin) {
      // Declare player the winner and end game
      declarePlayerAsWinnerAndEndGame(playerNum)
    }

    val adjacentPositionsCommaSeparated = getAdjacentNodeIds(
      startingPosition,
      broadcastedPerturbedGraphX.get.value
    ).mkString(", ")
    val adjacentPositionsArrayText = s"[$adjacentPositionsCommaSeparated]"

    val message = s"Player $otherPlayerNum has already chosen to be the ${otherPlayerRole.get}. You are assigned to be the " +
      s"${thisPlayerAssignedRole.get}. You are currently at position $startingPosition. The adjacent positions " +
      s"you can move to are $adjacentPositionsArrayText. $MAKE_MOVE_PROMPT"

    val responseJson = ChooseRoleResponse(
      message,
      getMoveToNewPositionUrl(playerNum),
      getDistanceToNearestWinningPositionUrl(playerNum)
    ).toJson

    val response = ToResponseMarshallable(responseJson)
    complete(response)
  }

  def assignChosenRoleToPlayer(playerNum: Int, chosenRole: String): Route = {
    if (playerNum == 1) {
      player1Role = Some(chosenRole)
    } else {
      player2Role = Some(chosenRole)
    }

    logAndDocumentGameEvent(s"Player $playerNum chose and was assigned the role of $chosenRole.")

    val startingPosition = assignRandomPositionToPlayer(playerNum)

    val didPlayerWin = isPlayerInWinningPosition(playerNum)
    if (didPlayerWin) {
      // Declare player the winner and end game
      declarePlayerAsWinnerAndEndGame(playerNum)
    }

    val adjacentPositionsCommaSeparated = getAdjacentNodeIds(
      startingPosition,
      broadcastedPerturbedGraphX.get.value
    ).mkString(", ")
    val adjacentPositionsArrayText = s"[$adjacentPositionsCommaSeparated]"

    val message = s"You (Player $playerNum) have chosen to be the $chosenRole. You are currently at position:" +
      s" $startingPosition. The adjacent positions you can move to are $adjacentPositionsArrayText. $MAKE_MOVE_PROMPT"

    val responseJson = ChooseRoleResponse(
      message,
      getMoveToNewPositionUrl(playerNum),
      getDistanceToNearestWinningPositionUrl(playerNum),
    ).toJson

    val response = ToResponseMarshallable(responseJson)
    complete(response)
  }

  def handleInvalidMove(playerNum: Int): Route = {
    endGameAndCleanUpGameState

    // Capture which player won
    if (playerNum == 1) {
      // Player 1 lost
      didPlayer2Win = true
    } else {
      // Player 2 lost
      didPlayer1Win = true
    }

    val otherPlayerNum = if (playerNum == 1) 2 else 1

    logAndDocumentGameEvent(s"Player $playerNum made and invalid move and lost the game. Player " +
      s"$otherPlayerNum wins.")

    val message = s"You (player $playerNum) made an invalid move. Player $otherPlayerNum wins."
    complete(message)
  }

  def declarePlayerAsWinnerAndEndGame(playerNum: Int): Route = {
    val playerRole = if (playerNum == 1) player1Role else player2Role

    // Clean up game state
    endGameAndCleanUpGameState

    // Capture which player won
    if (playerNum == 1) {
      didPlayer1Win = true
    } else {
      didPlayer2Win = true
    }

    logAndDocumentGameEvent(s"(Player $playerNum) (${playerRole.get}) won the game!")

    if (playerRole.contains(ROLE_THIEF)) {
      complete(s"You (Player $playerNum) (${playerRole.get}) STOLE the valuable data. You won the game!")
    } else {
      complete(s"You (Player $playerNum) (${playerRole.get}) CAUGHT the $ROLE_THIEF. You won the game!")
    }
  }

  def handleGettingClosestReachableDistanceToWinningPosition(playerNum: Int): Route = {
    val thisPlayerRole = if (playerNum == 1) player1Role else player2Role
    val thisPlayerPosition = if (playerNum == 1) player1Position else player2Position

    if (thisPlayerRole.isEmpty) {
      val errorMessage = s"calculateClosestReachableDistanceToWinningPosition - Player $playerNum role can't be " +
        s"NULL when calling this function - failing the job."
      logger.error(errorMessage)
      throw new RuntimeException(errorMessage)
    }

    val closestReachableDistanceToWinningPosition = if (thisPlayerRole.get == ROLE_POLICEMAN) {
      val otherPlayerPosition = if (playerNum == 1) player2Position else player1Position

      val sourceNode = thisPlayerPosition
      val destinationNode = otherPlayerPosition

      calculateShortestDistance(sourceNode, destinationNode)
    } else {
      // For the thief, figure out the distance to the nearest node with valuable data
      originalValuableNodes
        .map(destinationNode => {
          val shortestDistance = calculateShortestDistance(thisPlayerPosition, destinationNode)
          shortestDistance
        })
        .filter(shortestDistance => shortestDistance != -1) // only get reachable distances
        .minOption
        .getOrElse(-1) // enhancement idea - thief should automatically lose if can't ever reach a valuable node
    }

    val adjacentPositionsCommaSeparated = getAdjacentNodeIds(
      thisPlayerPosition,
      broadcastedPerturbedGraphX.get.value
    ).mkString(", ")
    val adjacentPositionsArrayText = s"[$adjacentPositionsCommaSeparated]"

    val message = s"You (Player $playerNum) (${thisPlayerRole.get}) are at position $thisPlayerPosition. You " +
      s"are $closestReachableDistanceToWinningPosition positions away from the nearest winning position. The " +
      s"adjacent positions you can move to are $adjacentPositionsArrayText. $MAKE_MOVE_PROMPT"

    val responseJson = DistanceToWinningPositionResponse(
      message,
      getMoveToNewPositionUrl(playerNum),
      getDistanceToNearestWinningPositionUrl(playerNum)
    ).toJson
    val response = ToResponseMarshallable(responseJson)

    logAndDocumentGameEvent(s"Player $playerNum (${thisPlayerRole.get} (position $thisPlayerPosition) " +
      s"requested to see distance to nearest winning position which is $closestReachableDistanceToWinningPosition. " +
      s"Adjacent nodes are $adjacentPositionsArrayText")

    complete(response)
  }

  // OTHER HELPER FUNCTIONS

  def loadGraphs(sc: SparkContext): Unit = {
    // 1.) Load original NetGraph
    originalNetGraph = Some(loadNetGraph(true))

    if (originalNetGraph.isEmpty) {
      val errorMessage = s"loadGraphs - originalNetGraph is NULL - failing the job."
      logger.error(errorMessage)
      throw new RuntimeException(errorMessage)
    }

    // 2.) Convert original NetGraph into GraphX object
    val originalGraphX = Some(loadGraphX(originalNetGraph.get, sc, true))

    if (originalGraphX.isEmpty) {
      val errorMessage = s"loadGraphs - originalGraphX is NULL - failing the job."
      logger.error(errorMessage)
      throw new RuntimeException(errorMessage)
    }

    broadcastedOriginalGraphX = Some(sc.broadcast(originalGraphX.get))

    // 3.) Find all nodes in the original graph that have valuable data
    originalValuableNodes = originalNetGraph.get.sm.nodes().stream()
      .filter(node => node.valuableData)
      .map(node => node.id.toLong)
      .collect(Collectors.toList[VertexId])
      .asScala.toList

    logger.info(s"Nodes with valuable data from ORIGINAL (total ${originalValuableNodes.size}): ${originalValuableNodes}")

    // 4.) Load perturbed NetGraph
    perturbedNetGraph = Some(loadNetGraph(false))

    if (perturbedNetGraph.isEmpty) {
      val errorMessage = s"loadGraphs - perturbedNetGraph is NULL - failing the job."
      logger.error(errorMessage)
      throw new RuntimeException(errorMessage)
    }

    // 5.) Convert perturbed NetGraph into GraphX object
    val perturbedGraphX = Some(loadGraphX(perturbedNetGraph.get, sc, false))

    if (perturbedGraphX.isEmpty) {
      val errorMessage = s"loadGraphs - perturbedGraphX is NULL - failing the job."
      logger.error(errorMessage)
      throw new RuntimeException(errorMessage)
    }

    broadcastedPerturbedGraphX = Some(sc.broadcast(perturbedGraphX.get))
  }

  def loadNetGraph(isOriginal: Boolean): NetGraph = {
    val resourcePath = if (isOriginal) Configuration.getOriginalGraphPath else Configuration.getPerturbedGraphPath
    val netGraphPath = Main.getClass.getResource(resourcePath).getPath
    val netGraphLoaded = NetGraph.load(netGraphPath, dir = "")
    val originalOrPerturbedText = if (isOriginal) "ORIGINAL" else "PERTURBED"

    if (netGraphLoaded.isEmpty) {
      val errorMessage = s"Loading ${originalOrPerturbedText} NetGraph FAILED - failing the job."
      logger.error(errorMessage)
      throw new RuntimeException(errorMessage)
    }

    logger.info(s"Loading ${originalOrPerturbedText} NetGraph SUCCEEDED")

    val netGraph = netGraphLoaded.get
    logger.info(s"${originalOrPerturbedText} graph has ${netGraph.totalNodes} nodes")
    logger.info(s"${originalOrPerturbedText} graph has ${netGraph.sm.edges().size()} edges")

    netGraph
  }

  def loadGraphX(netGraph: NetGraph, sc: SparkContext, isOriginal: Boolean): Graph[NodeObject, Double] = {
    // get GraphX vertices
    val verticesList = netGraph.sm.nodes().asScala.toList
      .map((node) => (node.id.toLong, node))

    val verticesRDD = sc.parallelize(verticesList)

    // Get perturbed GraphX edges
    val edgesList = netGraph.sm.edges().asScala.toList
      .map((edgeAsEndpointPair) => generateGraphXEdge(netGraph, edgeAsEndpointPair))

    val edgesRDD = sc.parallelize(edgesList)

    // Construct GraphX graph
    val graphX = Graph(verticesRDD, edgesRDD)

    graphX.subgraph()

    // Confirm that we've successfully constructed a GraphX graph
    val originalOrPerturbedText = if (isOriginal) "ORIGINAL" else "PERTURBED"
    logger.info(s"the ${originalOrPerturbedText} GraphX has ${graphX.vertices.count()} nodes")
    logger.info(s"the ${originalOrPerturbedText} GraphX has ${graphX.edges.count()} edges")

    graphX
  }

  def generateGraphXEdge(originalGraph: NetGraph, edgeAsEndpointPair: EndpointPair[NodeObject]): Edge[Double] = {
    val sourceNodeId = edgeAsEndpointPair.source().id
    val destinationNodeId = edgeAsEndpointPair.target().id
    val edgeValue = originalGraph.sm.edgeValue(edgeAsEndpointPair)

    if (edgeValue.toScala.isEmpty) {
      val errorMessage = s"Failed to read edge value from node ${sourceNodeId} to node ${destinationNodeId}"
      logger.error(errorMessage)
      throw new RuntimeException(errorMessage)
    }

    Edge(sourceNodeId, destinationNodeId, edgeValue.get().cost)
  }

  // Assigns a random position to a player and returns that random position
  def assignRandomPositionToPlayer(playerNum: Int): Int = {
    if (originalNetGraph.isEmpty) {
      val errorMessage = s"assignRandomPositionToPlayer - originalNetGraph is NULL when trying to assign position - " +
        s"failing the job."
      logger.error(errorMessage)
      throw new RuntimeException(errorMessage)
    }

    if (perturbedNetGraph.isEmpty) {
      val errorMessage = s"assignRandomPositionToPlayer - perturbedNetGraph is NULL when trying to assign position - " +
        s"failing the job."
      logger.error(errorMessage)
      throw new RuntimeException(errorMessage)
    }

    val numOriginalNodes = originalNetGraph.get.totalNodes

    val randomPosition = Random.nextInt(numOriginalNodes)

    // Re-generate random position if random node doesn't exist in perturbed graph
    if (randomPosition < 0 || randomPosition > perturbedNetGraph.get.totalNodes) {
      logger.info(s"assignRandomPositionToPlayer - Node $randomPosition no longer exists in the perturbed graph - " +
        s"re-assigning a random position to player $playerNum")
      assignRandomPositionToPlayer(playerNum)
    }

    // If node DOES exist in perturbed graph
    if (playerNum == 1) {
      player1Position = randomPosition
    } else {
      player2Position = randomPosition
    }

    logAndDocumentGameEvent(s"Player $playerNum randomly assigned to position $randomPosition")

    randomPosition
  }

  // Returns a boolean indicating whether a player is in a winning position
  def isPlayerInWinningPosition(playerNum: Int): Boolean = {
    val currentPosition = if (playerNum == 1) player1Position else player2Position
    val playerRole = if (playerNum == 1) player1Role else player2Role

    logger.info(s"isPlayerInWinningPosition - checking if Player $playerNum (${playerRole.get}) " +
      s"(position $currentPosition) is currently in a winning position")

    if (playerRole.contains(ROLE_THIEF)) {
      // Retrieve node object associated with the current position of the player in a perturbed graph
      val perturbedNodeObject = broadcastedPerturbedGraphX.get.value.vertices.filter((vertexId: VertexId, _) => {
        vertexId == currentPosition
      }).first()._2

      perturbedNodeObject.valuableData
    } else {
      // Player is a policeman, so check if player is in same position as the thief
      player1Position == player2Position
    }
  }

  // Resets the game state
  def endGameAndCleanUpGameState: Unit = {
    numPlayers = 0
    player1Role = None
    player2Role = None
    player1Position = 0
    player2Position = 0
    isGameActive = false
  }

  // Returns a list of reachable destination nodes one hop away from currentPosition
  def getAdjacentNodeIds(currentPosition: Int, graph: Graph[NodeObject, Double]): List[VertexId] = {
    val allEdgesWithCurrentPositionAsSource = graph.edges
      .filter((edge) => {
        edge.srcId == currentPosition
      })

    val adjacentDestinationNodeIds = allEdgesWithCurrentPositionAsSource
      .map((edge) => {
        edge.dstId
      })
      .collect()
      .toList

    adjacentDestinationNodeIds
  }

  // Calculates the shortest distance between source node and destination node
  def calculateShortestDistance(sourceNode: VertexId, destinationNode: VertexId): Int = {
    // Initialize hop count
    val initialGraph = broadcastedPerturbedGraphX.get.value.mapVertices((id, _) => if (id == sourceNode) 0 else -1)

    // Define the Pregel function
    def pregelFunction(id: VertexId, hops: Int, newHops: Int): Int = {
      val updatedHops = math.min(hops, newHops)
      if (id == destinationNode) {
        val minHops = math.min(updatedHops, 1)
        logger.info(s"Vertex $id reached destination with hops: $minHops")
        minHops
      } else {
        logger.info(s"Vertex $id: UpdatedHops=$updatedHops, Dest=$destinationNode")
        updatedHops
      }
    }

    // Run Pregel until the destination node is reached
    val maxIterations = 50
    val minHopsGraph = initialGraph.pregel(
      -1, // Using -1 as a marker for unreachable nodes
      maxIterations,
      activeDirection = EdgeDirection.Out
    )(
      (id, hops, newHops) => pregelFunction(id, hops, newHops), // Vertex Program
      triplet => {
        if (triplet.srcId == destinationNode) {
          // If the destination node is reached, send a message to terminate
          println(s"Terminating at ${triplet.dstId} with hops ${triplet.srcAttr + 1}")
          Iterator((triplet.dstId, 0))
        } else {
          // Otherwise, continue with hop count
          println(s"Continue at ${triplet.dstId} with hops ${triplet.srcAttr + 1}")
          Iterator((triplet.dstId, triplet.srcAttr + 1))
        }
      }, // Send Message
      (a, b) => math.min(a, b) // Merge Message
    )

    // Display the minimum hops to the destination node
    val minHops = minHopsGraph.vertices.filter { case (id, _) => id == destinationNode }.collect()
      .headOption

    if (minHops.isEmpty) {
      -1
    } else {
      minHops.get._2
    }
  }

  def getDistanceToNearestWinningPositionUrl(playerNum: Int): String = {
    s"http://localhost:9090/$PT_GAME_PATH_SEGMENT/player/$playerNum/distance-to-nearest-winning-position"
  }

  def getMoveToNewPositionUrl(playerNum: Int): String = {
    s"http://localhost:9090/pt-game/player/$playerNum/move-to/<position>"
  }

  // Function to log the move to moves.txt
  private def logAndDocumentGameEvent(eventText: String): Unit = {
    logger.info(eventText)
    val movesFilePath = "gameEvents.txt"

    // Create the file if it doesn't exist
    if (!Files.exists(Paths.get(movesFilePath))) {
      Files.createFile(Paths.get(movesFilePath))
    }

    // Open the file in append mode
    val writer = new BufferedWriter(new FileWriter(movesFilePath, true))

    try {
      // Write the log entry
      val logEntry = s"$eventText\n\n"
      writer.write(logEntry)
    } finally {
      // Close the writer to release resources
      writer.close()
    }
  }
}




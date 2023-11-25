## Homework 3
#### Natalia Szlaszynski
#### CS 441, UIC
#### nszlas2@uic.edu

### Overview
#### This assignment implements a microservice for the Policeman/Thief game using Akka HTTP, where users send requests to the server and recieve responses regarding the player info. and it's adjacent nodes. The game starts when the first player that joins chooses their role as a Policeman or a Thief, with the latter assigned the opposite role, and both Policeman and Thief are then placed at random nodes on the graph. 
#### The Thief can send a request to the specified endpoint in the Url in order to find the nearest node with valuable data, and the Policeman may do the same in order to find the distance/number of positions they are from the Theif node.
#### The Pregel API is used for computing the shortest distance between the current position and winning position. The gameEvents.txt file contains logs of the moves the players made throughout the game.

### User's Guide on How to Play
#### In your local terminal, do the following: "git clone https://github.com/thecoder0/CS441_HW3.git"
#### Next, run the "sbt clean compile" command to build the program and "sbt run" to run the program or simply run PTGraphGameServer Run Configuration in IntelliJ

#### Now you are ready to start the game!

#### First, open Postman, or an HTTP client of your choosing (at the top level of the project you can find a Postman collection for easier testing)
#### Send a GET request to http://localhost:9090/pt-game/start-game - this will make you Player 1
#### Next, choose Player 1's role as a player by sending a GET request to either http://localhost:9090/pt-game/player/1/choose-role/policeman or http://localhost:9090/pt-game/player/1/choose-role/thief
#### Next, send a GET request to http://localhost:9090/pt-game/start-game again to enter as Player 2. Since Player 1 already chose a role, the other role will be assigned to Player 2
#### Choosing a role for either player will also put them in a random position in the perturbed graph
#### Each player can send a GET request to http://localhost:9090/pt-game/player/<playerNum>/distance-to-nearest-winning-position to figure out how far they are from the nearest winning position (sometimes, it returns -1 since the pregel algorithm isn't working right for some reason)
#### Each player can also send a GET request to http://localhost:9090/pt-game/player/<playerNum>/move-to/<position> to move to a new position in the perturbed graph. If an invalid move is made (in case that directed edge doesn't exist in either the perturbed graph or the original graph, then the user automatically loses.
#### Every time a player makes a move, a check is made to see if they are on a winning position based on their role. If a player is on a winning position, the game is won and the user is told they won.

### Dependencies
#### Netgamesim - https://github.com/0x1DOCD00D/NetGameSim (Owned by Dr. Mark Grechanik)

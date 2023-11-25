## Homework 3
#### Natalia Szlaszynski
#### CS 441, UIC
#### nszlas2@uic.edu

### Overview
#### This assignment implements a microservice for the Policeman/Thief game using Akka HTTP, where users send requests to the server and recieve responses regarding the player info. and it's adjacent nodes. The game starts when the first player that joins chooses their role as a Policeman or a Thief, with the latter assigned the opposite role, and both Policeman and Thief are then placed at random nodes on the graph. 
#### The Thief can send a request to the specified endpoint in the Url in order to find the nearest node with valuable data, and the Policeman may do the same in order to find the distance/number of positions they are from the Theif node.
#### The Pregel API is used for computing the shortest distance between the current position and winning position. The Yaml file contains logs of the moves the players made throughout the game.

### User's Guide on How to Play
#### In your local terminal, do the following: "git clone <my-CS441-HW3-repo>"
#### Next, run the "sbt clean compile" command to build the program

#### Now you are ready to start the game!

#### First, open Postman, or an HTTP client of your choosing
#### Paste the start game Url in the search bar: // TODO
#### Next, indicate your role as a player:
#### Policeman: // TODO: choose policeman url goes here
####    or
#### Thief: // TODO: choose thief url
#### Start the game again as the other player by clicking SEND url in Postman
#### To make a move, hit the endpoints for either:
#### Player 1:
#### // TODO: list player 1 move to endpoints
#### Player 2:
#### // TODO: list player 2 move to endpoints

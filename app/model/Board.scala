package model

import play.api.libs.json.Json

import scala.collection.mutable.ListBuffer
import scala.util.Random

/**
 * Created by greg.rubino on 10/2/15.
 */
case class Board(gameId: String,
                 layers: Vector[Vector[Place]],
                 monkeyStarts: List[List[Monkey]],
                 maxStack: Int,
                 diceCount: Int,
                 currentPlayer: Int,
                 turnIndex: Int,
                 playerCount: Int,
                 bananaCards: BananaCards) {

  private def prevIndex(playerId: Int, positionTuple: (Int, Int)): (Int, Int) = {
    val layerIndex = positionTuple._1
    val placeIndex = positionTuple._2
    if(layerIndex == -1 & placeIndex == -1)
      (-1, -1)
    else {
      val slideDown = layers(layerIndex)(placeIndex).getSlideDown == playerId
      val newLayerIndex = if(slideDown) { layerIndex-1 } else { layerIndex }
      val newPlaceIndex =
        if(slideDown) {
          layers(layerIndex-1) indexWhere(p => p.getSlideUp == playerId)
        } else {
          ((placeIndex-1)+layers(layerIndex).length)%layers(layerIndex).length
        }
      (newLayerIndex, newPlaceIndex)
    }
  }

  private def nextIndex(playerId: Int, positionTuple: (Int, Int)): (Int, Int) = {
    val layerIndex = positionTuple._1
    val placeIndex = positionTuple._2
    if(layerIndex == -1 & placeIndex == -1) {
      (1, layers.head.indexWhere(p => p.getSlideDown == playerId))
    } else {
      val slideUp = layers(layerIndex)(placeIndex).getSlideUp == playerId
      val newLayerIndex = if(slideUp) { layerIndex+1 } else { layerIndex }
      val newPlaceIndex =
        if(slideUp) {
          if(newLayerIndex < 5) {
            layers(layerIndex + 1) indexWhere (p => p.getSlideDown == playerId)
          } else {
            0
          }
        } else {
          (placeIndex+1)%layers(layerIndex).length
        }
      (newLayerIndex, newPlaceIndex)
    }
  }
  private def targetIndex(playerId: Int, positionTuple: (Int, Int), distance: Int): (Int, Int) = {
    if(distance == 1) {
      nextIndex(playerId, positionTuple)
    } else {
      targetIndex(playerId, nextIndex(playerId, positionTuple), distance-1)
    }
  }

  private def canBump(monkeys: List[Monkey], place: Place): Boolean = {
    val playerId = monkeys.head.playerId
    val monkeyCount = monkeys.length
    if(place.monkeys.forall(_.playerId == playerId)) {
      false
    } else {
      val playerCounts = for (i: Int <- place.monkeys.map(m => m.playerId).toSet;
                              n = place.monkeys.count(_.playerId == i); if i != playerId) yield n
      playerCounts.sum < (monkeyCount + place.monkeys.count(_.playerId == playerId))
    }
  }

  private def findLayerOverflows(index: Int): List[(Int, Int)] = {
    val layer = layers(index)
    val layerOverflows = for(
      placeIndex <- layer.indices;
      overflow = (index, placeIndex)
      if layer(placeIndex).monkeys.length > maxStack) yield overflow
    layerOverflows.toList
  }

  private def findOverflows(): List[(Int, Int)] = {
    val overflows = for(layerIndex <- layers.indices;
                        layerOverflows = findLayerOverflows(layerIndex)) yield layerOverflows
    overflows.reduce((l, m) => l ++ m)
  }

  private def playerLayerStart(monkey: Monkey, layerIndex: Int): Int = {
    if(layerIndex == -1) -1 else {
      layers(layerIndex).indexWhere(
        place =>
          place.slideDown match {
            case Some(slide) => slide == monkey.playerId case None => false
          })
    }
  }
  private def findMovableForward(monkeys: List[Monkey], location: (Int, Int)): (Int, Int) = {
    val tryLocation = nextIndex(monkeys.head.playerId, location)
    if(isMovable(monkeys, layers(location._1)(location._2))) tryLocation
    else findMovableForward(monkeys, tryLocation)
  }
  private def findMovableBackward(monkeys: List[Monkey], location: (Int, Int)): (Int, Int) = {
    val tryLocation = prevIndex(monkeys.head.playerId, location)
    if(isMovable(monkeys, layers(location._1)(location._2))) tryLocation
    else findMovableBackward(monkeys, tryLocation)
  }

  private def bump(monkey: Monkey, location: (Int, Int)): Board = {
    val endLayer = location._1-1
    val endPlace: (Int, Int) =
      if(endLayer >= 0) {
        val firstTry =
          (location._2 * layers(endLayer).length) / layers(location._1).length +
            (if(location._1 < 5 && layers(location._1).length % layers(endLayer).length != 0) 1
              else 0)
        if(location._1 < 5 && !isMovable(List(monkey), layers(endLayer)(firstTry))) {
          findMovableForward(List(monkey), (endLayer, firstTry))
        } else if(location._2 >= 5 && !isMovable(List(monkey), layers(endLayer)(firstTry))) {
          findMovableBackward(List(monkey), (endLayer, firstTry))
        } else {
          (endLayer, firstTry)
        }
      } else (-1, -1)
    moveMonkeys(List(monkey), location, endPlace)
  }

  private def resolveBump(overflow: (Int, Int)): Board = {
    val place = layers(overflow._1)(overflow._2)
    val monkeySet = place.monkeys.toSet
    val bumped = monkeySet.find(monkey => place.monkeys.minBy(m => place.monkeys.count(_.playerId == m.playerId)) == monkey)
    bumped match {
      case Some(monkey) => bump(monkey, overflow)
      case None => this
    }

  }

  private def resolveBumps(): Board = {
    val overflows = findOverflows()
    if(overflows.nonEmpty) {
      resolveBump(overflows.head).resolveBumps()
    } else {
      this
    }
  }

  private def isMovable(monkeys: List[Monkey], place: Place): Boolean = {
    !(place.monkeys.length == maxStack && !canBump(monkeys, place))
  }

  private def updatedTargetLayer(monkeys: List[Monkey], end: (Int, Int)): Vector[Vector[Place]] = {
    val targetPlace = layers(end._1)(end._2)
    if(!isMovable(monkeys, targetPlace)) {
      throw new IllegalStateException("too many monkeys to one spot")
    }
    val updatedTargetPlace = targetPlace.addMonkeys(monkeys)
    layers.updated(end._1, updatedLayer(layers(end._1), updatedTargetPlace, end._2))
  }

  private def updatedStartLayer(monkeys: List[Monkey], start: (Int, Int)): Vector[Vector[Place]] = {
    val startPlace = layers(start._1)(start._2)
    val playerId = monkeys.head.playerId
    if(startPlace.monkeys.count(_.playerId == playerId) == 0) {
      throw new IllegalStateException("no monkeys to move")
    }
    val updatedStartPlace = startPlace.removeMonkeys(monkeys)
    layers.updated(start._1, updatedLayer(layers(start._1), updatedStartPlace, start._2))
  }

  private def awardBananaCard(landingPlace: Place): BananaCards = {
    if(bananaCards.deck.nonEmpty) {
      BananaCards(bananaCards.deck.tail, bananaCards.deck.head :: bananaCards.active, bananaCards.discard)
    } else if(bananaCards.discard.nonEmpty) {
      val r = new Random
      val shuffled = r.shuffle(bananaCards.discard)
      BananaCards(shuffled.tail, shuffled.head :: bananaCards.active, List())
    } else {
      bananaCards
    }
  }

  private def withUpdatedStartLayer(monkeys: List[Monkey], start: (Int, Int), newMonkeyStarts: List[List[Monkey]]): Board = {
    Board(gameId, updatedStartLayer(monkeys, start), newMonkeyStarts, maxStack, diceCount, currentPlayer, turnIndex, playerCount, bananaCards)
  }

  private def withUpdatedTargetLayer(monkeys: List[Monkey], end: (Int, Int), newMonkeyStarts: List[List[Monkey]]): Board = {
    Board(gameId, updatedTargetLayer(monkeys, end), newMonkeyStarts, maxStack, diceCount, currentPlayer, turnIndex, playerCount, awardBananaCard(layers(end._1)(end._2)))
  }

  private def moveMonkeys(monkeys: List[Monkey], start: (Int, Int), end: (Int, Int)): Board = {

    if(monkeys.isEmpty) {
      throw new IllegalArgumentException("tried to move no monkeys")
    }

    val playerId = monkeys.head.playerId
    if(!monkeys.forall(_.playerId == playerId)) {
      throw new IllegalArgumentException("tried to move multicolored monkeys")
    } else if(start._1 == -1 && start._2 == -1) {
      val newMonkeyStarts = monkeyStarts.updated(playerId, monkeyStarts(playerId).filter(m => !monkeys.contains(m)))
      this.withUpdatedTargetLayer(monkeys, end, newMonkeyStarts)
    } else if(end._1 == -1 && end._2 == -1) {
      val newMonkeyStarts = monkeyStarts.updated(playerId, monkeyStarts(playerId) ++
        layers(start._1)(start._2).monkeys.filter(m => monkeys.contains(m)))
      this.withUpdatedStartLayer(monkeys, start, newMonkeyStarts)
    } else {
      this.
        withUpdatedStartLayer(monkeys, start, monkeyStarts).
        withUpdatedTargetLayer(monkeys, end, monkeyStarts)
    }

  }

  private def advance(playerId: Int, layerIndex: Int, placeIndex: Int, monkeyCount: Int, distance: Int): Board = {
    val targetPosition = targetIndex(playerId, (layerIndex, placeIndex), distance)
    val monkeys = if(layerIndex == -1 && placeIndex == -1) {
      monkeyStarts(playerId).take(monkeyCount)
    } else {
      layers(layerIndex)(placeIndex).monkeys.filter(m => m.playerId == playerId).take(monkeyCount)
    }
    moveMonkeys(monkeys, (layerIndex, placeIndex), targetPosition)
  }

  private def makeMove(move: Move): Board = {
    if(move.monkeyCount > 1 && (move.distance.length != move.monkeyCount || !move.distance.forall(_ == move.distance.head))) {
      throw new IllegalStateException("illegal move")
    }
    advance(move.playerId, move.layerIndex, move.placeIndex, move.monkeyCount, move.distance.sum).resolveBumps()
  }

  private def incrementTurn(): Board = {
    val newCurrentPlayer =
      if(bananaCards.active.nonEmpty) {
        currentPlayer
      } else {
        (currentPlayer+1)%playerCount
      }
    Board(gameId, layers, monkeyStarts, maxStack, diceCount, newCurrentPlayer, turnIndex+1, playerCount, bananaCards)
  }

  private def extractDice(move: Move, dice: List[Int]): List[Int] = {
    dice.diff(move.distance)
  }

  def consumeDice(diceDecisions: List[Move], diceRolls: List[Int]): Board = {
    if(diceDecisions.isEmpty) { this }
    val decision = diceDecisions.head
    if(decision.playerId != currentPlayer) {
      throw new IllegalArgumentException("it is not player "+decision.playerId+"'s turn")
    }
    val remainingDecisions = diceDecisions.tail
    val remainingDice = extractDice(decision, diceRolls)
    if(diceRolls.length > diceCount || (remainingDice.nonEmpty && remainingDice.length == diceRolls.length)) {
      throw new IllegalStateException("illegal move")
    } else if(remainingDecisions.nonEmpty) {
      makeMove(decision).consumeDice(remainingDecisions, remainingDice)
    } else {
      makeMove(decision).incrementTurn()
    }
  }

  private def updatedLayer(layer: Vector[Place], newPlace: Place, placeIndex: Int): Vector[Place] = {
    layer.updated(placeIndex, newPlace)
  }

}

object MonkeyStartGenerator {
  def generateMonkeyStarts(playerCount: Int, monkeyCount: Int): List[List[Monkey]] = {
    (for(i <- 0 to playerCount-1;
      monkeys = (for(j <- 1 to monkeyCount; monkey = Monkey(i, i*100+j)) yield monkey).toList) yield monkeys).toList
  }
}

object LayerGenerator {
  def generateLayers(playerCount: Int): Vector[Vector[Place]] = {
    (for(
      i <- 0 to 5;
      layer = if(i < 5) generateLayer(playerCount, i) else Vector(Place(List(), None, None))
    ) yield layer).toVector
  }
  private def generateLayer(playerCount: Int, layerIndex: Int): Vector[Place] = {
    (for(
      i <- 1 to playerCount * sideLength(layerIndex);
      place = Place(List(), getSlideUp(playerCount, layerIndex, i-1), getSlideDown(playerCount, layerIndex, i-1))
    ) yield place).toVector
  }
  private def getSlideUp(playerCount: Int, layerIndex: Int, placeIndex: Int): Option[Int] = {
    getSlideDown(playerCount, layerIndex, placeIndex+1)
  }
  private def getSlideDown(playerCount: Int, layerIndex: Int, placeIndex: Int): Option[Int] = {
    if(placeIndex % sideLength(layerIndex) == 0) {
      Option(placeIndex / sideLength(layerIndex) % playerCount)
    } else {
      None
    }
  }
  def sideLength(layerIndex: Int): Int = {
    layerIndex match {
      case 0 => 4
      case 1 => 4
      case 2 => 3
      case 3 => 3
      case 4 => 2
      case 5 => 0
    }
  }
}

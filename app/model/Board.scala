package model

import scala.collection.mutable.ListBuffer
import scala.util.Random

/**
 * Created by greg.rubino on 10/2/15.
 */
case class Board(gameId: String,
                 layers: Vector[Vector[Place]],
                 monkeyStarts: List[Int],
                 maxStack: Int,
                 diceCount: Int,
                 currentPlayer: Int,
                 turnIndex: Int,
                 playerCount: Int,
                 bananaCards: BananaCards) {

  private def nextIndex(playerId: Int, positionTuple: (Int, Int)): (Int, Int) = {
    val layerIndex = positionTuple._1
    val placeIndex = positionTuple._2
    if(layerIndex == -1 & placeIndex == -1) {
      (0, layers.head.indexWhere(p => p.getSlideUp == playerId))
    } else {
      val slideUp = layers(layerIndex)(placeIndex).getSlideUp == playerId
      val newLayerIndex = if(slideUp) { layerIndex + 1 } else { layerIndex }
      val newPlaceIndex = if(slideUp) { layers(layerIndex+1) indexWhere(p => p.getSlideDown == playerId) } else { placeIndex+1 }
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

  private def canBump(playerId: Int, monkeyCount: Int, place: Place): Boolean = {
    if(place.monkeys.forall(_ == playerId)) {
      false
    } else {
      val playerCounts = for (i: Int <- place.monkeys.toSet; n = place.monkeys.count(_ == i); if i != playerId) yield n
      playerCounts.sum < (monkeyCount + place.monkeys.count(_ == playerId))
    }
  }

  private def findLayerOverflows(index: Int): List[(Int, Int)] = {
    val layer = layers(index)
    val layerOverflows = for(
      placeIndex <- 0 to layer.length;
      overflow = (index, placeIndex)
      if layer(placeIndex).monkeys.length > maxStack) yield overflow
    layerOverflows.toList
  }

  private def findOverflows(): List[(Int, Int)] = {
    val overflows = for(layerIndex <- 0 to layers.length; layerOverflows = findLayerOverflows(layerIndex)) yield layerOverflows
    overflows.reduce((l, m) => l ++ m)
  }

  private def playerLayerStart(monkey: Int, layerIndex: Int): Int = {
    if(layerIndex == -1) -1 else {
      layers(layerIndex).indexWhere(
        place =>
          place.slideDown match {
            case Some(slide) => slide == monkey case None => false
          })
    }
  }

  private def playerLayerProgress(monkey: Int, layerIndex: Int, placeIndex: Int): Int = {
    val startIndex = playerLayerStart(monkey, layerIndex)
    (placeIndex - startIndex) % layers(layerIndex).length
  }

  private def bump(monkey: Int, location: (Int, Int)): Board = {
    val endLayer = location._1-1
    val endPosition = playerLayerStart(monkey, endLayer) +
      (playerLayerProgress(monkey, location._1, location._2) * layers(location._1).length) / layers(endLayer).length
    val endLocation = (endLayer, endPosition)
    moveMonkeys(monkey, 1, location, endLocation)
  }

  private def resolveBump(overflow: (Int, Int)): Board = {
    val place = layers(overflow._1)(overflow._2)
    val monkeySet = place.monkeys.toSet
    val bumped = monkeySet.find(monkey => place.monkeys.minBy(m => place.monkeys.count(_ == m)) == monkey)
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

  private def updatedLayers(playerId: Int, monkeyCount: Int, start: (Int, Int), end: (Int, Int)): Vector[Vector[Place]] = {

    val startPlace = layers(start._1)(start._2)
    if(startPlace.monkeys.count(_ == playerId) < monkeyCount) {
      throw new IllegalStateException("tried to move nonexistent monkeys")
    }
    val targetPlace = layers(end._1)(end._2)
    if(targetPlace.monkeys.length + monkeyCount == maxStack && !canBump(playerId, monkeyCount, targetPlace)) {
      throw new IllegalStateException("too many monkeys to one spot")
    }
    val updatedStartPlace = startPlace.removeMonkeys(playerId, monkeyCount)
    val updatedTargetPlace = targetPlace.addMonkeys(playerId, monkeyCount)
    val updatedLayersStart = layers.updated(start._1, updatedLayer(layers(end._1), updatedStartPlace, start._1))
    updatedLayersStart.updated (end._1, updatedLayer(updatedLayersStart(end._1), updatedTargetPlace, end._2))

  }

  private def awardBananaCard(landingPlace: Place): BananaCards = {
    if(bananaCards.deck.nonEmpty) {
      BananaCards(bananaCards.deck.tail, bananaCards.deck.head :: bananaCards.active, bananaCards.discard)
    } else {
      val r = new Random
      val shuffled = r.shuffle(bananaCards.discard)
      BananaCards(shuffled.tail, shuffled.head :: bananaCards.active, List())
    }
  }

  private def moveMonkeys(playerId: Int, monkeyCount: Int, start: (Int, Int), end: (Int, Int)): Board = {
    val newMonkeyStarts = if(start._1 == -1 & start._2 == -1) {
      monkeyStarts.updated(playerId, monkeyStarts(playerId) - 1)
    } else {
      monkeyStarts
    }
    val updatedLayersEnd = updatedLayers(playerId, monkeyCount, start, end)
    val updatedBananaCards = awardBananaCard(layers(end._1)(end._2))
    Board(gameId, updatedLayersEnd, newMonkeyStarts, maxStack, diceCount, currentPlayer, turnIndex, playerCount, updatedBananaCards)
  }

  private def advance(playerId: Int, layerIndex: Int, placeIndex: Int, monkeyCount: Int, distance: Int): Board = {
    val targetPosition = targetIndex(playerId, (layerIndex, placeIndex), distance)
    moveMonkeys(playerId, monkeyCount, (layerIndex, placeIndex), targetPosition)
  }

  private def makeMove(move: Move): Board = {
    if(move.monkeyCount > 1 && (move.distance.length != move.monkeyCount || !move.distance.forall(_ == move.distance.head))) {
      throw new IllegalStateException("illegal move")
    }
    advance(move.playerId, move.layerIndex, move.placeIndex, move.monkeyCount, move.distance.sum).resolveBumps()
  }

  private def extractDice(move: Move, dice: List[Int]): List[Int] = {
    dice.diff(move.distance)
  }

  def consumeDice(diceDecisions: List[Move], diceRolls: List[Int]): Board = {
    val decision = diceDecisions.head
    val remainingDecisions = diceDecisions.tail
    val remainingDice = extractDice(decision, diceRolls)
    if(diceRolls.length > diceCount || remainingDice.length == diceRolls.length) {
      throw new IllegalStateException("illegal move")
    } else if(remainingDecisions.nonEmpty) {
      makeMove(decision).consumeDice(remainingDecisions, remainingDice)
    } else {
      makeMove(decision)
    }
  }

  private def updatedLayer(layer: Vector[Place], newPlace: Place, placeIndex: Int): Vector[Place] = {
    layer updated(placeIndex, newPlace)
  }

}

package model

import play.api.libs.json.Json

import scala.annotation.tailrec
import scalaz._
import syntax.bitraverse._
import std.option._
import std.tuple._
import scala.util.Random

/**
  * Created by greg.rubino on 10/2/15.
  */
case class Board(gameId: String,
                 playerTokens: List[String],
                 monkeys: Map[Monkey, (Int, Int)],
                 maxStack: Int,
                 diceCount: Int,
                 currentPlayer: Int,
                 turnIndex: Int,
                 playerCount: Int,
                 bananaCards: BananaCards) {

  val sideLength = List(4, 4, 4, 3, 3, 2, 1)
  val layerLength = sideLength.map(_ * playerCount)
  val

  private def layerStart(playerId: Int, layer: Int): Int = playerId * sideLength(layer)

  private def prevIndex(playerId: Int, positionTuple: Option[(Int, Int)]): Option[(Int, Int)] = {
    positionTuple.flatMap {
      case (layer, place) =>
        val atBeginning = place == layerStart(playerId, layer)
        val newLayerIndex = if (!atBeginning) { Some(layer) } else if (layer > 0) { Some(layer - 1) } else { None }
        val newPlaceIndex = if (!atBeginning) {
          Some(layer-1)
        } else if(layer > 0) {
          Some((layerStart(playerId, layer-1) + layerLength(layer-1) - 1) % layerLength(layer-1))
        } else {
          None
        }
        Zip[Option].zip(newLayerIndex, newPlaceIndex)
    }
  }

  private def nextIndex(playerId: Int, positionTuple: Option[(Int, Int)]): Option[(Int, Int)] = {
    positionTuple.map {
      case (layer, place) =>
        val atEnd = place == (layerStart(playerId, layer) + layerLength(layer)) % layerLength(layer)
        val newLayer = if (!atEnd) { Some(layer) } else if (layer < layerLength.length - 1) {
          Some(layer+1)
        } else {
          None
        }
        val newPlace = if (!atEnd) { Some(layer) } else if (layer < layerLength.length - 1) {
          Some(layerStart(playerId, layer+1))
        } else {
          None
        }
        Zip[Option].zip(newLayer, newPlace)
    }.getOrElse(Some((0, layerStart(playerId, 0))))
  }

  private def checkWinCondition: Option[Int] = {
    val winners = monkeys.filter { case (monkey, (layer, place)) => layer > (layerLength.length - 2) }
      .map(_._1).groupBy(_.playerId).filter { case (pId, ms) => ms.size > 5 }
    if (winners.isEmpty) { None } else if (winners.size == 1) { Some(winners.head._1) } else {
      throw new Exception("more than one winner")
    }
  }

  @tailrec
  private def targetIndex(playerId: Int, positionTuple: Option[(Int, Int)], distance: Int): Option[(Int, Int)] = {
    if (distance == 1) {
      nextIndex(playerId, positionTuple)
    } else {
      targetIndex(playerId, nextIndex(playerId, positionTuple), distance - 1)
    }
  }

  private def canBump(playerId: Int, monkeyCount: Int, place: Place): Boolean = {
    if (place.monkeys.length + monkeyCount <= maxStack) true
    else if (place.monkeys.forall(_.playerId == playerId)) false
    else if (place.monkeys.length + monkeyCount == maxStack + 1) {
      val playerCounts = for (i: Int <- place.monkeys.map(m => m.playerId).toSet;
                              n = place.monkeys.count(_.playerId == i); if i != playerId) yield n
      playerCounts.sum < (monkeyCount + place.monkeys.count(_.playerId == playerId))
    } else false
  }

  private def canBump(monkeys: List[Monkey], place: Place): Boolean = {
    val playerId = monkeys.head.playerId
    val monkeyCount = monkeys.length
    canBump(playerId, monkeyCount, place)
  }

  private def findLayerOverflows(index: Int): List[(Int, Int)] = {
    val layer = layers(index)
    val layerOverflows = for (
      placeIndex <- layer.indices;
      overflow = (index, placeIndex)
      if layer(placeIndex).monkeys.length > maxStack) yield overflow
    layerOverflows.toList
  }

  private def findOverflows(): List[(Int, Int)] = {
    val overflows = for (layerIndex <- layers.indices;
                         layerOverflows = findLayerOverflows(layerIndex)) yield layerOverflows
    overflows.reduce((l, m) => l ++ m)
  }

  private def playerLayerStart(monkey: Monkey, layerIndex: Int): Int = {
    if (layerIndex == -1) -1
    else {
      layers(layerIndex).indexWhere(
        place =>
          place.slideDown match {
            case Some(slide) => slide == monkey.playerId
            case None => false
          })
    }
  }

  @tailrec
  private def findMovableForward(monkeys: List[Monkey], location: Option[(Int, Int)]): (Int, Int) = {
    val tryLocation = nextIndex(monkeys.head.playerId, location)
    if (isMovable(monkeys, layers(tryLocation._1)(tryLocation._2))) tryLocation
    else findMovableForward(monkeys, Some(tryLocation))
  }

  @tailrec
  private def findMovableBackward(monkeys: List[Monkey], location: (Int, Int)): (Int, Int) = {
    val tryLocation = prevIndex(monkeys.head.playerId, Some(location))
    if (isMovable(monkeys, layers(location._1)(location._2))) tryLocation
    else findMovableBackward(monkeys, tryLocation)
  }

  private def bump(monkey: Monkey, location: (Int, Int)): Board = {
    val endLayer = location._1 - 1
    val endPlace: Option[(Int, Int)] =
      if (endLayer >= 0) {
        val firstTry =
          (location._2 * layers(endLayer).length) / layers(location._1).length +
            (if (location._1 < 5 && layers(location._1).length % layers(endLayer).length != 0) 1
            else 0)
        if (location._1 < 4 && !isMovable(List(monkey), layers(endLayer)(firstTry))) {
          Some(findMovableForward(List(monkey), Some((endLayer, firstTry))))
        } else if (location._2 >= 4 && !isMovable(List(monkey), layers(endLayer)(firstTry))) {
          Some(findMovableBackward(List(monkey), (endLayer, firstTry)))
        } else {
          Some((endLayer, firstTry))
        }
      } else None
    moveMonkeys(List(monkey), Some(location), endPlace)
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

  @tailrec
  private def resolveBumps(): Board = {
    val overflows = findOverflows()
    if (overflows.isEmpty) {
      this
    } else {
      resolveBump(overflows.head).resolveBumps()
    }
  }

  private def isMovable(monkeys: List[Monkey], place: Place): Boolean = {
    isMovable(monkeys.head.playerId, monkeys.length, place)
  }

  private def isMovable(playerId: Int, monkeyCount: Int, place: Place): Boolean = {
    !(place.monkeys.length == maxStack && !canBump(playerId, monkeyCount, place))
  }

  private def updatedTargetLayer(monkeys: List[Monkey], end: (Int, Int)): Vector[Vector[Place]] = {
    val targetPlace = layers(end._1)(end._2)
    if (!isMovable(monkeys, targetPlace)) {
      throw new IllegalStateException("too many monkeys to one spot")
    }
    val updatedTargetPlace = targetPlace.addMonkeys(monkeys)
    layers.updated(end._1, updatedLayer(layers(end._1), updatedTargetPlace, end._2))
  }

  private def updatedStartLayer(monkeys: List[Monkey], start: (Int, Int)): Vector[Vector[Place]] = {
    val startPlace = layers(start._1)(start._2)
    val playerId = monkeys.head.playerId
    if (startPlace.monkeys.count(_.playerId == playerId) == 0) {
      throw new IllegalStateException("no monkeys to move")
    }
    val updatedStartPlace = startPlace.removeMonkeys(monkeys)
    layers.updated(start._1, updatedLayer(layers(start._1), updatedStartPlace, start._2))
  }

  private def awardBananaCard(landingPlace: Place): BananaCards = {
    if (bananaCards.deck.nonEmpty) {
      BananaCards(bananaCards.deck.tail, bananaCards.deck.head :: bananaCards.active, bananaCards.discard)
    } else if (bananaCards.discard.nonEmpty) {
      val r = new Random
      val shuffled = r.shuffle(bananaCards.discard)
      BananaCards(shuffled.tail, shuffled.head :: bananaCards.active, List())
    } else {
      bananaCards
    }
  }

  private def withUpdatedStartLayer(monkeys: List[Monkey], start: (Int, Int), newMonkeyStarts: List[List[Monkey]]): Board = {
    Board(
      gameId,
      playerTokens,
      updatedStartLayer(monkeys, start),
      newMonkeyStarts,
      maxStack,
      diceCount,
      currentPlayer,
      turnIndex,
      playerCount,
      bananaCards)
  }

  private def withUpdatedTargetLayer(monkeys: List[Monkey],
                                     end: (Int, Int),
                                     newMonkeyStarts: List[List[Monkey]]): Board = {
    Board(
      gameId,
      playerTokens,
      updatedTargetLayer(monkeys, end),
      newMonkeyStarts,
      maxStack,
      diceCount,
      currentPlayer,
      turnIndex,
      playerCount,
      awardBananaCard(layers(end._1)(end._2)))
  }

  def slideMonkeysUp(mCoord: (Int, Int, Int, Int)): Board = {
    val monkeys = layers(mCoord._2)(mCoord._3).monkeys.filter(_.playerId == mCoord._1)
    val targetCoord = slideUpCoord((mCoord._2, mCoord._3))
    if (monkeys.length < mCoord._4) {
      throw new IllegalArgumentException("not enough monkeys to slide")
    } else if (!isMovable(monkeys, layers(targetCoord._1)(targetCoord._2))) {
      throw new IllegalArgumentException("illegal slide")
    }
    this.withUpdatedStartLayer(monkeys, (mCoord._2, mCoord._3), monkeyStarts).
      withUpdatedTargetLayer(monkeys, targetCoord, monkeyStarts).resolveBumps()
  }

  def slideMonkeysDown(mCoord: (Int, Int, Int, Int)): Board = {
    val monkeys = layers(mCoord._2)(mCoord._3).monkeys.filter(_.playerId == mCoord._1)
    if (monkeys.length < mCoord._4) {
      throw new IllegalArgumentException("not enough monkeys to slide")
    }

    slideDownCoord(mCoord._2, mCoord._3).bisequence[Option, Int, Int] match {
      case Some(coord) =>
        if (!isMovable(monkeys, layers(coord._1)(coord._2))) {
          throw new IllegalArgumentException("illegal slide")
        }
        this.withUpdatedStartLayer(monkeys, (mCoord._2, mCoord._3), monkeyStarts).
          withUpdatedTargetLayer(monkeys, coord, monkeyStarts).resolveBumps()
      case None =>
        this.withUpdatedStartLayer(monkeys, (mCoord._2, mCoord._3),
          monkeyStarts.updated(mCoord._1, monkeyStarts(mCoord._1) ++
            layers(mCoord._2)(mCoord._3).monkeys.filter(_.playerId == mCoord._1).take(mCoord._4)))
    }

  }

  def slideDownCoord(coord: (Int, Int)): (Option[Int], Option[Int]) = {
    if (coord._1 > 0) {
      (Some(coord._1 - 1), Some((coord._2 * layers(coord._1 - 1).length) / layers(coord._1).length))
    } else {
      (None, None)
    }
  }

  def slideUpCoord(coord: (Int, Int)): (Int, Int) = {
    if (coord._1 < layers.length - 1) {
      (coord._1 + 1, (coord._2 * layers(coord._1 + 1).length) / layers(coord._1).length)
    } else {
      throw new IllegalArgumentException("cannot slide up")
    }
  }


  def swapMonkeys(first: (Int, Int, Int), second: (Int, Int, Int)): Board = {
    val startMonkey = layers(first._2)(first._3).monkeys.find(_.playerId == first._1).
      getOrElse(throw new IllegalArgumentException("cannot swap without source monkey"))
    val endMonkey = layers(second._2)(second._3).monkeys.find(_.playerId == second._1).
      getOrElse(throw new IllegalArgumentException("cannot swap without target monkey"))
    this.withUpdatedStartLayer(
      List(startMonkey),
      (first._2, first._3),
      monkeyStarts
    ).withUpdatedTargetLayer(
      List(startMonkey),
      (second._2, second._3),
      monkeyStarts
    ).withUpdatedStartLayer(
      List(endMonkey),
      (second._2, second._3),
      monkeyStarts
    ).withUpdatedTargetLayer(
      List(endMonkey),
      (first._2, first._3),
      monkeyStarts)
  }

  private def moveMonkeys(monkeys: List[Monkey], start: Option[(Int, Int)], end: Option[(Int, Int)]): Board = {

    if (monkeys.isEmpty) {
      throw new IllegalArgumentException("tried to move no monkeys")
    }
    val playerId = monkeys.head.playerId

    if (!monkeys.forall(_.playerId == playerId)) {
      throw new IllegalArgumentException("tried to move multicolored monkeys")
    }

    start.map {
      s =>
        end.map {
          e =>
            this.withUpdatedStartLayer(monkeys, s, monkeyStarts).
              withUpdatedTargetLayer(monkeys, e, monkeyStarts)
        }.getOrElse {
          val newMonkeyStarts = monkeyStarts.updated(playerId, monkeyStarts(playerId) ++
            layers(s._1)(s._2).monkeys.filter(m => monkeys.contains(m)))
          this.withUpdatedStartLayer(monkeys, s, newMonkeyStarts)
        }
    }.getOrElse {
      end.map {
        e =>
          val newMonkeyStarts = monkeyStarts.updated(playerId, monkeyStarts(playerId).filter(m => !monkeys.contains(m)))
          this.withUpdatedTargetLayer(monkeys, e, newMonkeyStarts)
      }.getOrElse(this)
    }

  }

  private def advance(playerId: Int,
                      layerIndex: Option[Int],
                      placeIndex: Option[Int],
                      monkeyCount: Int,
                      distance: Int): Board = {
    (layerIndex, placeIndex) match {
      case (None, None) =>
        val targetPosition = targetIndex(playerId, None, distance)
        moveMonkeys(
          monkeyStarts(playerId).take(monkeyCount),
          None, Some(targetPosition))
      case (Some(l: Int), Some(p: Int)) =>
        val targetPosition = targetIndex(playerId, Some((l, p)), distance)
        moveMonkeys(
          layers(l)(p).monkeys.filter(m => m.playerId == playerId).take(monkeyCount),
          Some((l, p)), Some(targetPosition))
      case _ =>
        throw new IllegalArgumentException("invalid move")
    }
  }

  private def makeMove(move: Move): Board = {
    if (move.monkeyCount > 1 &&
      (move.distance.length != move.monkeyCount ||
      !move.distance.forall(_ == move.distance.head))) {
      throw new IllegalStateException("illegal move")
    }
    advance(move.playerId, move.layerIndex, move.placeIndex, move.monkeyCount, move.distance.sum)
  }

  private def incrementTurn(): Board = {
    val newCurrentPlayer =
      if (bananaCards.active.nonEmpty) {
        currentPlayer
      } else {
        (currentPlayer + 1) % playerCount
      }
    Board(gameId, playerTokens, layers, monkeyStarts, maxStack, diceCount, newCurrentPlayer, turnIndex + 1, playerCount, bananaCards)
  }

  private def extractDice(move: Move, dice: List[Int]): List[Int] = {
    dice.diff(move.distance)
  }

  def winner(): Int = {
    val winners: IndexedSeq[Boolean] =
      for {
        i <- 0 until playerCount
        won = checkWinCondition(i)
      } yield won
    winners.indexWhere(_ == true)
  }

  def allMove(playerId: Int, distance: Int): List[Move] = {
    layers.zipWithIndex.reverse.flatMap(layerTuple =>
      layerTuple._1.zipWithIndex.
        filter(_._1.monkeys.count(_.playerId == playerId) > 0).reverse.
        flatMap {
          placeTuple =>
            for {
              monkey <- placeTuple._1.monkeys if monkey.playerId == playerId
              move = Move(playerId, Some(layerTuple._2), Some(placeTuple._2), 1, List(1))
            } yield move
        }).toList
  }

  private def advanceAll(playerId: Int): Board = {
    val moveOneOn: List[Move] =
      if (monkeyStarts(currentPlayer).length > 0) {
        if (isMovable(List(monkeyStarts(playerId).head),
          layers(0).find(_.slideDown == Some(playerId)).getOrElse(
            throw new IllegalStateException("couldn't find start place for monkey")))) {
          List(Move(playerId, None, None, 1, List(1)))
        } else List()
      } else List()
    val moveAllOne: List[Move] = allMove(playerId, 1)
    val moves: List[Move] = moveOneOn ++ moveAllOne
    makePossibleMoves(moves)
  }

  @tailrec
  final def makePossibleMoves(moves: List[Move]): Board = {
    if (moves.isEmpty) {
      this
    } else {
      val target =
        targetIndex(
          moves.head.playerId,
          (moves.head.layerIndex, moves.head.placeIndex).bisequence[Option, Int, Int],
          moves.head.distance.sum)
      val possibleMoves = if (isMovable(moves.head.playerId, moves.head.monkeyCount, layers(target._1)(target._2))) {
        moves
      } else {
        moves.tail
      }
      makeMove(possibleMoves.head).makePossibleMoves(possibleMoves.tail)
    }
  }

  def slideMonkeysUp(descriptorList: List[(Int, Int, Int, Int)]): Board = {
    if(descriptorList.isEmpty) this
    else slideMonkeysUp(descriptorList.head).slideMonkeysUp(descriptorList.tail)
  }

  def slideMonkeysDown(descriptorList: List[(Int, Int, Int, Int)]): Board = {
    if(descriptorList.isEmpty) this
    else slideMonkeysDown(descriptorList.head).slideMonkeysDown(descriptorList.tail)
  }

  @tailrec
  final def consumeDice(diceDecisions: List[Move], diceRolls: List[Int]): Board = {
    val winners: IndexedSeq[Boolean] =
      for {
        i <- 0 until playerCount
        won = checkWinCondition(i)
      } yield won
    if (diceDecisions.isEmpty) {
      if (diceRolls.forall(_ == 1)) {
        advanceAll(currentPlayer).resolveBumps().incrementTurn()
      } else {
        this
      }
    } else if (winners.count(_ == true) == 1) {
      throw new Exception("cannot move after game is over")
    } else {
      val decision = diceDecisions.head
      if (decision.playerId != currentPlayer) {
        throw new IllegalArgumentException("it is not player " + decision.playerId + "'s turn")
      }
      val remainingDecisions = diceDecisions.tail
      val remainingDice = extractDice(decision, diceRolls)
      if (diceRolls.length > diceCount && !diceRolls.forall(_ == 1)) {
        throw new IllegalStateException("cannot move more monkeys than the number of dice")
      } else if (remainingDice.nonEmpty && remainingDice.length == diceRolls.length) {
        throw new IllegalStateException("dice have not been consumed")
      } else if (remainingDecisions.isEmpty) {
        makeMove(decision).resolveBumps().incrementTurn()
      } else {
        makeMove(decision).consumeDice(remainingDecisions, remainingDice)
      }
    }
  }

  private def updatedLayer(layer: Vector[Place], newPlace: Place, placeIndex: Int): Vector[Place] = {
    layer.updated(placeIndex, newPlace)
  }

}

object MonkeyStartGenerator {
  def generateMonkeyStarts(playerCount: Int, monkeyCount: Int): List[List[Monkey]] = {
    (for {
      i <- 0 until playerCount
      monkeys = (for {
        j <- 1 to monkeyCount
        monkey = Monkey(i, i * 100 + j)
      } yield monkey).toList
    } yield monkeys).toList
  }
}

object LayerGenerator {
  val layerCount = 6

  def generateLayers(playerCount: Int): Vector[Vector[Place]] = {
    (for {
      i <- 0 until layerCount
      layer = if (i < 5) generateLayer(playerCount, i) else Vector(Place(List(), None, None))
    } yield layer).toVector
  }

  private def generateLayer(playerCount: Int, layerIndex: Int): Vector[Place] = {
    (for {
      i <- 0 until playerCount * sideLength(layerIndex)
      place = Place(List(), getSlideUp(playerCount, layerIndex, i), getSlideDown(playerCount, layerIndex, i))
    } yield place).toVector
  }

  private def getSlideUp(playerCount: Int, layerIndex: Int, placeIndex: Int): Option[Int] = {
    getSlideDown(playerCount, layerIndex, placeIndex + 1)
  }

  private def getSlideDown(playerCount: Int, layerIndex: Int, placeIndex: Int): Option[Int] = {
    if (placeIndex % sideLength(layerIndex) == 0) {
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
      case _ => throw new IllegalStateException("layer index out of range")
    }
  }
}

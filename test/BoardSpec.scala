import model._
import org.junit.runner.RunWith
import org.specs2.mutable._

import org.specs2.runner.JUnitRunner

/**
 * Created by greg.rubino on 10/9/15.
 */
@RunWith(classOf[JUnitRunner])
class BoardSpec extends Specification {

  def testBoard: Board = {
    model.Board(
      "testGame",
      LayerGenerator.generateLayers(4),
      List(
        (for(i <- 0 to 6; monkey = Monkey(0, 10+i)) yield monkey).toList,
        (for(i <- 0 to 6; monkey = Monkey(1, 20+i)) yield monkey).toList,
        (for(i <- 0 to 6; monkey = Monkey(2, 30+i)) yield monkey).toList,
        (for(i <- 0 to 6; monkey = Monkey(3, 40)) yield monkey).toList),
      maxStack = 2, diceCount = 2, currentPlayer = 0, turnIndex = 0, playerCount = 4,
      BananaCards(List(), List(), List()))
  }

  "Board" should {
    "allow valid moves and return correct board" in {
      val board = testBoard.consumeDice(List(Move(0, None, None, 1, List(2, 2))), List(2, 2))
      (board.layers(1)(3).monkeys must haveLength(1)) and
        (board.monkeyStarts.head.length must equalTo(6))
    }
    "prevent players from moving out of turn" in {
      testBoard.consumeDice(List(Move(1, None, None, 1, List(2, 2))), List(2, 2)) must throwAn[IllegalArgumentException]
    }
    "prevent moving of non existent monkeys" in {
      testBoard.consumeDice(List(Move(0, Some(0), Some(0), 1, List(2, 2))), List(2, 2)) must throwAn[IllegalArgumentException]
    }
    "prevent monkeys from moving more than the dice values" in {
      testBoard.consumeDice(List(Move(0, None, None, 1, List(4, 4))), List(2, 2)) must throwAn[IllegalStateException]
    }
    "prevent monkeys from move to positions that are already at capacity when no bump is possible" in {
      (testBoard.
        consumeDice(List(Move(0, None, None, 2, List(1, 1))), List(1, 1)).
        consumeDice(List(Move(1, None, None, 1, List(6, 6, 1))), List(6, 6, 1)) must throwAn[IllegalStateException]) and
        (testBoard.consumeDice(List(
          Move(0, None, None, 1, List(1)), Move(0, None, None, 1, List(1)),
          Move(0, None, None, 1, List(1))), List(1, 1, 1)) must throwAn[IllegalStateException])
    }
    "allow monkeys to bump other monkeys out of positions that are at capacity" in {
      val board = testBoard.
        consumeDice(List(Move(0, None, None, 1, List(2, 3))), List(2, 3)).
        consumeDice(List(Move(1, None, None, 1, List(1)), Move(1, None, None, 1, List(1))), List(1, 1))
      (board.layers(1)(4).monkeys must haveLength(2)) and
        (board.layers(0)(4).monkeys must haveLength(1)) and
        (board.monkeyStarts.head.length must equalTo(6)) and
        (board.monkeyStarts(1).length must equalTo(5))
    }
    "allow monkey stacking when doubles are rolled" in {
      testBoard.consumeDice(List(Move(0, None, None, 2, List(2, 2))), List(2, 2)).layers(1)(3).monkeys must haveLength(2)
    }
    "prevent monkey stacking when doubles are not rolled" in {
      testBoard.consumeDice(List(Move(0, None, None, 2, List(2, 3))), List(2, 3)) must throwAn[IllegalStateException]
    }
    "prevent monkey stacking higher than maxStack" in {
      testBoard.consumeDice(List(Move(0, None, None, 3, List(2, 2, 2))), List(2, 2, 2)) must throwAn[IllegalStateException]
    }
    "allow monkeytalk to move a single monkey forward" in {
      val lingo = new MonkeyTalk(
        testBoard.consumeDice(List(Move(0, None, None, 1, List(2, 3))), List(2, 3)),
        Map("aColor" -> 0, "layer" -> 1, "place" -> 4))
      val newBoard = lingo.parseAll(lingo.monkeyExpr, "move (aColor:layer:place:1, 3)")
      newBoard.map {
        b =>
          b.layers(1)(4).monkeys must haveLength(0)
          b.layers(1)(7).monkeys must haveLength(1)
      }.getOrElse(throw new IllegalStateException("parser did not return a board"))
    }
    "allow monkeytalk to move several monkeys forward" in {
      val lingo = new MonkeyTalk(
        testBoard.consumeDice(List(Move(0, None, None, 1, List(1)), Move(0, None, None, 1, List(1))), List(1, 1)),
        Map("aColor" -> 0, "layer" -> 1, "place" -> 0))
      val newBoard = lingo.parseAll(lingo.monkeyExpr, "move (aColor:layer:place:1, 3), (aColor:layer:place:1, 3)")
      newBoard.map {
        b =>
          b.layers(1)(3).monkeys must haveLength(2)
      }.getOrElse(throw new IllegalStateException("parser did not return a board"))
    }
    "allow monkeytalk to move all monkeys forward" in {
      val lingo = new MonkeyTalk(
        testBoard.consumeDice(List(Move(0, None, None, 1, List(2)), Move(0, None, None, 1, List(1))), List(2, 1)),
        Map("aColor" -> 0, "layer" -> 1, "place" -> 0))
      val newBoard = lingo.parseAll(lingo.monkeyExpr, "move (aColor:*:*:1, 3)")
      newBoard.map {
        b =>
          b.layers(1)(1).monkeys must haveLength(1)
          b.layers(1)(2).monkeys must haveLength(1)
      }.getOrElse(throw new IllegalStateException("parser did not return a board"))
    }
    "allow monkeytalk to swap two monkeys" in {
      val lingo = new MonkeyTalk(
        testBoard.consumeDice(List(Move(0, None, None, 1, List(1)), Move(0, None, None, 1, List(5))), List(1, 5)).
          consumeDice(List(Move(1, None, None, 1, List(1))), List(1)),
        Map("aColor" -> 0, "aLayer" -> 1, "aPlace" -> 0, "bColor" -> 1, "bLayer" -> 1, "bPlace" -> 4))
      val newBoard = lingo.parseAll(lingo.monkeyExpr, "swap aColor:aLayer:aPlace:1 with bColor:bLayer:bPlace:1")
      newBoard.map {
        b =>
          b.layers(1)(0).monkeys must haveLength(1)
          b.layers(1)(0).monkeys.head.playerId must equalTo(1)
          b.layers(1)(4).monkeys must haveLength(2)
          b.layers(1)(4).monkeys.filter(_.playerId == 0) must haveLength(2)
      }.getOrElse(throw new IllegalStateException("parser did not return a board"))
    }
  }
}

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
      val board = testBoard.consumeDice(List(Move(0, -1, -1, 1, List(2, 2))), List(2, 2))
      (board.layers.head(3).monkeys must haveLength(1)) and
        (board.monkeyStarts.head.length must equalTo(6))
    }
    "prevent players from moving out of turn" in {
      testBoard.consumeDice(List(Move(1, -1, -1, 1, List(2, 2))), List(2, 2)) must throwAn[IllegalArgumentException]
    }
    "prevent moving of non existent monkeys" in {
      testBoard.consumeDice(List(Move(0, 0, 0, 1, List(2, 2))), List(2, 2)) must throwAn[IllegalArgumentException]
    }
    "prevent monkeys from moving more than the dice values" in {
      testBoard.consumeDice(List(Move(0, -1, -1, 1, List(4, 4))), List(2, 2)) must throwAn[IllegalStateException]
    }
    "prevent monkeys from move to positions that are already at capacity when no bump is possible" in {
      (testBoard.
        consumeDice(List(Move(0, -1, -1, 2, List(1, 1))), List(1, 1)).
        consumeDice(List(Move(1, -1, -1, 1, List(6, 6, 1))), List(6, 6, 1)) must throwAn[IllegalStateException]) and
        (testBoard.consumeDice(List(
          Move(0, -1, -1, 1, List(1)), Move(0, -1, -1, 1, List(1)),
          Move(0, -1, -1, 1, List(1))), List(1, 1, 1)) must throwAn[IllegalStateException])
    }
    "allow monkeys to bump other monkeys out of positions that are at capacity" in {
      val board = testBoard.
        consumeDice(List(Move(0, -1, -1, 1, List(2, 3))), List(2, 3)).
        consumeDice(List(Move(1, -1, -1, 1, List(1)), Move(1, -1, -1, 1, List(1))), List(1, 1))
      (board.layers.head(4).monkeys must haveLength(2)) and
        (board.monkeyStarts.head.length must equalTo(7)) and
        (board.monkeyStarts(1).length must equalTo(5))
    }
    "allow monkey stacking when doubles are rolled" in {
      testBoard.consumeDice(List(Move(0, -1, -1, 2, List(2, 2))), List(2, 2)).layers.head(3).monkeys must haveLength(2)
    }
    "prevent monkey stacking when doubles are not rolled" in {
      testBoard.consumeDice(List(Move(0, -1, -1, 2, List(2, 3))), List(2, 3)) must throwAn[IllegalStateException]
    }
    "prevent monkey stacking higher than maxStack" in {
      testBoard.consumeDice(List(Move(0, -1, -1, 3, List(2, 2, 2))), List(2, 2, 2)) must throwAn[IllegalStateException]
    }
  }
}

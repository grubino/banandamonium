package model

/**
 * Created by greg.rubino on 10/2/15.
 */
case class Place(monkeys: List[Int], slideUp: Option[Int], slideDown: Option[Int]) {
  def removeMonkeys(playerId: Int, monkeyCount: Int): Place = {
    if(monkeyCount == 1) {
      removeOneMonkey(playerId)
    } else {
      removeMonkeys(playerId, monkeyCount-1).removeOneMonkey(playerId)
    }
  }

  def addMonkeys(playerId: Int, monkeyCount: Int): Place = {
    if(monkeyCount == 1) {
      addOneMonkey(playerId)
    } else {
      addMonkeys(playerId, monkeyCount-1).addOneMonkey(playerId)
    }
  }

  def addOneMonkey(playerId: Int): Place = {
    Place(monkeys :+ playerId, slideUp, slideDown)
  }

  def removeOneMonkey(playerId: Int): Place = {
    val indexOfMonkey = monkeys indexOf(playerId)
    if(indexOfMonkey == -1) {
      throw new IllegalArgumentException("no monkey at location")
    }
    return Place(monkeys.take(indexOfMonkey) ++ monkeys.takeRight(monkeys.length-indexOfMonkey-1), slideUp, slideDown)
  }

  def getSlideUp: Int = {
    slideUp match {
      case Some(x) => x
      case None => -1
    }
  }
  def getSlideDown: Int = {
    slideDown match {
      case Some(x) => x
      case None => -1
    }
  }
}
